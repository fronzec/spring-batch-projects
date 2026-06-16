# ticket-bundle-job

Spring Batch plugin that bundles all generated ticket PDFs for an event into a single ZIP
archive. Delivered as a standalone shaded JAR and loaded dynamically by the `fr-batch-service`
host.

## What it does

For a given `EVENT_ID`, the job:

1. Reads all rows from `generated_files` joined through `event_tickets` where `event_id = ?`,
   ordered by `ticket_id` ascending.
2. Streams each PDF at its absolute `storage_path` into a temp ZIP file on disk.
   ZIP entry names are prefixed with the ticket ID (`<ticketId>-<filename>`) for determinism.
3. Uploads the completed ZIP to `{OUTPUT_DIR}/bundles/event-{EVENT_ID}.zip` via
   `LocalFileStorage` (SHA-256 checksum computed during upload).
4. Upserts one row in `generated_bundles` (DELETE + INSERT — portable across H2 and MySQL).
5. Deletes the temp file.

The job is **idempotent**: re-running for the same event overwrites the ZIP and replaces the
single `generated_bundles` row. The `UNIQUE(event_id)` constraint enforces at most one current
bundle per event.

If the event has no `generated_files` rows the job completes successfully without producing
any output (no-op). If any source PDF is missing on disk the job fails immediately
(fail-fast policy, v1).

## Job parameters

| Parameter    | Required | Description |
|--------------|----------|-------------|
| `EVENT_ID`   | Yes      | Positive long identifying the event to bundle |
| `OUTPUT_DIR` | Yes      | Directory where the output ZIP is written (`bundles/event-{id}.zip` subpath created automatically) |
| `DATE`       | No       | Processing date (informational, e.g. `2024-06-15`) |

Parameters are validated fail-fast by `BundleJobParametersValidator` before any step executes.

## Two-step flow

```
Step 1 — ticketBundleZipStep (chunk, size 20)
  Reader : JdbcCursorItemReader over generated_files JOIN event_tickets WHERE event_id = ?
  Writer : ZipBundleItemWriter streams each PDF into a temp ZipOutputStream
  Listener: BundleStepListener — binds params, sets reader SQL, closes ZIP in afterStep,
            writes bundle.zip.temp.path and bundle.ticket.count to Job ExecutionContext

Step 2 — ticketBundlePersistStep (tasklet)
  BundlePersistTasklet — reads Job ctx, uploads ZIP via LocalFileStorage,
                         upserts generated_bundles, deletes temp file
```

Cross-step state handoff uses two serializable Job ExecutionContext keys:

| Key | Type | Description |
|-----|------|-------------|
| `bundle.zip.temp.path` | String | Absolute path of the temp ZIP (absent for empty events) |
| `bundle.ticket.count`  | int    | Number of PDFs zipped (0 for empty events) |

## Building the fat JAR

Requires Maven and a GitHub Packages token (the `batch-job-api` dependency is hosted there).

Configure `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

Then build:

```bash
mvn -B package -f ticket-bundle-job/pom.xml
```

The shaded JAR is produced at `ticket-bundle-job/target/ticket-bundle-job-1.0.0.jar`.
This plugin uses only JDK built-ins (`java.util.zip`) so the shaded JAR contains only
plugin classes and is very small (< 50 KB).

## Running via the REST API

### 1. Upload the JAR

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/upload \
  -F "file=@ticket-bundle-job/target/ticket-bundle-job-1.0.0.jar" \
  -F "jobName=ticket-bundle-job" \
  -F "version=1.0.0" \
  -F "mainClassName=com.fronzec.plugins.ticketbundle.TicketBundleJobPlugin"
```

### 2. Run the job

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/run \
  -H "Content-Type: application/json" \
  -d '{
    "jobBeanName": "ticket-bundle-job",
    "params": {
      "DATE": "2024-06-15",
      "OUTPUT_DIR": "/var/data/bundles",
      "EVENT_ID": "42"
    }
  }'
```

## Idempotency

Re-running the job for the same `EVENT_ID` is safe:
- The upload key `bundles/event-{id}.zip` is deterministic — re-uploading overwrites the previous ZIP.
- The upsert (DELETE + INSERT) replaces the existing `generated_bundles` row.
- After any number of re-runs, exactly one ZIP file and one DB row exist for the event.

## Schema dependency

This plugin requires the `event_tickets` and `generated_files` tables from Flyway migration
`V5__ticket_pdf_tables.sql`, and the `generated_bundles` table from `V6__ticket_bundle_tables.sql`.
Ensure the host has run at least V6 before loading this plugin.
