# Running the Stack Locally

Everything you need to go from a fresh clone to a fully operational local environment: MySQL running, backend migrated, seeds applied, all four plugins loaded, and the UI showing live data.

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java (JDK) | 21 | `java -version` |
| JAVA_HOME | points to JDK 21 | `echo $JAVA_HOME` |
| Maven | 3.9+ | `mvn -version` |
| Docker or Podman | any recent | `docker info` |
| Node.js | 20 LTS | `node -version` |
| npm | bundled with Node 20 | `npm -version` |
| jq or python3 | either | `jq --version` / `python3 --version` |

---

## One-Time: GitHub Packages Authentication

`batch-job-api` is published to GitHub Packages. Every plugin and `fr-batch-service` declare it as a Maven dependency. Without auth, Maven exits with HTTP 401.

### Option A — Local install (simplest for the repo owner)

Build and install `batch-job-api` into your local Maven cache. No GitHub PAT needed.

```bash
mvn -f batch-job-api/pom.xml clean install
```

### Option B — GitHub PAT in settings.xml

1. Create a [Personal Access Token](https://github.com/settings/tokens) with the **`read:packages`** scope.
2. Add a `<server>` entry to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_PAT_HERE</password>
    </server>
  </servers>
</settings>
```

> Option A is the faster path if you own the repository. Option B is needed for contributors who cannot publish to the registry.

---

## Step 1 — Start MySQL

The database container lives in `fr-batch-service/_devenvironment/`.

```bash
cd fr-batch-service/_devenvironment
docker compose up -d
cd -
```

The compose file reads `fr-batch-service/.env`. That file configures `MYSQL_DATABASE=frbatchservicedb2` and a non-root user. The app's **local profile** connects to a different database (`fr_batch_local`) as `root` with an empty password — so you must create that database manually:

```bash
# Wait a few seconds for MySQL to be ready, then:
docker exec -it "$(docker ps --filter ancestor=mysql:8.2.0 -q | head -1)" \
  mysql -u root -e "CREATE DATABASE IF NOT EXISTS fr_batch_local CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Or connect with any MySQL client and run:

```sql
CREATE DATABASE IF NOT EXISTS fr_batch_local
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> **Why the mismatch?** `fr-batch-service/.env` is git-tracked and sets `DB_NAME=frbatchservicedb2`. The local Spring profile (`application-local.properties`) is hard-coded to `fr_batch_local`. They serve different purposes: the `.env` file pre-dates the local profile. The simplest fix is to create `fr_batch_local` as shown above; do not rename the `.env` value.

Verify MySQL is up:

```bash
docker exec -it "$(docker ps --filter ancestor=mysql:8.2.0 -q | head -1)" \
  mysqladmin ping -u root
```

---

## Step 2 — Start the Backend

```bash
cd fr-batch-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**The `local` profile flag is required.** Without it, the app tries to read `${DB_HOST}`, `${DB_PORT}`, `${DB_NAME}`, `${DB_USERNAME}`, and `${DB_PASSWORD}` from the environment and fails at startup.

What happens on first run with the `local` profile:

- `FlywayMigrationRunner` (active only in the `local` profile) runs migrations V1–V8, creating all tables.
- `spring.flyway.enabled=false` in `application-local.properties` keeps Spring's built-in Flyway autoconfigure out of the way.
- `AutoApproveConfig` auto-approves every plugin upload — no manual approval step needed.
- NoOp password encoder is active (plain-text credentials work).

Wait until you see a line similar to:
```
Started FrBatchServiceApplication in X.XXX seconds
```

Health check:

```bash
curl -s http://localhost:8080/api/batch-service/actuator/health | python3 -m json.tool
```

Expected: `{"status":"UP"}`.

---

## Step 3 — Apply Seeds

The seed files live in `fr-batch-service/_devenvironment/db/`. Apply them **after** the backend has started (Flyway must have created the tables first).

Use the helper script:

```bash
bash scripts/seed-local-db.sh
```

Or apply manually:

```bash
# Default: root@127.0.0.1, DB fr_batch_local
DB_NAME=fr_batch_local DB_USER=root MYSQL_HOST=127.0.0.1 bash scripts/seed-local-db.sh
```

Seed files applied in order:

| File | Requires | Inserts |
|------|----------|---------|
| `10_seed_event_tickets.sql` | V5 (event_tickets, generated_files) | sample event ticket rows |
| `20_seed_harvest_source.sql` | V7 (harvest_source, harvest_dead_letter) | harvest source configs |
| `30_seed_usage_record.sql` | V8 (usage_record, billing_charge) | usage and billing samples |

> Do **not** run `00_create_schema.sql` — that file is superseded by Flyway and will conflict with existing tables.

---

## Step 4 — Create the Plugin JAR Directory

The local profile stores uploaded JARs at an absolute path:

```
/Users/lalo/__home/projects/spring-batch-projects/target/local-plugins/jars/
```

This path is hard-coded in `fr-batch-service/src/main/resources/application-local.properties` (`fr-batch-service.plugins.jar-dir`). Create it before uploading plugins:

```bash
mkdir -p /Users/lalo/__home/projects/spring-batch-projects/target/local-plugins/jars/
```

> **Non-owner machines:** If your repo is checked out to a different path, override the property before starting the backend:
> ```bash
> mvn spring-boot:run \
>   -Dspring-boot.run.profiles=local \
>   -Dspring-boot.run.arguments="--fr-batch-service.plugins.jar-dir=/your/path/to/jars/"
> ```
> Or set it directly in `application-local.properties` — that file is not a secret, just local config.

---

## Step 5 — Build the Plugin JARs

Each plugin is a separate Maven module. Build all four fat JARs:

```bash
mvn -B package -f ticket-pdf-job/pom.xml -DskipTests
mvn -B package -f ticket-bundle-job/pom.xml -DskipTests
mvn -B package -f fault-tolerant-harvester-job/pom.xml -DskipTests
mvn -B package -f partitioned-harvester-job/pom.xml -DskipTests
```

Expected output jars:

| Plugin | JAR |
|--------|-----|
| ticket-pdf-job | `ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar` |
| ticket-bundle-job | `ticket-bundle-job/target/ticket-bundle-job-1.0.0.jar` |
| fault-tolerant-harvester-job | `fault-tolerant-harvester-job/target/fault-tolerant-harvester-job-1.0.0.jar` |
| partitioned-harvester-job | `partitioned-harvester-job/target/partitioned-harvester-job-1.0.0.jar` |

> If you skipped Option A for GitHub Packages auth, Maven will 401 here. Go back and run `mvn -f batch-job-api/pom.xml clean install` first.

---

## Step 6 — Upload, Enable, and Load Each Plugin

Plugin loading is **dynamic upload, not classpath**. At startup, zero plugins are active. You register them through the REST API:

1. **Upload** — POST the JAR to the service (returns a definition `id`)
2. **Enable** — PUT to mark the definition active
3. **Load** — POST to instantiate the plugin and register it as a Spring Batch job

Use the helper script (it handles all four plugins and all three steps):

```bash
bash scripts/load-plugins.sh
```

To also build the JARs before loading:

```bash
bash scripts/load-plugins.sh --build
```

### Manual reference

For a single plugin, the three calls look like:

```bash
BASE=http://localhost:8080/api/batch-service

# 1. Upload
RESPONSE=$(curl -s -u admin:admin123 -X POST "$BASE/jobs/upload" \
  -F "file=@ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar" \
  -F "jobName=ticket-pdf-job" \
  -F "version=1.0.0" \
  -F "mainClassName=com.fronzec.plugins.ticketpdf.TicketPdfJobPlugin")

ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# 2. Enable
curl -s -u admin:admin123 -X PUT "$BASE/jobs/definitions/$ID/enable"

# 3. Load
curl -s -u admin:admin123 -X POST "$BASE/jobs/definitions/$ID/load"
```

### Plugin coordinates reference

| jobName | JAR path | mainClassName |
|---------|----------|---------------|
| `ticket-pdf-job` | `ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar` | `com.fronzec.plugins.ticketpdf.TicketPdfJobPlugin` |
| `ticket-bundle-job` | `ticket-bundle-job/target/ticket-bundle-job-1.0.0.jar` | `com.fronzec.plugins.ticketbundle.TicketBundleJobPlugin` |
| `fault-tolerant-harvester-job` | `fault-tolerant-harvester-job/target/fault-tolerant-harvester-job-1.0.0.jar` | `com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin` |
| `partitioned-harvester-job` | `partitioned-harvester-job/target/partitioned-harvester-job-1.0.0.jar` | `com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin` |

> **Security note:** `POST /jobs/upload`, `PUT /jobs/definitions/{id}/enable`, and `POST /jobs/definitions/{id}/load` all require the `ADMIN` role. Locally, use `admin:admin123`. `AutoApproveConfig` means the approval step is skipped automatically — you go straight from upload to enable.

---

## Step 7 — Start the UI

```bash
cd batch-ops-ui
npm install
npm run dev
```

Open http://localhost:5173. Log in with `viewer/viewer123`.

The Vite dev server proxies `/api` requests to `:8080`, so the backend must be running first.

---

## Verify It Worked

```bash
# List registered plugins (public endpoint — no auth needed)
curl -s http://localhost:8080/api/batch-service/jobs/plugins | python3 -m json.tool

# List job definitions (requires VIEWER role)
curl -s -u viewer:viewer123 http://localhost:8080/api/batch-service/jobs/definitions | python3 -m json.tool
```

Expected: four entries in `/jobs/plugins`, each showing `loadStatus: LOADED`.

Open the dashboard at http://localhost:5173 — you should see all four jobs listed and triggerable.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Could not resolve placeholder 'DB_HOST'` at startup | Missing `local` profile flag | Add `-Dspring-boot.run.profiles=local` |
| `Unknown database 'fr_batch_local'` | DB not created | Run `CREATE DATABASE IF NOT EXISTS fr_batch_local;` in MySQL |
| `mvn package` exits with HTTP 401 | GitHub Packages not authenticated | Run `mvn -f batch-job-api/pom.xml clean install` (Option A) |
| Upload returns 500 / `No such file or directory` | JAR directory does not exist | `mkdir -p /Users/lalo/__home/projects/spring-batch-projects/target/local-plugins/jars/` |
| UI shows "Backend unreachable" | Backend not running on :8080 | Start backend first: Step 2 |
| `POST /jobs/upload` returns 401 | Wrong credentials | Use `admin:admin123` for write operations |
| Flyway fails with `Table already exists` | `00_create_schema.sql` was run manually | Drop all tables and let Flyway recreate, or restore a clean volume |

---

## Alternative: Full-Stack Docker Compose

A root-level `docker-compose.yml` runs MySQL, the Spring Boot app (built from the Dockerfile), Prometheus, and Grafana together. It targets the `docker` profile, not `local`.

```bash
DB_PASSWORD=yourpassword docker compose up -d
```

This is useful for a production-like smoke test, but not the recommended path for day-to-day development because it rebuilds the app image on every change.
