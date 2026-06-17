package com.fronzec.plugins.partitionedharvester.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * Fail-fast pre-flight validation of job parameters for {@code partitioned-harvester-job}.
 *
 * <p>Registered on the {@code JobBuilder}, so it runs at job launch <strong>before any step
 * starts</strong>. Invalid or zero/negative parameter values produce a clean
 * {@link InvalidJobParametersException} rather than a mid-step failure.
 *
 * <h3>Validations performed</h3>
 * <ol>
 *   <li>{@code GRID_SIZE}, if present, must be a positive long (≥ 1).</li>
 *   <li>{@code CHUNK_SIZE}, if present, must be a positive long (≥ 1).</li>
 * </ol>
 *
 * <h3>Missing parameters</h3>
 * <p>Missing parameters are treated as valid — the plugin's {@code getDefaultParameters()}
 * provides the defaults, which are applied at job launch time by the host framework.
 *
 * <h3>gridSize build-time constraint note</h3>
 * <p>{@code configureJob} receives no {@code JobParameters}, so the partition count and
 * thread-pool size are baked in at construction time using {@code GRID_SIZE_DEFAULT = 4}.
 * If {@code GRID_SIZE} is supplied at launch, it controls validator acceptance but does NOT
 * change the number of physical partitions — this is a single-JVM pedagogical constraint.
 * For a future enhancement that reads gridSize dynamically, the partitioner and step builder
 * would need a holder populated by a {@code JobExecutionListener.beforeJob}.
 *
 * @see com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin
 */
public class PartitionedHarvesterJobParametersValidator implements JobParametersValidator {

    /**
     * Validates the job parameters.
     *
     * @param parameters the parameters to validate; may be empty but must not be null
     * @throws InvalidJobParametersException if {@code GRID_SIZE} or {@code CHUNK_SIZE}
     *         is present and not a positive value (zero or negative)
     */
    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null) {
            throw new InvalidJobParametersException("Job parameters must not be null");
        }

        Long gridSize = parameters.getLong("GRID_SIZE");
        if (gridSize != null && gridSize <= 0) {
            throw new InvalidJobParametersException(
                    "GRID_SIZE must be a positive value (>= 1), got: " + gridSize);
        }

        Long chunkSize = parameters.getLong("CHUNK_SIZE");
        if (chunkSize != null && chunkSize <= 0) {
            throw new InvalidJobParametersException(
                    "CHUNK_SIZE must be a positive value (>= 1), got: " + chunkSize);
        }
    }
}
