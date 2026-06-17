// DTO interfaces mirroring the exact snake_case wire JSON from fr-batch-service.
// Backend uses spring.jackson.property-naming-strategy=SNAKE_CASE, so all field
// names here match the raw wire keys, NOT camelCase component names.

// GET /api/batch-service/jobs/plugins  — List<MergedPluginInfoResponse>  (PUBLIC endpoint)
export interface PluginSummary {
  job_name: string;
  version: string;
  display_name: string | null;
  description: string | null;
  author: string | null;                              // null for UPLOADED source
  tags: string[] | null;                              // null for UPLOADED source
  estimated_runtime_seconds: number | null;           // Long -> number | null
  default_parameters: Record<string, string> | null;  // null for UPLOADED
  source: 'CLASSPATH' | 'UPLOADED';
  status: 'ACTIVE' | 'ENABLED' | 'DISABLED' | 'LOADED';
}

// GET /api/batch-service/jobs/definitions      — List<JobDefinitionResponse>
// GET /api/batch-service/jobs/definitions/{id} — JobDefinitionResponse
export interface JobDefinition {
  id: number;
  job_name: string;
  display_name: string | null;
  version: string;
  enabled: boolean | null;
  load_status: string | null;       // e.g. "LOADED" / "UNLOADED" / null
  jar_file_name: string;
  main_class_name: string;
  created_at: string | null;        // LocalDateTime -> ISO-8601 string or null
  approval_status: string | null;   // "APPROVED" / "REJECTED" / "PENDING" / null
  approved_by: string | null;
  approved_at: string | null;       // ISO-8601 string or null
}

// GET /api/batch-service/jobs/running-all  — Map<jobName, Map<executionId, params>>
// Object-of-objects; map keys are job/execution names as-is (no Jackson rename on keys).
export type RunningJobs = Record<string, Record<string, string>>;
