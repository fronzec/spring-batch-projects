# ticket-pdf-job

Spring Batch plugin that generates signed PDF tickets with embedded QR codes for events.
Delivered as a standalone shaded JAR and loaded dynamically by the `fr-batch-service` host.

## What it does

For each row in `event_tickets` where `processed = FALSE`:

1. Computes an HMAC-SHA256 signed token: `{ticketId}|{ticketCode}.{base64url_hmac}`
2. Encodes the token into a QR code image (ZXing, error correction M, UTF-8)
3. Renders a PDF document (OpenPDF) containing event name, holder name, seat, location,
   date/time, ticket code, and the embedded QR image
4. Writes the PDF to `{OUTPUT_DIR}/{ticketId}.pdf` via `LocalFileStorage`
5. Inserts a row into `generated_files` (storage path, SHA-256 checksum, file size)
6. Updates `event_tickets.processed = TRUE`

Chunk size is 10. On any failure within a chunk, the chunk transaction rolls back and the job
exits as FAILED. Re-runs are safe: the reader filters on `processed = FALSE`.

## Job parameters

| Parameter      | Required | Description |
|----------------|----------|-------------|
| `DATE`         | No       | Processing date (informational, e.g. `2024-06-15`) |
| `OUTPUT_DIR`   | Yes      | Absolute or relative path where PDF files are written |
| `TOKEN_SECRET` | Yes      | HMAC-SHA256 signing key — **must be at least 32 bytes** (UTF-8 length) |
| `EVENT_ID`     | No       | When provided, only tickets with matching `event_id` are processed |

### TOKEN_SECRET minimum length

`HmacTokenService` enforces a minimum of 32 UTF-8 bytes for the secret. Shorter secrets
cause the job to fail fast with `IllegalArgumentException`. Example of a valid secret:

```
my-production-secret-key-minimum-32-bytes
```

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
mvn -B package -f ticket-pdf-job/pom.xml
```

The shaded JAR is produced at `ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar` (~2 MB).
OpenPDF and ZXing are bundled; Spring, spring-batch, slf4j, and spring-jdbc are excluded
(provided by the host at runtime).

## Running via the REST API

### 1. Upload the JAR

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/upload \
  -F "file=@ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar" \
  -F "jobName=ticket-pdf-job" \
  -F "version=1.0.0" \
  -F "mainClassName=com.fronzec.plugins.ticketpdf.TicketPdfJobPlugin" \
  -u admin:password
```

Save the `id` from the response.

### 2. Enable the definition

```bash
curl -X PUT http://localhost:8080/api/batch-service/jobs/definitions/{id}/enable \
  -u admin:password
```

### 3. Approve the definition

```bash
curl -X PUT http://localhost:8080/api/batch-service/jobs/definitions/{id}/approve \
  -H "Content-Type: application/json" \
  -d '{"approved_by":"your-name"}' \
  -u admin:password
```

### 4. Load the plugin

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/definitions/{id}/load \
  -u admin:password
```

### 5. Run the job

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/run \
  -H "Content-Type: application/json" \
  -d '{
    "jobBeanName": "ticket-pdf-job",
    "params": {
      "DATE": "2024-06-15",
      "OUTPUT_DIR": "/var/data/tickets",
      "TOKEN_SECRET": "my-production-secret-key-minimum-32-bytes",
      "EVENT_ID": "42"
    }
  }' \
  -u admin:password
```

## FileStorage seam (v1 → v2)

The writer depends only on the `FileStorage` interface. `LocalFileStorage` is the v1
implementation writing to the local filesystem. A future `S3FileStorage` that uploads to
an S3 bucket is a drop-in replacement: implement `FileStorage`, change the `TicketPdfJobPlugin`
wiring to instantiate `S3FileStorage` instead of delegating to `TicketFileItemWriter`'s lazy
init. No changes to the reader, processor, or writer business logic are required.

`generated_files.storage_type` already stores `"LOCAL"` vs `"S3"` to distinguish entries
written by each implementation.

## Schema dependency

The plugin requires `event_tickets` and `generated_files` tables created by the host
Flyway migration `V5__ticket_pdf_tables.sql`. Ensure the host has run at least V5 before
loading this plugin.
