package com.fronzec.plugins.harvester.batch;

/**
 * Immutable domain record representing one row from {@code harvest_source}.
 *
 * <p>All fields are read directly from the database; none are mutated during processing.
 * {@code transientFailUntilAttempt} is a read-only threshold — the processor compares
 * the current Spring Batch retry count against this value but NEVER updates the source row.
 *
 * @param id                       primary key of the harvest_source row
 * @param payload                  the raw payload string (up to 2 048 chars)
 * @param poisonFlag               when {@code true} the item is skipped and dead-lettered
 *                                 with {@code failure_type='SKIP'}
 * @param transientFailUntilAttempt read-only threshold; the processor throws
 *                                 {@link TransientProcessingException} while the in-memory
 *                                 retry count is below this value, then succeeds
 * @param abortFlag                when {@code true} the processor throws
 *                                 {@link AbortJobException}, which is non-skippable and
 *                                 non-retryable, causing the job to fail (restart demo)
 */
public record HarvestRow(
        long id,
        String payload,
        boolean poisonFlag,
        int transientFailUntilAttempt,
        boolean abortFlag) {}
