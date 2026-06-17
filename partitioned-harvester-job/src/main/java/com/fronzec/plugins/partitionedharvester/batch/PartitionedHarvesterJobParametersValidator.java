package com.fronzec.plugins.partitionedharvester.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * Fail-fast pre-flight validation of job parameters for {@code partitioned-harvester-job}.
 *
 * <p>Registered on the {@code JobBuilder}, so it runs at job launch <strong>before any step
 * starts</strong>. Invalid parameter values produce a clean
 * {@link InvalidJobParametersException} rather than a mid-step failure or a silently ignored
 * override.
 *
 * <h3>Build-time constraint (why values are pinned, not range-checked)</h3>
 * <p>{@code configureJob} receives no {@code JobParameters}, so the partition count
 * ({@code GRID_SIZE}) and the chunk commit size ({@code CHUNK_SIZE}) are baked in at
 * construction time. A {@code GRID_SIZE} or {@code CHUNK_SIZE} supplied at launch therefore
 * <strong>cannot take effect</strong>. Rather than silently ignore such an override (a
 * footgun: the caller asks for 8 partitions and silently gets 4), this validator rejects any
 * value that differs from the build-time constant, with a message that explains the
 * constraint. This makes the single-JVM pedagogical limitation impossible to miss.
 *
 * <h3>Validations performed</h3>
 * <ol>
 *   <li>Parameters object must not be null.</li>
 *   <li>{@code GRID_SIZE}, if present, must equal the build-time grid size.</li>
 *   <li>{@code CHUNK_SIZE}, if present, must equal the build-time chunk size.</li>
 * </ol>
 *
 * <h3>Missing parameters</h3>
 * <p>Missing parameters are valid — the plugin's {@code getDefaultParameters()} supplies the
 * defaults, which equal the build-time constants.
 *
 * <p>A future enhancement that reads {@code GRID_SIZE} dynamically (via a
 * {@code JobExecutionListener.beforeJob} → holder → partitioner wiring) would replace the
 * equality checks here with positivity range checks.
 *
 * @see com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin
 */
public class PartitionedHarvesterJobParametersValidator implements JobParametersValidator {

    private final long expectedGridSize;
    private final long expectedChunkSize;

    /**
     * Constructs the validator with the build-time constants it must enforce.
     *
     * @param expectedGridSize  the build-time partition count baked into {@code configureJob}
     * @param expectedChunkSize the build-time chunk commit size baked into {@code configureJob}
     */
    public PartitionedHarvesterJobParametersValidator(long expectedGridSize, long expectedChunkSize) {
        this.expectedGridSize = expectedGridSize;
        this.expectedChunkSize = expectedChunkSize;
    }

    /**
     * Validates the job parameters.
     *
     * @param parameters the parameters to validate; must not be null (may be empty)
     * @throws InvalidJobParametersException if {@code parameters} is null, or if
     *         {@code GRID_SIZE}/{@code CHUNK_SIZE} is present and differs from its build-time
     *         constant (overrides cannot take effect in this single-JVM plugin)
     */
    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null) {
            throw new InvalidJobParametersException("Job parameters must not be null");
        }

        Long gridSize = parameters.getLong("GRID_SIZE");
        if (gridSize != null && gridSize != expectedGridSize) {
            throw new InvalidJobParametersException(
                    "GRID_SIZE is fixed at " + expectedGridSize + " at build time in this "
                            + "single-JVM plugin and cannot be overridden at launch (got: "
                            + gridSize + "). Omit it to use the build-time default.");
        }

        Long chunkSize = parameters.getLong("CHUNK_SIZE");
        if (chunkSize != null && chunkSize != expectedChunkSize) {
            throw new InvalidJobParametersException(
                    "CHUNK_SIZE is fixed at " + expectedChunkSize + " at build time in this "
                            + "single-JVM plugin and cannot be overridden at launch (got: "
                            + chunkSize + "). Omit it to use the build-time default.");
        }
    }
}
