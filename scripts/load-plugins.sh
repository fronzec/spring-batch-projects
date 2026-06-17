#!/usr/bin/env bash
# load-plugins.sh — Build (optional), upload, enable, and load all four plugins.
#
# Usage:
#   bash scripts/load-plugins.sh [--build] [--help]
#
# Flags:
#   --build    Build all four plugin JARs before uploading (runs mvn package)
#
# Environment variables (all optional — defaults shown below):
#   BASE_URL    Backend base URL   (default: http://localhost:8080/api/batch-service)
#   ADMIN_USER  Admin username     (default: admin)
#   ADMIN_PASS  Admin password     (default: admin123)
#
# Prerequisites:
#   - curl must be in PATH
#   - jq OR python3 must be in PATH (used to parse the upload response)
#   - The backend must be running on BASE_URL before this script is called.
#   - The plugin JAR directory must exist (see RUNNING_LOCALLY.md Step 4).
#   - If --build is passed, mvn must be in PATH and batch-job-api must be
#     installed locally (mvn -f batch-job-api/pom.xml clean install).

set -Eeuo pipefail

# ─── Script location ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"

# ─── Configuration ────────────────────────────────────────────────────────────
BASE_URL="${BASE_URL:-http://localhost:8080/api/batch-service}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"
BUILD=false

# ─── Help ─────────────────────────────────────────────────────────────────────
usage() {
    printf 'Usage: %s [--build] [--help]\n\n' "$(basename "$0")"
    printf 'Upload, enable, and load all four plugins into the running backend.\n\n'
    printf 'Flags:\n'
    printf '  --build    Build plugin JARs first (mvn package -DskipTests)\n\n'
    printf 'Environment variables:\n'
    printf '  BASE_URL    Backend URL    (default: http://localhost:8080/api/batch-service)\n'
    printf '  ADMIN_USER  Admin user     (default: admin)\n'
    printf '  ADMIN_PASS  Admin password (default: admin123)\n'
    exit 0
}

# ─── Argument parsing ─────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --build)
            BUILD=true
            shift
            ;;
        --help|-h)
            usage
            ;;
        *)
            printf '[ERROR] Unknown argument: %s\n' "$1" >&2
            usage
            ;;
    esac
done

# ─── Dependency checks ────────────────────────────────────────────────────────
if ! command -v curl &>/dev/null; then
    printf '[ERROR] curl not found in PATH.\n' >&2
    exit 1
fi

# Detect JSON parser: prefer jq, fall back to python3
JSON_PARSER=""
if command -v jq &>/dev/null; then
    JSON_PARSER="jq"
elif command -v python3 &>/dev/null; then
    JSON_PARSER="python3"
else
    printf '[ERROR] Neither jq nor python3 found in PATH.\n' >&2
    printf '        Install one of them and retry (brew install jq).\n' >&2
    exit 1
fi

extract_id() {
    local json="$1"
    if [[ "$JSON_PARSER" == "jq" ]]; then
        printf '%s' "$json" | jq -r '.id'
    else
        printf '%s' "$json" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
    fi
}

if [[ "$BUILD" == "true" ]] && ! command -v mvn &>/dev/null; then
    printf '[ERROR] --build was requested but mvn is not in PATH.\n' >&2
    exit 1
fi

# ─── Plugin definitions ───────────────────────────────────────────────────────
# Format: "jobName|relative/jar/path|mainClassName"
declare -a PLUGINS=(
    "ticket-pdf-job|ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar|com.fronzec.plugins.ticketpdf.TicketPdfJobPlugin"
    "ticket-bundle-job|ticket-bundle-job/target/ticket-bundle-job-1.0.0.jar|com.fronzec.plugins.ticketbundle.TicketBundleJobPlugin"
    "fault-tolerant-harvester-job|fault-tolerant-harvester-job/target/fault-tolerant-harvester-job-1.0.0.jar|com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin"
    "partitioned-harvester-job|partitioned-harvester-job/target/partitioned-harvester-job-1.0.0.jar|com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin"
)

# ─── Optional build ───────────────────────────────────────────────────────────
if [[ "$BUILD" == "true" ]]; then
    printf '[INFO] Building plugin JARs ...\n'
    declare -a MODULE_DIRS=(
        "ticket-pdf-job"
        "ticket-bundle-job"
        "fault-tolerant-harvester-job"
        "partitioned-harvester-job"
    )
    for module in "${MODULE_DIRS[@]}"; do
        printf '[INFO]   Building %s ...\n' "$module"
        mvn -B package -f "$REPO_ROOT/$module/pom.xml" -DskipTests -q
        printf '[OK]     %s built.\n' "$module"
    done
    printf '[INFO] All JARs built.\n\n'
fi

# ─── Backend connectivity check ───────────────────────────────────────────────
printf '[INFO] Checking backend at %s ...\n' "$BASE_URL"
HEALTH_STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
    "${BASE_URL}/actuator/health" || true)

if [[ "$HEALTH_STATUS" != "200" ]]; then
    printf '[ERROR] Backend health check returned HTTP %s (expected 200).\n' \
        "$HEALTH_STATUS" >&2
    printf '        Is the backend running? Start it with:\n' >&2
    printf '          cd fr-batch-service && mvn spring-boot:run -Dspring-boot.run.profiles=local\n' >&2
    exit 1
fi
printf '[INFO] Backend is up.\n\n'

# ─── Track results ────────────────────────────────────────────────────────────
LOADED=0
SKIPPED=0
FAILED=0

# ─── Process each plugin ──────────────────────────────────────────────────────
for entry in "${PLUGINS[@]}"; do
    IFS='|' read -r JOB_NAME JAR_REL MAIN_CLASS <<< "$entry"
    JAR_ABS="$REPO_ROOT/$JAR_REL"

    printf '─── Plugin: %s ───────────────────────────────────────\n' "$JOB_NAME"

    # Validate JAR exists
    if [[ ! -f "$JAR_ABS" ]]; then
        printf '[ERROR] JAR not found: %s\n' "$JAR_ABS" >&2
        printf '        Build it first: mvn -B package -f %s/pom.xml -DskipTests\n' \
            "$(dirname "$JAR_REL")" >&2
        FAILED=$((FAILED + 1))
        continue
    fi

    # ── Step 1: Upload ─────────────────────────────────────────────────────
    printf '[1/3] Uploading %s ...\n' "$JOB_NAME"
    HTTP_RESPONSE=$(curl -s -w '\n%{http_code}' \
        -u "${ADMIN_USER}:${ADMIN_PASS}" \
        -X POST "${BASE_URL}/jobs/upload" \
        -F "file=@${JAR_ABS}" \
        -F "jobName=${JOB_NAME}" \
        -F "version=1.0.0" \
        -F "mainClassName=${MAIN_CLASS}")

    HTTP_BODY=$(printf '%s' "$HTTP_RESPONSE" | head -n -1)
    HTTP_CODE=$(printf '%s' "$HTTP_RESPONSE" | tail -n 1)

    if [[ "$HTTP_CODE" == "409" ]]; then
        printf '[SKIP] %s already uploaded (HTTP 409 Conflict).\n' "$JOB_NAME"
        SKIPPED=$((SKIPPED + 1))
        # Still try enable+load on the existing definition via list endpoint
        EXISTING_ID=$(curl -s \
            -u "${ADMIN_USER}:${ADMIN_PASS}" \
            "${BASE_URL}/jobs/definitions" \
            | (
                if [[ "$JSON_PARSER" == "jq" ]]; then
                    jq -r --arg name "$JOB_NAME" '.[] | select(.jobName == $name) | .id'
                else
                    python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in data:
    if item.get('jobName') == '${JOB_NAME}':
        print(item['id'])
        break
"
                fi
            ) || true)

        if [[ -z "${EXISTING_ID:-}" ]]; then
            printf '[WARN] Could not find existing ID for %s — skipping enable+load.\n' \
                "$JOB_NAME" >&2
            continue
        fi
        DEFINITION_ID="$EXISTING_ID"
    elif [[ "$HTTP_CODE" == "201" ]]; then
        DEFINITION_ID=$(extract_id "$HTTP_BODY" || true)
        if [[ -z "${DEFINITION_ID:-}" || "$DEFINITION_ID" == "null" ]]; then
            printf '[ERROR] Upload succeeded but could not parse ID from response:\n' >&2
            printf '%s\n' "$HTTP_BODY" >&2
            FAILED=$((FAILED + 1))
            continue
        fi
        printf '[OK]   Uploaded. Definition ID: %s\n' "$DEFINITION_ID"
    else
        printf '[ERROR] Upload failed with HTTP %s:\n' "$HTTP_CODE" >&2
        printf '%s\n' "$HTTP_BODY" >&2
        FAILED=$((FAILED + 1))
        continue
    fi

    # ── Step 2: Enable ─────────────────────────────────────────────────────
    printf '[2/3] Enabling definition %s ...\n' "$DEFINITION_ID"
    ENABLE_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
        -u "${ADMIN_USER}:${ADMIN_PASS}" \
        -X PUT "${BASE_URL}/jobs/definitions/${DEFINITION_ID}/enable")

    if [[ "$ENABLE_CODE" == "200" ]]; then
        printf '[OK]   Enabled.\n'
    else
        printf '[WARN] Enable returned HTTP %s (definition may already be enabled).\n' \
            "$ENABLE_CODE"
    fi

    # ── Step 3: Load ───────────────────────────────────────────────────────
    printf '[3/3] Loading definition %s ...\n' "$DEFINITION_ID"
    LOAD_RESPONSE=$(curl -s -w '\n%{http_code}' \
        -u "${ADMIN_USER}:${ADMIN_PASS}" \
        -X POST "${BASE_URL}/jobs/definitions/${DEFINITION_ID}/load")

    LOAD_BODY=$(printf '%s' "$LOAD_RESPONSE" | head -n -1)
    LOAD_CODE=$(printf '%s' "$LOAD_RESPONSE" | tail -n 1)

    if [[ "$LOAD_CODE" == "200" ]]; then
        printf '[OK]   Loaded.\n'
        LOADED=$((LOADED + 1))
    elif [[ "$LOAD_CODE" == "409" ]]; then
        printf '[SKIP] Already loaded (HTTP 409).\n'
    else
        printf '[ERROR] Load failed with HTTP %s:\n' "$LOAD_CODE" >&2
        printf '%s\n' "$LOAD_BODY" >&2
        FAILED=$((FAILED + 1))
        continue
    fi

    printf '\n'
done

# ─── Summary ──────────────────────────────────────────────────────────────────
printf '══════════════════════════════════════════════════════\n'
printf ' Summary\n'
printf '══════════════════════════════════════════════════════\n'
printf '  Loaded:  %d\n' "$LOADED"
printf '  Skipped: %d (already uploaded)\n' "$SKIPPED"
printf '  Failed:  %d\n' "$FAILED"
printf '\n'

if [[ $FAILED -gt 0 ]]; then
    printf '[WARN] %d plugin(s) failed. Check output above.\n' "$FAILED" >&2
    exit 1
fi

printf '[DONE] Verify with:\n'
printf '       curl -s %s/jobs/plugins | python3 -m json.tool\n' "$BASE_URL"
