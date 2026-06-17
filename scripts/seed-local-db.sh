#!/usr/bin/env bash
# seed-local-db.sh — Apply the three local seed files to fr_batch_local.
#
# Usage:
#   bash scripts/seed-local-db.sh [--help]
#
# Environment variables (all optional — defaults shown below):
#   DB_NAME      Database name          (default: fr_batch_local)
#   DB_USER      MySQL username         (default: root)
#   DB_PASSWORD  MySQL password         (default: empty string)
#   MYSQL_HOST   MySQL host             (default: 127.0.0.1)
#   MYSQL_PORT   MySQL port             (default: 3306)
#
# Prerequisites:
#   - mysql client must be in PATH
#   - The backend must have started and run Flyway migrations at least once,
#     otherwise the target tables do not exist yet.
#
# Notes:
#   - Do NOT run 00_create_schema.sql — it is superseded by Flyway.
#   - Seeds are idempotent if they use INSERT IGNORE or ON DUPLICATE KEY.
#     If they do not, re-running may produce duplicate-key errors; those are safe
#     to ignore once data is already seeded.

set -Eeuo pipefail

# ─── Script location ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
SEED_DIR="$REPO_ROOT/fr-batch-service/_devenvironment/db"

# ─── Configuration ────────────────────────────────────────────────────────────
DB_NAME="${DB_NAME:-fr_batch_local}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"

# ─── Help ─────────────────────────────────────────────────────────────────────
usage() {
    printf 'Usage: %s [--help]\n\n' "$(basename "$0")"
    printf 'Apply local seed SQL files to the fr_batch_local database.\n\n'
    printf 'Environment variables:\n'
    printf '  DB_NAME      Database name    (default: fr_batch_local)\n'
    printf '  DB_USER      MySQL user       (default: root)\n'
    printf '  DB_PASSWORD  MySQL password   (default: empty)\n'
    printf '  MYSQL_HOST   MySQL host       (default: 127.0.0.1)\n'
    printf '  MYSQL_PORT   MySQL port       (default: 3306)\n'
    exit 0
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
fi

# ─── Dependency check ─────────────────────────────────────────────────────────
if ! command -v mysql &>/dev/null; then
    printf '[ERROR] mysql client not found in PATH.\n' >&2
    printf '        Install it (e.g. brew install mysql-client) and retry.\n' >&2
    exit 1
fi

# ─── Seed files in order ──────────────────────────────────────────────────────
declare -a SEED_FILES=(
    "$SEED_DIR/10_seed_event_tickets.sql"
    "$SEED_DIR/20_seed_harvest_source.sql"
    "$SEED_DIR/30_seed_usage_record.sql"
)

# ─── Helpers ──────────────────────────────────────────────────────────────────
run_mysql() {
    # Accepts -e "SQL" or piped input
    if [[ -n "${DB_PASSWORD}" ]]; then
        mysql \
            --host="$MYSQL_HOST" \
            --port="$MYSQL_PORT" \
            --user="$DB_USER" \
            --password="$DB_PASSWORD" \
            "$@"
    else
        # Empty password: avoid the --password flag to suppress the warning
        mysql \
            --host="$MYSQL_HOST" \
            --port="$MYSQL_PORT" \
            --user="$DB_USER" \
            "$@"
    fi
}

# ─── Verify connectivity ──────────────────────────────────────────────────────
printf '[INFO] Checking MySQL connectivity at %s:%s ...\n' "$MYSQL_HOST" "$MYSQL_PORT"
if ! run_mysql -e "SELECT 1;" &>/dev/null; then
    printf '[ERROR] Cannot connect to MySQL at %s:%s as user %s.\n' \
        "$MYSQL_HOST" "$MYSQL_PORT" "$DB_USER" >&2
    printf '        Is the database container running? (docker compose up -d)\n' >&2
    exit 1
fi
printf '[INFO] Connected.\n'

# ─── Ensure database exists ───────────────────────────────────────────────────
printf '[INFO] Ensuring database "%s" exists ...\n' "$DB_NAME"
run_mysql -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
printf '[INFO] Database "%s" ready.\n' "$DB_NAME"

# ─── Apply seeds ──────────────────────────────────────────────────────────────
for seed in "${SEED_FILES[@]}"; do
    if [[ ! -f "$seed" ]]; then
        printf '[ERROR] Seed file not found: %s\n' "$seed" >&2
        exit 1
    fi

    filename="$(basename "$seed")"
    printf '[INFO] Applying seed: %s ...\n' "$filename"

    if run_mysql "$DB_NAME" < "$seed"; then
        printf '[OK]   %s applied successfully.\n' "$filename"
    else
        printf '[ERROR] Failed to apply %s — check the output above.\n' "$filename" >&2
        exit 1
    fi
done

printf '\n[DONE] All seeds applied to database "%s".\n' "$DB_NAME"
