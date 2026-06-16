package com.fronzec.plugins.harvester.batch;

/**
 * Thrown by {@link HarvestItemProcessor} when a {@link HarvestRow} has a
 * {@code transient_fail_until_attempt} threshold that has not yet been reached.
 *
 * <p>This exception is both <em>skippable</em> and <em>retryable</em>. The step is configured:
 * <pre>{@code
 *   .skip(TransientProcessingException.class)   // reached only after retry exhaustion
 *   .retry(TransientProcessingException.class)
 * }</pre>
 *
 * <p>Spring Batch will retry the item up to {@code retryLimit=3} times with exponential backoff.
 * If retries are exhausted the item is treated as a skip, and
 * {@link HarvestSkipListener#onSkipInProcess} writes a dead-letter record with
 * {@code failure_type='RETRY_EXHAUSTED'}.
 */
public class TransientProcessingException extends RuntimeException {

    /**
     * Constructs a new exception for the given source row id.
     *
     * @param sourceId   the {@code harvest_source.id} of the transient row
     * @param retryCount the current retry count when this exception was thrown
     * @param threshold  the {@code transient_fail_until_attempt} threshold
     */
    public TransientProcessingException(long sourceId, int retryCount, int threshold) {
        super("Transient failure for harvest_source.id=" + sourceId
                + " retryCount=" + retryCount + " threshold=" + threshold);
    }

    /**
     * Constructs a new exception with a custom message.
     *
     * @param message descriptive message
     */
    public TransientProcessingException(String message) {
        super(message);
    }
}
