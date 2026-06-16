package com.fronzec.plugins.harvester.batch;

/**
 * Thrown by {@link HarvestItemProcessor} when a {@link HarvestRow} has {@code poison_flag=TRUE}.
 *
 * <p>This exception is <em>skippable</em> but <em>not retryable</em>. The step is configured with:
 * <pre>{@code
 *   .skip(PoisonItemException.class)
 *   .noRetry(PoisonItemException.class)
 * }</pre>
 *
 * <p>When skipped, {@link HarvestSkipListener#onSkipInProcess} writes a dead-letter record
 * with {@code failure_type='SKIP'} under {@code PROPAGATION_REQUIRES_NEW}.
 */
public class PoisonItemException extends RuntimeException {

    /**
     * Constructs a new exception for the given source row id.
     *
     * @param sourceId the {@code harvest_source.id} of the poison row
     */
    public PoisonItemException(long sourceId) {
        super("Poison item detected for harvest_source.id=" + sourceId);
    }

    /**
     * Constructs a new exception with a custom message.
     *
     * @param message descriptive message
     */
    public PoisonItemException(String message) {
        super(message);
    }
}
