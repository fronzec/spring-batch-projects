package com.fronzec.plugins.harvester.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Populates {@link HarvestParamsHolder} from job parameters before the reader opens, and
 * resets the processor's in-memory attempt counter so that each step execution starts clean.
 *
 * <p>This is the canonical pattern for injecting job parameters without {@code @StepScope}:
 * the listener reads {@code JobParameters} in {@code beforeStep} and distributes values to
 * shared components before Spring Batch calls {@code ItemReader.open()}.
 *
 * <p><strong>Processor counter reset:</strong> {@link HarvestItemProcessor} maintains a
 * per-item in-memory attempt counter (keyed by {@code harvest_source.id}) as the fallback
 * retry-count mechanism when {@code RetrySynchronizationManager.getContext()} is unavailable.
 * {@code beforeStep} calls {@link HarvestItemProcessor#resetAttemptCounters()} to clear
 * that map before each execution, ensuring restarts do not carry over stale counts.
 */
public class HarvestStepListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(HarvestStepListener.class);

    private final HarvestParamsHolder holder;
    private final HarvestItemProcessor processor;

    /**
     * Constructs the listener.
     *
     * @param holder    shared parameter holder to populate in {@code beforeStep}
     * @param processor the item processor whose attempt counters must be reset
     */
    public HarvestStepListener(HarvestParamsHolder holder, HarvestItemProcessor processor) {
        this.holder = holder;
        this.processor = processor;
    }

    /**
     * Populates the parameter holder and resets the processor's attempt counters.
     *
     * @param stepExecution the current step execution (provides job parameters)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        var params = stepExecution.getJobParameters();

        String date = params.getString("DATE");
        String attemptNumber = params.getString("ATTEMPT_NUMBER");

        holder.setDate(date);
        holder.setAttemptNumber(attemptNumber);

        // Reset the processor's per-id attempt counter so restarts start at 0 per item.
        processor.resetAttemptCounters();

        log.info("harvest-step configured: date={} attemptNumber={}", date, attemptNumber);
    }
}
