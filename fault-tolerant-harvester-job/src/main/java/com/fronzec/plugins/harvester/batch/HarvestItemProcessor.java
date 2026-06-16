package com.fronzec.plugins.harvester.batch;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.retry.support.RetrySynchronizationManager;

/**
 * Processes each {@link HarvestRow} according to its flag columns.
 *
 * <h3>Processing order</h3>
 * <ol>
 *   <li>{@code abort_flag=TRUE} → throw {@link AbortJobException} (non-skippable, non-retryable).</li>
 *   <li>{@code poison_flag=TRUE} → throw {@link PoisonItemException} (skippable, not retryable).</li>
 *   <li>{@code transient_fail_until_attempt > 0} → R4 fixed-threshold retry logic (see below).</li>
 *   <li>Otherwise → return the item unchanged (happy path).</li>
 * </ol>
 *
 * <h3>R4 fixed-threshold retry mechanism</h3>
 * <p>{@code transient_fail_until_attempt} is a read-only column that holds the number of
 * attempts required before the item succeeds. The source row is NEVER mutated.
 *
 * <p>The processor attempts to read the current retry count from
 * {@link RetrySynchronizationManager#getContext()} (spring-retry thread-local, populated
 * when the processor runs inside a fault-tolerant chunk step). If the context is available,
 * it uses {@link org.springframework.retry.RetryContext#getRetryCount()} directly
 * ({@code 0} on first attempt, {@code 1} on first retry, etc.).
 *
 * <p><strong>Fallback (in-memory counter):</strong> If {@code RetrySynchronizationManager}
 * returns {@code null} (observed in some Spring Batch 6 configurations where the retry context
 * is not propagated into the item processor thread), the processor falls back to a per-id
 * {@code Map<Long, Integer>} that is incremented on each call and reset by
 * {@link #resetAttemptCounters()} (called from {@link HarvestStepListener#beforeStep}).
 * The in-memory counter has identical semantics: 0 on first call, 1 on first retry, etc.
 *
 * <p><em>Which approach was active is logged at DEBUG on each invocation and reported in
 * the apply-progress artifact.</em>
 *
 * <h3>Source row immutability</h3>
 * <p>The processor never writes to {@code harvest_source}. A chunk rollback cannot corrupt
 * the retry threshold because the threshold lives on the source row and is read-only.
 */
public class HarvestItemProcessor implements ItemProcessor<HarvestRow, HarvestRow> {

    private static final Logger log = LoggerFactory.getLogger(HarvestItemProcessor.class);

    /**
     * Per-id in-memory attempt counter used as fallback when
     * {@link RetrySynchronizationManager#getContext()} returns {@code null}.
     *
     * <p>Key: {@code harvest_source.id}. Value: number of times this item has been
     * presented to the processor in the current step execution (0-based on first call).
     * Reset by {@link #resetAttemptCounters()} in {@link HarvestStepListener#beforeStep}.
     */
    private final Map<Long, Integer> attemptCounters = new HashMap<>();

    /**
     * Processes one harvest row.
     *
     * @param item the row read from {@code harvest_source}
     * @return the same row unchanged (for the happy-path and successful retry)
     * @throws AbortJobException          if {@code abort_flag=TRUE} — non-skippable
     * @throws PoisonItemException        if {@code poison_flag=TRUE} — skippable, not retryable
     * @throws TransientProcessingException if the retry count is below the threshold — retryable
     */
    @Override
    public HarvestRow process(HarvestRow item) {

        // 1. Abort check — non-skippable, non-retryable; fails the job immediately.
        if (item.abortFlag()) {
            log.warn("Abort flag set for harvest_source.id={} — throwing AbortJobException", item.id());
            throw new AbortJobException(item.id());
        }

        // 2. Poison check — skippable, not retryable; dead-lettered with failure_type='SKIP'.
        if (item.poisonFlag()) {
            log.warn("Poison flag set for harvest_source.id={} — throwing PoisonItemException", item.id());
            throw new PoisonItemException(item.id());
        }

        // 3. Transient threshold check — retryable; dead-lettered with failure_type='RETRY_EXHAUSTED'
        //    only when retries are exhausted (then it becomes a skip).
        int threshold = item.transientFailUntilAttempt();
        if (threshold > 0) {
            int retryCount = resolveRetryCount(item.id());
            log.debug("Transient item id={} retryCount={} threshold={}", item.id(), retryCount, threshold);
            if (retryCount < threshold) {
                throw new TransientProcessingException(item.id(), retryCount, threshold);
            }
            log.info("Transient item id={} succeeded after {} retries", item.id(), retryCount);
        }

        // 4. Happy path — return item unchanged; writer will mark processed=TRUE.
        return item;
    }

    /**
     * Resolves the current retry count for the given item id.
     *
     * <p>Priority: {@link RetrySynchronizationManager} context (spring-retry thread-local).
     * Fallback: per-id in-memory counter incremented on every call to this method.
     *
     * @param itemId the {@code harvest_source.id}
     * @return 0-based retry count (0 = first attempt, 1 = first retry, …)
     */
    int resolveRetryCount(long itemId) {
        var context = RetrySynchronizationManager.getContext();
        if (context != null) {
            int count = context.getRetryCount();
            log.debug("retry-count source=RetrySynchronizationManager id={} count={}", itemId, count);
            return count;
        }

        // Fallback: in-memory per-id counter.
        // getOrDefault returns the count BEFORE this call (i.e., number of prior attempts),
        // then we store count+1 for the next call.
        int currentCount = attemptCounters.getOrDefault(itemId, 0);
        attemptCounters.put(itemId, currentCount + 1);
        log.debug("retry-count source=in-memory-fallback id={} count={}", itemId, currentCount);
        return currentCount;
    }

    /**
     * Resets all per-id attempt counters.
     *
     * <p>Called by {@link HarvestStepListener#beforeStep} at the start of each step execution
     * so that restarts do not carry over stale counts from a previous run.
     */
    public void resetAttemptCounters() {
        attemptCounters.clear();
        log.debug("Attempt counters reset (beforeStep)");
    }
}
