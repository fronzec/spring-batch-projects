package com.fronzec.plugins.partitionedharvester;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.plugins.partitionedharvester.batch.BillingCharge;
import com.fronzec.plugins.partitionedharvester.batch.BillingChargeWriter;
import com.fronzec.plugins.partitionedharvester.batch.IdRangePartitioner;
import com.fronzec.plugins.partitionedharvester.batch.PartitionedHarvesterJobParametersValidator;
import com.fronzec.plugins.partitionedharvester.batch.PartitionedHarvesterReader;
import com.fronzec.plugins.partitionedharvester.batch.RatingProcessor;
import com.fronzec.plugins.partitionedharvester.batch.UsageRecord;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch plugin that demonstrates id-range partitioned billing with
 * partitioned restart and no double-billing.
 *
 * <p>Implemented as a shaded standalone JAR loaded dynamically by the host service.
 * No Spring annotations — instantiated via {@code getDeclaredConstructor().newInstance()}.
 *
 * <h3>Job parameters</h3>
 * <table border="1">
 *   <tr><th>Parameter</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>{@code GRID_SIZE}</td><td>4</td><td>Number of partitions (parallelism)</td></tr>
 *   <tr><td>{@code CHUNK_SIZE}</td><td>100</td><td>Items per commit</td></tr>
 * </table>
 *
 * <h3>gridSize build-time constraint</h3>
 * <p>{@code configureJob} receives no {@code JobParameters}, so the number of partitions
 * and the thread-pool size are baked in at build time using {@code GRID_SIZE_DEFAULT = 4}.
 * Because a launch-time {@code GRID_SIZE} or {@code CHUNK_SIZE} override cannot take effect,
 * {@link PartitionedHarvesterJobParametersValidator} REJECTS any value that differs from the
 * build-time constant rather than silently ignoring it — the constraint is made explicit
 * instead of becoming a footgun.
 * Full runtime-dynamic gridSize resolution is deferred to a future enhancement requiring
 * a {@code JobExecutionListener.beforeJob} → holder → partitioner wiring.
 *
 * <h3>ThreadLocal reader (no {@code @StepScope})</h3>
 * <p>{@link PartitionedHarvesterReader} implements BOTH {@code ItemStreamReader} AND
 * {@code StepExecutionListener}. It is registered as both {@code .reader(reader)} AND
 * {@code .listener((Object) reader)} on the worker step so that {@code beforeStep} fires
 * before {@code open}, populating the per-thread {@code JdbcCursorItemReader} delegate.
 *
 * <h3>Idempotency</h3>
 * <p>The {@code billing_charge} table has a UNIQUE constraint on {@code source_id}.
 * {@link BillingChargeWriter} uses a guarded {@code INSERT … WHERE NOT EXISTS} so
 * re-running a partition is always a safe no-op for already-written charges.
 */
public class PartitionedHarvesterJobPlugin implements BatchJobPlugin {

    private static final Logger log =
            LoggerFactory.getLogger(PartitionedHarvesterJobPlugin.class);

    /** Job name registered in the Spring Batch job repository. */
    static final String JOB_NAME = "partitioned-harvester-job";

    /** Plugin version. */
    static final String VERSION = "1.0.0";

    /**
     * Build-time default number of partitions.
     *
     * <p>Both the {@link IdRangePartitioner} constructor and the
     * {@link org.springframework.batch.core.step.builder.PartitionStepBuilder#gridSize(int)}
     * call use this constant so they are always in sync — a required invariant because
     * Spring Batch uses the gridSize argument to pre-size internal structures and the
     * partitioner produces exactly this many ExecutionContexts.
     */
    static final int GRID_SIZE_DEFAULT = 4;

    /** Default number of items per chunk commit. */
    static final int CHUNK_SIZE_DEFAULT = 100;

    /** Thread-pool queue capacity: 0 means synchronous hand-off to worker threads. */
    private static final int POOL_QUEUE_CAPACITY = 0;

    @Override
    public String getJobName() {
        return JOB_NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    /**
     * Configures the full partitioned harvester job.
     *
     * <h3>Step wiring</h3>
     * <p>Manager step ({@code usageManagerStep}):
     * <ul>
     *   <li>Partitioner: {@link IdRangePartitioner} — splits {@code usage_record} by id range.</li>
     *   <li>Worker step: {@code usageWorkerStep} — one chunk step reused across N threads.</li>
     *   <li>Executor: {@link ThreadPoolTaskExecutor} with {@code core = max = GRID_SIZE_DEFAULT}.</li>
     *   <li>Grid size: {@code GRID_SIZE_DEFAULT} — fixed at build time (see class Javadoc).</li>
     * </ul>
     * <p>Worker step ({@code usageWorkerStep}):
     * <ul>
     *   <li>Reader: {@link PartitionedHarvesterReader} — ThreadLocal JdbcCursorItemReader,
     *       also registered as {@code StepExecutionListener} to populate the ThreadLocal in
     *       {@code beforeStep}.</li>
     *   <li>Processor: {@link RatingProcessor} — stateless flat-rate cost computation.</li>
     *   <li>Writer: {@link BillingChargeWriter} — idempotent guarded INSERT.</li>
     * </ul>
     *
     * @param jobRepository      the shared job repository provided by the host
     * @param transactionManager the transaction manager provided by the host
     * @param parentContext      the host application context (used to obtain DataSource)
     * @return the configured {@link Job}
     */
    @Override
    public Job configureJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext parentContext) {

        log.info("Configuring partitioned-harvester-job plugin v{}", VERSION);

        DataSource dataSource = parentContext.getBean(DataSource.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // ── Partitioner ───────────────────────────────────────────────────────
        // gridSize is fixed at GRID_SIZE_DEFAULT (build-time constraint; see class Javadoc).
        // The partitioner produces exactly GRID_SIZE_DEFAULT ExecutionContexts.
        IdRangePartitioner partitioner = new IdRangePartitioner(jdbc, GRID_SIZE_DEFAULT);

        // ── Batch components ──────────────────────────────────────────────────
        // Reader implements both ItemStreamReader AND StepExecutionListener;
        // registered as .reader(...) AND .listener((Object) reader) below.
        PartitionedHarvesterReader reader = new PartitionedHarvesterReader(dataSource);
        RatingProcessor processor = new RatingProcessor();
        BillingChargeWriter writer = new BillingChargeWriter(jdbc);

        // ── Thread pool ───────────────────────────────────────────────────────
        // corePoolSize = maxPoolSize = GRID_SIZE_DEFAULT: one thread per partition,
        // no growth and no queueing (queueCapacity=0 → synchronous hand-off).
        // afterPropertiesSet() is mandatory — ThreadPoolTaskExecutor is an InitializingBean.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(GRID_SIZE_DEFAULT);
        executor.setMaxPoolSize(GRID_SIZE_DEFAULT);
        executor.setQueueCapacity(POOL_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("usage-worker-");
        executor.afterPropertiesSet();

        // ── Worker step ───────────────────────────────────────────────────────
        // .listener((Object) reader) registers the reader as a StepExecutionListener.
        // beforeStep() populates the ThreadLocal delegate; afterStep() removes it.
        // The cast to Object forces the generic .listener(Object) overload on
        // SimpleStepBuilder/AbstractTaskletStepBuilder which performs annotation
        // introspection and StepExecutionListener detection.
        Step workerStep = new StepBuilder("usageWorkerStep", jobRepository)
                .<UsageRecord, BillingCharge>chunk(CHUNK_SIZE_DEFAULT, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener((Object) reader)   // reader IS the StepExecutionListener
                .build();

        // ── Manager (partition) step ──────────────────────────────────────────
        // gridSize(GRID_SIZE_DEFAULT) MUST match the IdRangePartitioner constructor arg.
        // Spring Batch uses this value to size the internal StepExecutionSplitter; if the
        // partitioner returns more contexts than gridSize, the extras are silently ignored.
        Step managerStep = new StepBuilder("usageManagerStep", jobRepository)
                .partitioner("usageWorkerStep", partitioner)
                .step(workerStep)
                .gridSize(GRID_SIZE_DEFAULT)
                .taskExecutor(executor)
                .build();

        // ── Job ───────────────────────────────────────────────────────────────
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new PartitionedHarvesterJobParametersValidator(
                        GRID_SIZE_DEFAULT, CHUNK_SIZE_DEFAULT))
                .start(managerStep)
                .build();
    }

    /**
     * Returns the default job parameters.
     *
     * <p>Both parameters are optional at launch; missing ones fall back to these defaults.
     * {@code GRID_SIZE} is also the build-time partition count used in {@link #configureJob}.
     *
     * @return default parameter map
     */
    @Override
    public Map<String, String> getDefaultParameters() {
        return Map.of(
                "GRID_SIZE", String.valueOf(GRID_SIZE_DEFAULT),
                "CHUNK_SIZE", String.valueOf(CHUNK_SIZE_DEFAULT));
    }

    /**
     * Returns required runtime dependencies.
     *
     * <p>All needed libraries are provided transitively by the host (spring-batch-core,
     * spring-jdbc, spring-scheduling).
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
     * @return a {@link PartitionedHarvesterJobMetadata} instance
     */
    @Override
    public JobMetadata getMetadata() {
        return new PartitionedHarvesterJobMetadata();
    }

    /** Named static inner class so the compiled {@code .class} file is discoverable. */
    public static class PartitionedHarvesterJobMetadata implements JobMetadata {

        @Override
        public String getDisplayName() {
            return "Partitioned Harvester";
        }

        @Override
        public String getDescription() {
            return "Demonstrates a partitioned Spring Batch job: id-range partitioning over a "
                    + "frozen usage_record table, flat-rate billing (cost = units × rate), "
                    + "parallel execution via ThreadPoolTaskExecutor, idempotent guarded INSERT "
                    + "on billing_charge, and partitioned restart with no double-billing.";
        }

        @Override
        public String getAuthor() {
            return "fronzec";
        }

        @Override
        public List<String> getTags() {
            return List.of("partitioned", "billing", "cdr");
        }

        @Override
        public Duration getEstimatedRuntime() {
            return Duration.ofSeconds(30);
        }
    }
}
