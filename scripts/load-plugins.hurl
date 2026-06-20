# load-plugins.hurl — declarative replacement for the curl/jq/python3 HTTP chain
# in scripts/load-plugins.sh. Drives ONE plugin through the full lifecycle:
#   upload -> resolve id -> approve -> enable -> load
#
# This is a proof-of-concept for a single plugin. The thin wrapper that loops it
# over all four plugins (and runs the optional Maven build) comes next, once this
# flow is validated against a running backend.
#
# Run (from the repo root, backend up, JAR already built):
#   hurl --variable base=http://localhost:8080/api/batch-service \
#        --variable user=admin --variable pass=admin123 \
#        --variable jobName=ticket-pdf-job \
#        --variable version=1.0.0 \
#        --variable mainClass=com.fronzec.plugins.ticketpdf.TicketPdfJobPlugin \
#        --variable jar=ticket-pdf-job/target/ticket-pdf-job-1.0.0.jar \
#        scripts/load-plugins.hurl
#
# Idempotent: re-running is a no-op. Upload accepts 201 (new) or 409 (exists);
# approve and load accept their "already done" 409s. The definition id is ALWAYS
# resolved from GET /definitions by job_name, so the flow never depends on parsing
# the upload response — which carries no id on a 409.

# ─── 1. Upload the JAR (multipart). 201 Created, or 409 if already uploaded. ───
POST {{base}}/jobs/upload
[BasicAuth]
{{user}}: {{pass}}
[MultipartFormData]
file: file,{{jar}};
jobName: {{jobName}}
version: {{version}}
mainClassName: {{mainClass}}
HTTP *
[Asserts]
status toString matches "^(201|409)$"

# ─── 2. Resolve the definition id by job_name — the single source of truth. ───
# The API serializes snake_case (Jackson SNAKE_CASE), so the field is job_name.
GET {{base}}/jobs/definitions
[BasicAuth]
{{user}}: {{pass}}
HTTP 200
[Captures]
def_id: jsonpath "$[?(@.job_name=='{{jobName}}')].id"

# ─── 3. Approve. 200 Approved, or 409 if already approved. ───
# /load rejects PENDING definitions, so approval is required first. The body
# field is approvedBy (camelCase in; the request record binds it directly).
PUT {{base}}/jobs/definitions/{{def_id}}/approve
Content-Type: application/json
[BasicAuth]
{{user}}: {{pass}}
{
  "approvedBy": "{{user}}"
}
HTTP *
[Asserts]
status toString matches "^(200|409)$"

# ─── 4. Enable the definition (flips enabled = true; idempotent). ───
PUT {{base}}/jobs/definitions/{{def_id}}/enable
[BasicAuth]
{{user}}: {{pass}}
HTTP 200

# ─── 5. Load the plugin into the registry. 200 Loaded, or 409 if already loaded. ───
POST {{base}}/jobs/definitions/{{def_id}}/load
[BasicAuth]
{{user}}: {{pass}}
HTTP *
[Asserts]
status toString matches "^(200|409)$"
