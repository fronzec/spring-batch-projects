package com.fronzec.plugins.harvester;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.plugins.harvester.batch.AbortJobException;
import com.fronzec.plugins.harvester.batch.HarvestItemProcessor;
import com.fronzec.plugins.harvester.batch.HarvestItemWriter;
import com.fronzec.plugins.harvester.batch.HarvestJobParametersValidator;
import com.fronzec.plugins.harvester.batch.HarvestParamsHolder;
import com.fronzec.plugins.harvester.batch.HarvestRetryListener;
import com.fronzec.plugins.harvester.batch.HarvestRow;
import com.fronzec.plugins.harvester.batch.HarvestRowMapper;
import com.fronzec.plugins.harvester.batch.HarvestSkipListener;
import com.fronzec.plugins.harvester.batch.HarvestStepListener;
import com.fronzec.plugins.harvester.batch.PoisonItemException;
import com.fronzec.plugins.harvester.batch.TransientProcessingException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
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
 */
public class FaultTolerantHarvesterJobPlugin implements BatchJobPlugin {

    private static final Logger log = LoggerFactory.getLogger(FaultTolerantHarvesterJobPlugin.class);

    private static final String JOB_NAME = "fault-tolerant-harvester-job";
    private static final String VERSION = "1.0.0";

    /** Number of items per chunk commit. */
    static final int CHUNK_SIZE = 5;

    /** Maximum number of skipped items before the step fails. */
    static final int SKIP_LIMIT = 5;

    /** Maximum retry attempts per item before it is treated as a skip. */
    static final int RETRY_LIMIT = 3;

    /**
     * Reader SQL: selects unprocessed rows in id order.
     * The {@code WHERE processed = FALSE} filter is belt-and-suspenders idempotency;
     * restart resumption is driven by the saved {@code currentItemCount} in
     * {@code BATCH_STEP_EXECUTION_CONTEXT} (enabled by {@code setSaveState(true)}).
     */
    private static final String READER_SQL =
            "SELECT id, payload, poison_flag, transient_fail_until_attempt, abort_flag"
                    + " FROM harvest_source"
                    + " WHERE processed = FALSE"
                    + " ORDER BY id";

    @Override
    public String getJobName() {
        return JOB_NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    /**
     * Configures the full fault-tolerant harvester job.
     *
     * <h3>Step wiring</h3>
     * <p>Single fault-tolerant chunk step {@code harvestStep}:
     * <ul>
     *   <li>Reader: {@link JdbcCursorItemReader} with {@code setSaveState(true)} for restart.</li>
     *   <li>Processor: {@link com.fronzec.plugins.harvester.batch.HarvestItemProcessor}
     *       — abort / poison / transient R4 threshold logic.</li>
     *   <li>Writer: {@link com.fronzec.plugins.harvester.batch.HarvestItemWriter}
     *       — idempotent {@code UPDATE} with {@code AND processed = FALSE}.</li>
     *   <li>Skip: {@link PoisonItemException} + {@link TransientProcessingException}
     *       (after retry exhaustion); {@link AbortJobException} is {@code noSkip}.</li>
     *   <li>Retry: {@link TransientProcessingException} up to {@code retryLimit=3}.</li>
     *   <li>BackOff: {@link ExponentialBackOffPolicy} (initial=10ms, multiplier=2.0)
     *       — short intervals so integration tests are fast.</li>
     *   <li>Listeners: {@link HarvestSkipListener} (dead-letter REQUIRES_NEW) +
     *       {@link HarvestRetryListener} (pedagogical logging).</li>
     * </ul>
     *
     * @param jobRepository      the shared job repository provided by the host
     * @param transactionManager the transaction manager provided by the host
     * @param parentContext      the host's application context (used to obtain DataSource)
     * @return the configured {@link Job}
     */
    @Override
    public Job configureJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext parentContext) {

        log.info("Configuring fault-tolerant-harvester-job plugin v{}", VERSION);

        DataSource dataSource = parentContext.getBean(DataSource.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // ── Shared objects ────────────────────────────────────────────────────────────────────
        HarvestParamsHolder holder = new HarvestParamsHolder();
        HarvestItemProcessor processor = new HarvestItemProcessor();
        HarvestItemWriter writer = new HarvestItemWriter(jdbc);

        // ── Reader ────────────────────────────────────────────────────────────────────────────
        JdbcCursorItemReader<HarvestRow> reader =
                new JdbcCursorItemReader<>(dataSource, READER_SQL, new HarvestRowMapper());
        reader.setName("harvestSourceReader");
        // setSaveState(true) persists currentItemCount to BATCH_STEP_EXECUTION_CONTEXT on each
        // chunk commit so the reader can fast-forward past already-committed rows on restart.
        reader.setSaveState(true);

        // ── Skip listener (dead-letter, REQUIRES_NEW) ─────────────────────────────────────────
        HarvestSkipListener skipListener = new HarvestSkipListener(jdbc, transactionManager);

        // ── Retry listener (pedagogical logging) ──────────────────────────────────────────────
        HarvestRetryListener retryListener = new HarvestRetryListener();

        // ── Step listeners ────────────────────────────────────────────────────────────────────
        // Primary: populates HarvestParamsHolder and resets processor counters.
        HarvestStepListener stepListener = new HarvestStepListener(holder, processor);

        // Secondary: captures jobExecutionId from the step execution and passes it to the
        // skip listener so dead-letter rows record the correct job_execution_id.
        StepExecutionListener jobIdCapture = new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                long jobExecutionId = stepExecution.getJobExecution().getId();
                skipListener.setJobExecutionId(jobExecutionId);
                log.debug("Captured jobExecutionId={} for skip listener", jobExecutionId);
            }
        };

        // ── Backoff policy ────────────────────────────────────────────────────────────────────
        // Short initial interval (10ms) so integration tests run quickly while still
        // demonstrating the exponential pattern.
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(10L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(100L);

        // ── Fault-tolerant chunk step ─────────────────────────────────────────────────────────
        // StepExecutionListeners (stepListener, jobIdCapture) are registered via the generic
        // listener(Object) overload on SimpleStepBuilder/AbstractTaskletStepBuilder BEFORE
        // .faultTolerant() is called.  SkipListener and RetryListener are registered on the
        // FaultTolerantStepBuilder returned by .faultTolerant() using their typed overloads.
        var stepWithListeners = new StepBuilder("harvestStep", jobRepository)
                .<HarvestRow, HarvestRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener((Object) stepListener)
                .listener((Object) jobIdCapture)
                .faultTolerant()
                    .skipLimit(SKIP_LIMIT)
                    .skip(PoisonItemException.class)
                    .skip(TransientProcessingException.class)
                    .noSkip(AbortJobException.class)
                    .retryLimit(RETRY_LIMIT)
                    .retry(TransientProcessingException.class)
                    .noRetry(PoisonItemException.class)
                    .backOffPolicy(backOffPolicy)
                    .listener(skipListener)
                    .listener(retryListener)
                .build();

        // ── Job ───────────────────────────────────────────────────────────────────────────────
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new HarvestJobParametersValidator())
                .start(stepWithListeners)
                .build();
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
