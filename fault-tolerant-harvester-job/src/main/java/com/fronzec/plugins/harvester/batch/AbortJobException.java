package com.fronzec.plugins.harvester.batch;

/**
 * Thrown by {@link HarvestItemProcessor} when a {@link HarvestRow} has {@code abort_flag=TRUE}.
 *
 * <p>This exception is <em>neither skippable nor retryable</em>. The step is configured with:
 * <pre>{@code
 *   .noSkip(AbortJobException.class)
 * }</pre>
 *
 * <p>When thrown, the step immediately fails and the job reaches {@code BatchStatus.FAILED}.
 * This is the trigger for the restart demo (PR#3): the source row's {@code abort_flag} is
 * cleared between launches, and the job re-runs with identical identifying parameters so
 * Spring Batch finds the prior FAILED execution and resumes from the last committed chunk offset.
 */
public class AbortJobException extends RuntimeException {

    /**
     * Constructs a new exception for the given source row id.
     *
     * @param sourceId the {@code harvest_source.id} of the abort row
     */
    public AbortJobException(long sourceId) {
        super("Abort triggered by harvest_source.id=" + sourceId + " (abort_flag=TRUE)");
    }
}
