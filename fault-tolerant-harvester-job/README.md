# fault-tolerant-harvester-job

Spring Batch plugin demonstrating a fault-tolerant chunk step: skip-to-dead-letter,
retry with exponential backoff, transaction-independent dead-letter auditing
(`PROPAGATION_REQUIRES_NEW`), and true job restart. Delivered as a standalone shaded JAR
and loaded dynamically by the `fr-batch-service` host.

## Purpose

This module is a pedagogical showcase of Spring Batch 6 fault-tolerance primitives:

| Scenario | Mechanism |
|---|---|
| Poison input (permanent failure) | `skip()` → `HarvestSkipListener` writes dead-letter row |
| Transient failure (recoverable) | `retry()` with `ExponentialBackOffPolicy` |
| Retry exhaustion | Item becomes a skip → dead-letter with `failure_type='RETRY_EXHAUSTED'` |
| Skip budget overflow | `skipLimit` breached → `BatchStatus.FAILED` |
| Abort (non-skippable) | `AbortJobException` → `noSkip()` + `noRetry()` → immediate step failure |
| Dead-letter isolation | `PROPAGATION_REQUIRES_NEW` → audit row survives chunk rollback |
| Restart | `setSaveState(true)` on reader → cursor fast-forwards on identical-param relaunch |

## Job parameters

| Parameter        | Required | Identifying | Description |
|------------------|----------|-------------|-------------|
| `DATE`           | Yes      | Yes         | Processing date in `yyyy-MM-dd` format. Validated fail-fast by `HarvestJobParametersValidator` before any step runs. |
| `ATTEMPT_NUMBER` | No       | Yes         | Attempt counter supplied by the host. **See the restart pitfall below.** |
| `DESCRIPTION`    | No       | No          | Informational only; not validated. |

Parameters are validated fail-fast by `HarvestJobParametersValidator` (implements
`JobParametersValidator`). Missing or unparseable `DATE` throws
`InvalidJobParametersException` before any step executes.

## Fault-tolerant step configuration

Single chunk step `harvestStep` (chunk size = 5):

```
Reader   : JdbcCursorItemReader on harvest_source ORDER BY id
           setSaveState(true) — persists currentItemCount to BATCH_STEP_EXECUTION_CONTEXT
Processor: HarvestItemProcessor — abort / poison / transient threshold logic
Writer   : HarvestItemWriter — idempotent UPDATE ... WHERE processed = FALSE
```

Fault-tolerance parameters:

| Setting | Value |
|---|---|
| `skipLimit` | 5 |
| Skippable | `PoisonItemException`, `TransientProcessingException` (after retry exhaustion) |
| Non-skippable | `AbortJobException` |
| `retryLimit` | 3 |
| Retryable | `TransientProcessingException` |
| Non-retryable | `PoisonItemException` |
| Backoff | `ExponentialBackOffPolicy` (initial 10 ms, multiplier 2.0, max 100 ms) |

Listeners:

- `HarvestSkipListener` — writes `harvest_dead_letter` rows under `PROPAGATION_REQUIRES_NEW`
- `HarvestRetryListener` — logs each retry attempt for pedagogical backoff visibility

## How rows drive behaviour

Rows in `harvest_source` control which fault-tolerance path is exercised:

| Column | Value | Effect |
|---|---|---|
| `poison_flag` | `TRUE` | Processor throws `PoisonItemException` → skipped, dead-lettered (`failure_type='SKIP'`) |
| `transient_fail_until_attempt` | `N > 0` | Processor throws `TransientProcessingException` while retry count < N; succeeds at N |
| `abort_flag` | `TRUE` | Processor throws `AbortJobException` → non-skippable, non-retryable → step FAILS |
| All flags | `FALSE`, threshold `0` | Normal row: passes processor, writer marks `processed=TRUE` |

`transient_fail_until_attempt` is a **read-only threshold** — it is never mutated at runtime.
The processor reads the in-flight Spring Batch retry count (via
`RetrySynchronizationManager.getContext().getRetryCount()`) and compares it to the threshold.
Chunk rollbacks cannot corrupt this value because the source row is never written.

## Schema

### `harvest_source`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PK` | |
| `payload` | `VARCHAR(2048) NOT NULL` | Arbitrary payload string |
| `poison_flag` | `BOOLEAN NOT NULL DEFAULT FALSE` | Permanent failure trigger |
| `transient_fail_until_attempt` | `INT NOT NULL DEFAULT 0` | Retry threshold (read-only) |
| `abort_flag` | `BOOLEAN NOT NULL DEFAULT FALSE` | Non-skippable abort trigger for restart demo |
| `processed` | `BOOLEAN NOT NULL DEFAULT FALSE` | Set to `TRUE` by the writer (idempotent) |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | |

### `harvest_dead_letter`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PK` | |
| `source_id` | `BIGINT NULL` | Soft reference to `harvest_source.id` (no FK — audit rows survive source purge) |
| `raw_payload` | `VARCHAR(2048) NULL` | Payload at skip time (`NULL` for read-skips) |
| `failure_phase` | `VARCHAR(16) NOT NULL` | `READ`, `PROCESS`, or `WRITE` |
| `failure_type` | `VARCHAR(16) NOT NULL` | `SKIP` or `RETRY_EXHAUSTED` |
| `exception_class` | `VARCHAR(512) NOT NULL` | Fully-qualified exception class name |
| `exception_msg` | `VARCHAR(2048) NULL` | Exception message, truncated to 2048 chars |
| `attempt_count` | `INT NOT NULL DEFAULT 1` | See "attempt_count semantics" below |
| `job_execution_id` | `BIGINT NOT NULL` | `BATCH_JOB_EXECUTION.id` for the run that produced the record |
| `recorded_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | |

Created by Flyway migration `V7__fault_tolerant_harvester_tables.sql`.

### attempt_count semantics

For `SKIP` items the value is always `1` (a single attempt was made).

For `RETRY_EXHAUSTED` items the value is `retryLimit + 1 = 4` — this represents the TOTAL
number of processing calls: 1 initial attempt plus 3 retries. The stored value is greater by 1
than the raw `retryLimit` constant because total-calls is a more useful diagnostic than the
configuration value alone.

## Restart pitfall — ATTEMPT_NUMBER is identifying

> **CRITICAL**: the host (`JobsManagerService`) launches with `DATE` + `ATTEMPT_NUMBER` as
> identifying parameters and uses **no `RunIdIncrementer`**.

This means:

- `DATE=2026-01-01, ATTEMPT_NUMBER=1` → creates `JobInstance #A`
- Re-submitting `DATE=2026-01-01, ATTEMPT_NUMBER=1` (IDENTICAL) → Spring Batch finds the
  existing `FAILED` `JobExecution` for `JobInstance #A` and **restarts** it from the last
  committed chunk offset.
- **Incrementing** `ATTEMPT_NUMBER`: `DATE=2026-01-01, ATTEMPT_NUMBER=2` → creates a brand-new
  `JobInstance #B` and runs from row 1 — this is a **fresh run, NOT a restart**.

A true restart requires submitting the **same `DATE` and the same `ATTEMPT_NUMBER`** that the
failed run used. The host operator must know which attempt number failed and resubmit that
exact number. Incrementing the attempt counter to "try again" defeats the restart mechanism
entirely.

The restart mechanism itself is driven by `setSaveState(true)` on the reader: after each chunk
commit, Spring Batch serialises the cursor's `currentItemCount` into
`BATCH_STEP_EXECUTION_CONTEXT`. On restart, the reader reopens the cursor from row 1 and
fast-forwards by skipping the first `currentItemCount` rows — so only uncommitted rows are
reprocessed. The writer's `WHERE processed = FALSE` guard provides additional idempotency in
case a boundary row is redelivered.

Note: the reader SQL reads **all** rows (`SELECT … FROM harvest_source ORDER BY id` with no
`processed = FALSE` filter). Filtering by `processed` would shrink the result set on restart
and cause the count-based skip-ahead to land on the wrong row.

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
mvn -B package -f fault-tolerant-harvester-job/pom.xml
```

The shaded JAR is produced at
`fault-tolerant-harvester-job/target/fault-tolerant-harvester-job-1.0.0.jar`. The shaded JAR
includes only plugin classes and `spring-retry`; all Spring Batch / Spring Framework / SLF4J
dependencies are `provided` and excluded from the shade. Approximate JAR size: < 100 KB.

## Running via the REST API

### 1. Upload the JAR

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/upload \
  -F "file=@fault-tolerant-harvester-job/target/fault-tolerant-harvester-job-1.0.0.jar" \
  -F "jobName=fault-tolerant-harvester-job" \
  -F "version=1.0.0" \
  -F "mainClassName=com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin"
```

### 2. Run the job

```bash
curl -X POST http://localhost:8080/api/batch-service/jobs/run \
  -H "Content-Type: application/json" \
  -d '{
    "jobBeanName": "fault-tolerant-harvester-job",
    "params": {
      "DATE": "2026-01-01",
      "ATTEMPT_NUMBER": "1"
    }
  }'
```

### 3. Restart after a failure (identical params)

```bash
# Use the SAME DATE and ATTEMPT_NUMBER as the failed run:
curl -X POST http://localhost:8080/api/batch-service/jobs/run \
  -H "Content-Type: application/json" \
  -d '{
    "jobBeanName": "fault-tolerant-harvester-job",
    "params": {
      "DATE": "2026-01-01",
      "ATTEMPT_NUMBER": "1"
    }
  }'
```

Spring Batch will detect the existing `FAILED` execution, resume from the last committed
chunk, and complete without reprocessing already-committed rows.

## Schema dependency

This plugin requires the `harvest_source` and `harvest_dead_letter` tables from Flyway
migration `V7__fault_tolerant_harvester_tables.sql`. Ensure the host has run at least V7
before loading this plugin.
