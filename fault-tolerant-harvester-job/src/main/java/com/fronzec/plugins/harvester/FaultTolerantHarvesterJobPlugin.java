package com.fronzec.plugins.harvester;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.plugins.harvester.batch.HarvestJobParametersValidator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch plugin that demonstrates a fault-tolerant chunk step:
 * skip-to-dead-letter, retry with exponential backoff, and restartability.
 *
 * <p>Implemented as a shaded standalone JAR loaded dynamically by the host service.
 * No Spring annotations — instantiated via {@code getDeclaredConstructor().newInstance()}.
 *
 * <h3>Job parameters</h3>
 * <ul>
 *   <li>{@code DATE} — required; identifying parameter for the job instance (yyyy-MM-dd)</li>
 *   <li>{@code ATTEMPT_NUMBER} — required identifying parameter supplied by the host;
 *       incrementing this creates a NEW {@code JobInstance} (a fresh run, NOT a restart —
 *       see README for the restart pitfall)</li>
 *   <li>{@code DESCRIPTION} — optional, non-identifying, informational</li>
 * </ul>
 *
 * <h3>Fault-tolerance characteristics</h3>
 * <ul>
 *   <li>Poison rows (poison_flag=TRUE) are skipped and dead-lettered with failure_type='SKIP'</li>
 *   <li>Transient rows (transient_fail_until_attempt &gt; 0) are retried up to retryLimit times
 *       with exponential backoff; exhaustion dead-letters with failure_type='RETRY_EXHAUSTED'</li>
 *   <li>Abort rows (abort_flag=TRUE) throw a non-skippable exception, failing the job for
 *       the restart demo</li>
 *   <li>{@code skipLimit=5}, {@code retryLimit=3}, {@code chunkSize=5}</li>
 * </ul>
 *
 * <p><strong>PR#1 note:</strong> {@link #configureJob} is a stub in this slice.
 * Full wiring (reader, processor, writer, skip/retry listeners) is implemented in PR#2.
 */
public class FaultTolerantHarvesterJobPlugin implements BatchJobPlugin {

    private static final Logger log = LoggerFactory.getLogger(FaultTolerantHarvesterJobPlugin.class);

    private static final String JOB_NAME = "fault-tolerant-harvester-job";
    private static final String VERSION = "1.0.0";

    @Override
    public String getJobName() {
        return JOB_NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    /**
     * Configures the fault-tolerant harvester Spring Batch job.
     *
     * <p><strong>PR#1 STUB:</strong> This method is not yet implemented. Full job wiring
     * (reader, processor, writer, skip listener, retry listener, step builder) is deferred
     * to PR#2. At dynamic-load time this stub will cause the load to fail gracefully — the
     * host marks the definition FAILED and logs the error; it does NOT crash the host context.
     *
     * @param jobRepository       the shared job repository provided by the host
     * @param transactionManager  the transaction manager provided by the host
     * @param parentContext       the host's application context
     * @return never returns; always throws in PR#1
     * @throws UnsupportedOperationException always, until PR#2 is merged
     */
    @Override
    public Job configureJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext parentContext) {

        // STUB — implemented in PR#2.
        // Throws here so this PR#1 JAR is never accidentally registered as a runnable job
        // without the full fault-tolerance wiring (reader, processor, writer, listeners).
        // The host DynamicJobLoaderService catches RuntimeExceptions from configureJob and
        // marks the definition FAILED, without crashing the host application context.
        throw new UnsupportedOperationException(
                "FaultTolerantHarvesterJobPlugin.configureJob() is not implemented yet "
                        + "(PR#1 scaffold only — full wiring in PR#2)");
    }

    /**
     * Returns the default job parameters with placeholder values.
     *
     * <p>{@code DATE} is the required identifying parameter.
     * {@code ATTEMPT_NUMBER} is the second identifying parameter supplied by the host to control
     * job instance identity (see README for the restart pitfall).
     *
     * @return default parameter map
     */
    @Override
    public Map<String, String> getDefaultParameters() {
        return Map.of(
                "DATE", "2026-01-01",
                "ATTEMPT_NUMBER", "1");
    }

    /**
     * Returns required runtime dependencies.
     *
     * <p>spring-retry is provided transitively via spring-batch-core — no extra runtime deps.
     *
     * @return empty list
     */
    @Override
    public List<String> getRequiredDependencies() {
        return List.of();
    }

    /**
     * Returns the plugin metadata.
     *
     * @return a {@link HarvesterJobMetadata} instance
     */
    @Override
    public JobMetadata getMetadata() {
        return new HarvesterJobMetadata();
    }

    /** Named static inner class so the compiled {@code .class} file is discoverable. */
    public static class HarvesterJobMetadata implements JobMetadata {

        @Override
        public String getDisplayName() {
            return "Fault-Tolerant Harvester";
        }

        @Override
        public String getDescription() {
            return "Demonstrates a fault-tolerant Spring Batch chunk step: "
                    + "skip-to-dead-letter, retry with exponential backoff, "
                    + "dead-letter transaction independence (REQUIRES_NEW), and restartability.";
        }

        @Override
        public String getAuthor() {
            return "fronzec";
        }

        @Override
        public List<String> getTags() {
            return List.of("fault-tolerance", "skip", "retry", "restart");
        }

        @Override
        public Duration getEstimatedRuntime() {
            return Duration.ofSeconds(30);
        }
    }
}
