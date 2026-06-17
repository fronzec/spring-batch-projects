package com.fronzec.plugins.partitionedharvester.batch;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Test utility that constructs minimal {@link StepExecution} instances for unit tests.
 *
 * <p>Avoids the need for a full Spring Batch infrastructure (JobRepository, DataSource, etc.)
 * in unit tests that only need a step execution context with specific keys set.
 */
final class TestStepExecutionFactory {

    private TestStepExecutionFactory() {}

    /**
     * Creates a {@link StepExecution} with the given step name and a pre-populated
     * {@link ExecutionContext} containing the provided key-value pairs.
     *
     * @param stepName         the step name to use
     * @param executionContext  the pre-populated execution context (copied into the step)
     * @return a minimal StepExecution suitable for unit tests
     */
    static StepExecution create(String stepName, ExecutionContext executionContext) {
        JobInstance instance = new JobInstance(1L, "partitioned-harvester-job");
        JobExecution jobExecution = new JobExecution(1L, instance, new JobParameters());
        StepExecution stepExecution = new StepExecution(stepName, jobExecution);
        // Copy all keys from the provided context into the step's execution context
        executionContext.entrySet().forEach(entry ->
                stepExecution.getExecutionContext().put(entry.getKey(), entry.getValue()));
        return stepExecution;
    }
}
