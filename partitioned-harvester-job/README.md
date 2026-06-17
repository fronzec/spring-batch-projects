# partitioned-harvester-job

Spring Batch plugin demonstrating id-range partitioning for parallel billing: one
`billing_charge` per `usage_record`, flat-rate cost (`units × rate`), parallel
execution via a bounded thread pool, and — most importantly — **partitioned restart
with no double-billing**. Delivered as a standalone shaded JAR and loaded dynamically
by the `fr-batch-service` host.

## Purpose

This module is a pedagogical showcase of Spring Batch 6 partitioning primitives:

| Scenario | Mechanism |
|---|---|
| Cheap partitioning | `SELECT MIN(id), MAX(id)` — no full-table scan |
| Parallel execution | `TaskExecutorPartitionHandler` over a `ThreadPoolTaskExecutor` |
| Per-partition reader isolation | `ThreadLocal<JdbcCursorItemReader>` (no `@StepScope`) |
| Idempotent write | Guarded `INSERT … WHERE NOT EXISTS` on UNIQUE `source_id` |
| Partitioned restart | `saveState(true)` → cursor offset per partition; completed partitions skip |
| No double-billing proof | UNIQUE `billing_charge.source_id` makes re-running a no-op |

## Architecture

```
IdRangePartitioner
  SELECT MIN(id), MAX(id) FROM usage_record
  → N ExecutionContexts {minId, maxId, partitionName}
        │
        ▼
  PartitionStep (usageManagerStep)
    TaskExecutorPartitionHandler [gridSize=4 threads]
        │ per partition
        ▼
  Worker chunk step (usageWorkerStep) ×N
    PartitionedHarvesterReader [ThreadLocal JdbcCursorItemReader]
      WHERE id BETWEEN minId AND maxId ORDER BY id
        │
    RatingProcessor
      cost = Math.multiplyExact(units, rate)
        │
    BillingChargeWriter
      INSERT INTO billing_charge … WHERE NOT EXISTS (… WHERE source_id = ?)
```

### Key components

| Class | Role |
|---|---|
| `PartitionedHarvesterJobPlugin` | Plugin entry point; builds all steps and the job |
| `IdRangePartitioner` | Computes N equal-width id ranges using MIN/MAX arithmetic |
| `PartitionedHarvesterReader` | `ItemStreamReader` + `StepExecutionListener`; ThreadLocal delegate |
| `RatingProcessor` | Stateless flat-rate processor; uses `Math.multiplyExact` |
| `BillingChargeWriter` | Idempotent writer with guarded INSERT |
| `PartitionedHarvesterJobParametersValidator` | Fail-fast parameter validation |

### Partitioning vs chunking (important distinction)

- **Partition** — the unit of parallelism AND restart. Each partition covers an id range.
  `gridSize` = number of partitions = number of parallel worker threads. Larger gridSize
  means more parallelism but also more Spring Batch metadata rows.
- **Chunk** — the unit of transaction commit. Each chunk commits at most `chunkSize` items.
  Smaller chunkSize = more frequent commits = finer-grained restart checkpoints within a
  partition; larger chunkSize = fewer commits = higher throughput.

`gridSize` and `chunkSize` are independent. Doubling `chunkSize` does not change the
number of partitions or the partition boundaries.

### Why over-partition?

Setting `gridSize` to a small multiple of the physical thread-pool size (e.g. `gridSize=8`
with 4 threads) acts as a work queue: large partitions finish slower than small ones, and
free threads can pick up the remaining partitions. This absorbs skew from id gaps.

For this plugin, `gridSize` equals the thread-pool size (both default to 4) because the
table is assumed to have roughly uniform id density.

### ThreadLocal reader — why no `@StepScope`

`configureJob` constructs all beans without a Spring application context, so `@StepScope`
and `@Value("#{stepExecutionContext[...]}")` are unavailable. The single worker step object
is reused across N threads by `TaskExecutorPartitionHandler`. If a plain instance field
held the per-partition reader, one thread's `beforeStep` would overwrite another thread's
state.

The solution: `PartitionedHarvesterReader` implements both `ItemStreamReader` and
`StepExecutionListener`. Its `beforeStep(stepExecution)` reads `minId`/`maxId` from the
execution context and stores a fully-configured `JdbcCursorItemReader` in a
`ThreadLocal`. Spring Batch guarantees that `beforeStep → open → read → close → afterStep`
all execute on the same thread (the one `TaskExecutorPartitionHandler` assigned to this
partition). `afterStep` calls `threadLocal.remove()` to prevent leaks.

## Job parameters

| Parameter | Type | Default | Identifying | Description |
|-----------|------|---------|-------------|-------------|
| `GRID_SIZE` | Long | 4 | Yes | Number of partitions (parallelism). See the constraint note below. |
| `CHUNK_SIZE` | Long | 100 | Yes | Items per commit (memory vs throughput). See the constraint note below. |

Parameters are validated fail-fast by `PartitionedHarvesterJobParametersValidator` before
any step runs.

### gridSize/chunkSize build-time constraint (known gap — SC-07.1)

`configureJob` receives no `JobParameters`, so the partition count and thread-pool size are
baked in at build time using the constants `GRID_SIZE_DEFAULT=4` and `CHUNK_SIZE_DEFAULT=100`.

A `GRID_SIZE` or `CHUNK_SIZE` value supplied at launch **cannot take effect**. Rather than
silently ignoring an override (which would be a footgun: you ask for 8 partitions and
silently get 4), the validator **rejects** any value that differs from the build-time
constant with a message that explains the constraint:

```
GRID_SIZE is fixed at 4 at build time in this single-JVM plugin and cannot be overridden
at launch (got: 8). Omit it to use the build-time default.
```

Full runtime-dynamic `gridSize` resolution (via a `JobExecutionListener.beforeJob` →
holder → partitioner wiring) is a known enhancement deferred to a future PR.

**Implication for restart**: use the default `gridSize=4` when launching; omit `GRID_SIZE`
and `CHUNK_SIZE` parameters entirely.

## Frozen-source precondition (REQ-09)

> **CRITICAL**: `usage_record` MUST NOT be mutated between the start of a job run and
> its completion, including any restart.

The partitioner computes partition boundaries using `SELECT MIN(id), MAX(id)`. Both runs of
a restart use the same identifying parameters; the ranges produced on Run 2 MUST be
identical to Run 1 for the restart to be correct.

If `usage_record` is mutated between runs:
- Rows inserted with ids outside the original `[MIN, MAX]` range will NOT be included in
  any partition range — they will be silently skipped.
- Rows deleted may leave gaps. Arithmetic ranges still cover `[MIN, MAX]`, so empty ranges
  are harmless, but the final `billing_charge` count may not equal `COUNT(usage_record)`.
- `MIN(id)` or `MAX(id)` changing creates different ranges on Run 2. Previously-completed
  partitions may no longer map to the same id ranges, breaking the restart guarantee.

**Consequence of violation**: restart correctness is void. The only safe approach is to
treat `usage_record` as immutable for the duration of a job run.

## Restart behavior

A partitioned restart is the headline capability of this plugin.

### What Spring Batch saves

For each worker step, Spring Batch writes a `BATCH_STEP_EXECUTION` row and a
`BATCH_STEP_EXECUTION_CONTEXT` row (because `saveState(true)` is set on the reader).
The context stores the reader's `currentItemCount` after each chunk commit.

### On Run 2 (identical identifying parameters)

1. Spring Batch finds the existing `FAILED` `JobExecution` for the same `JobInstance`.
2. The manager step checks each worker step's previous status.
3. Steps that are already `COMPLETED` are **skipped** — their partitions are not re-run.
4. Steps that are `FAILED` or `STARTED` are **restarted** — the reader reopens the cursor
   and fast-forwards past the already-committed items using the saved `currentItemCount`.
5. The job reaches `COMPLETED` when all worker steps are `COMPLETED`.

### No double-billing guarantee

Even if the cursor restart replays items from the beginning of a partially-committed chunk
(the chunk offset is stored per-commit, not per-item), the `BillingChargeWriter` uses a
guarded `INSERT … WHERE NOT EXISTS`. Any `billing_charge` row that already exists for a
`source_id` is silently skipped. The UNIQUE constraint on `billing_charge.source_id` is
the final safety net.

### Restart pitfall — identical parameters are required

> **CRITICAL**: re-launching with ANY change to an identifying parameter creates a NEW
> `JobInstance` — a fresh run from scratch, not a restart.

Use the **same** `RUN_DATE` (or whichever identifying parameters you supply) on both runs.
Introducing a `RUN_ID` or incrementing a counter creates a new `JobInstance`.

## Schema (V8 migration)

### `usage_record`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PK` | Partition key; MIN/MAX used for range computation |
| `subscriber_id` | `BIGINT NOT NULL` | |
| `units` | `BIGINT NOT NULL` | Billable usage units consumed |
| `rate` | `BIGINT NOT NULL` | Flat rate in minor currency units per usage unit |
| `recorded_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | |

No `processed` column — `usage_record` is FROZEN and never mutated by the job.

### `billing_charge`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PK` | |
| `source_id` | `BIGINT NOT NULL` | Soft FK to `usage_record.id`; UNIQUE (idempotency key) |
| `subscriber_id` | `BIGINT NOT NULL` | Copied from source row |
| `units` | `BIGINT NOT NULL` | Copied from source row |
| `rate` | `BIGINT NOT NULL` | Copied from source row |
| `cost` | `BIGINT NOT NULL` | `units × rate`, computed with `Math.multiplyExact` |
| `job_execution_id` | `BIGINT NULL` | Optional audit: which execution produced this row |
| `recorded_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | |

UNIQUE constraint: `uk_billing_charge_source UNIQUE (source_id)`.

Created by Flyway migration `V8__partitioned_harvester_tables.sql`.

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
mvn -B package -f partitioned-harvester-job/pom.xml
```

The shaded JAR is produced at
`partitioned-harvester-job/target/partitioned-harvester-job-1.0.0.jar`. All Spring Batch /
Spring Framework / SLF4J dependencies are `provided` and excluded from the shade.

## Running via the REST API

### 1. Upload the JAR

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/upload \
  -F "file=@partitioned-harvester-job/target/partitioned-harvester-job-1.0.0.jar" \
  -F "jobName=partitioned-harvester-job" \
  -F "version=1.0.0" \
  -F "mainClassName=com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin"
```

### 2. Run the job

```bash
# Omit GRID_SIZE and CHUNK_SIZE — defaults (4 and 100) are applied automatically.
curl -X POST http://localhost:8080/api/batch-service/jobs/run \
  -H "Content-Type: application/json" \
  -d '{
    "jobBeanName": "partitioned-harvester-job",
    "params": {
      "RUN_DATE": "2026-06-16"
    }
  }'
```

### 3. Restart after a failure (identical parameters)

```bash
# Use the SAME RUN_DATE (and any other identifying params) as the failed run:
curl -X POST http://localhost:8080/api/batch-service/jobs/run \
  -H "Content-Type: application/json" \
  -d '{
    "jobBeanName": "partitioned-harvester-job",
    "params": {
      "RUN_DATE": "2026-06-16"
    }
  }'
```

Spring Batch detects the existing `FAILED` execution, skips completed partitions, resumes
the failed partition from its last committed chunk offset, and completes without
double-billing any record.

## Schema dependency

This plugin requires the `usage_record` and `billing_charge` tables from Flyway migration
`V8__partitioned_harvester_tables.sql`. Ensure the host has run at least V8 before loading
this plugin.
