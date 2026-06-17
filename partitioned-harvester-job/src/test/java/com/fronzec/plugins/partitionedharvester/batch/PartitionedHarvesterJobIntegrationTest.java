package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * End-to-end integration tests for {@link PartitionedHarvesterJobPlugin}.
 *
 * <p>Uses an isolated in-module H2 database with {@code MODE=MySQL;DB_CLOSE_DELAY=-1}
 * (multi-thread safe; DB survives for the full JVM lifetime). Spring Batch metadata
 * and application tables are bootstrapped once per class; {@code billing_charge} is
 * truncated before each test.
 *
 * <h3>Seed dataset (24 rows, gridSize=4)</h3>
 * <p>Contiguous ids 1–24; partition ranges with MIN=1, MAX=24, gridSize=4:
 * <ul>
 *   <li>rangeSize = (24-1)/4 = 5 (integer division)</li>
 *   <li>partition0: ids [1,5]   — 5 rows, subscriber 100, units=10, rate=5, cost=50 each → Σ=250</li>
 *   <li>partition1: ids [6,10]  — 5 rows, subscriber 101, units=20, rate=3, cost=60 each → Σ=300</li>
 *   <li>partition2: ids [11,15] — 5 rows, subscriber 102, units=5,  rate=8, cost=40 each → Σ=200</li>
 *   <li>partition3: ids [16,24] — 9 rows, subscriber 103, units=15, rate=4, cost=60 each → Σ=540</li>
 * </ul>
 * <p>Expected total: Σcost = 250 + 300 + 200 + 540 = 1290.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>Correctness and sum: COUNT=24, SUM(cost)=1440 after a full run.</li>
 *   <li>Parallelism: exactly 4 worker BATCH_STEP_EXECUTION rows with status COMPLETED.</li>
 *   <li>1:1 idempotency: no duplicate source_id; re-run does not change count or sum.</li>
 *   <li>Bounded memory: 1000-row run with CHUNK_SIZE=100 (default) completes correctly;
 *       passing is a proxy for cursor-based bounded-memory behaviour.</li>
 * </ol>
 *
 * @see PartitionedHarvesterRestartIntegrationTest for the partitioned restart proof
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartitionedHarvesterJobIntegrationTest {

    /**
     * Expected Σ(units × rate) for the 24-row seed dataset.
     * partition0: ids 1-5  → 5×(10×5)  = 5×50  = 250
     * partition1: ids 6-10 → 5×(20×3)  = 5×60  = 300
     * partition2: ids 11-15→ 5×(5×8)   = 5×40  = 200
     * partition3: ids 16-24→ 9×(15×4)  = 9×60  = 540
     * Total: 250 + 300 + 200 + 540 = 1290
     */
    private static final long EXPECTED_SUM_COST = 1290L;

    /** Total usage_record rows in the fixed seed. */
    private static final int SEED_ROW_COUNT = 24;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JobRepository jobRepository;
    private PlatformTransactionManager txManager;
    private StaticApplicationContext stubContext;

    @BeforeAll
    void setUpDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the entire test class run.
        // MODE=MySQL makes H2 accept MySQL-compatible DDL and the guarded INSERT ... WHERE NOT EXISTS.
        ds.setURL("jdbc:h2:mem:partitioned-it-"
                + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.txManager = new DataSourceTransactionManager(ds);

        // Bootstrap Spring Batch metadata schema (BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, ...)
        try (Connection conn = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
        }

        // Create application tables inline (mirrors the DDL from V8 Flyway migration)
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS usage_record ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " subscriber_id BIGINT NOT NULL,"
                        + " units BIGINT NOT NULL,"
                        + " rate BIGINT NOT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS billing_charge ("
                        + " id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + " source_id BIGINT NOT NULL,"
                        + " subscriber_id BIGINT NOT NULL,"
                        + " units BIGINT NOT NULL,"
                        + " rate BIGINT NOT NULL,"
                        + " cost BIGINT NOT NULL,"
                        + " job_execution_id BIGINT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " CONSTRAINT uk_billing_charge_source UNIQUE (source_id)"
                        + ")");

        // Bootstrap Spring Batch JobRepository
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(ds);
        factory.setTransactionManager(txManager);
        factory.afterPropertiesSet();
        this.jobRepository = factory.getObject();

        // Stub ApplicationContext exposing only the DataSource (mirrors existing IT pattern)
        stubContext = new StaticApplicationContext();
        stubContext.getBeanFactory().registerSingleton("dataSource", ds);
        stubContext.refresh();

        // Insert the fixed 24-row seed (frozen source; only truncated implicitly if needed)
        insertSeedData();
    }

    /**
     * Reset billing_charge before each test. usage_record is the frozen source and is
     * not modified between tests. Seed is inserted once in @BeforeAll.
     */
    @BeforeEach
    void cleanBillingCharge() {
        jdbc.execute("DELETE FROM billing_charge");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Inserts the deterministic 24-row seed dataset.
     *
     * <p>Partition membership (MIN=1, MAX=24, gridSize=4, rangeSize=5):
     * <pre>
     *   partition0: ids  1-5  subscriber=100 units=10 rate=5  cost=50  Σ=300
     *   partition1: ids  6-10 subscriber=101 units=20 rate=3  cost=60  Σ=360
     *   partition2: ids 11-15 subscriber=102 units=5  rate=8  cost=40  Σ=240
     *   partition3: ids 16-24 subscriber=103 units=15 rate=4  cost=60  Σ=540
     *   Total Σcost = 1440
     * </pre>
     */
    private void insertSeedData() {
        // partition0: ids 1-5, subscriber=100, units=10, rate=5 → cost=50
        for (long id = 1L; id <= 5L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 100L, 10L, 5L);
        }
        // partition1: ids 6-10, subscriber=101, units=20, rate=3 → cost=60
        for (long id = 6L; id <= 10L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 101L, 20L, 3L);
        }
        // partition2: ids 11-15, subscriber=102, units=5, rate=8 → cost=40
        for (long id = 11L; id <= 15L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 102L, 5L, 8L);
        }
        // partition3: ids 16-24, subscriber=103, units=15, rate=4 → cost=60
        for (long id = 16L; id <= 24L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 103L, 15L, 4L);
        }
    }

    private int countBillingCharges() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM billing_charge", Integer.class);
        return count != null ? count : 0;
    }

    private long sumCost() {
        Long sum = jdbc.queryForObject("SELECT SUM(cost) FROM billing_charge", Long.class);
        return sum != null ? sum : 0L;
    }

    private int countDistinctSourceIds() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT source_id) FROM billing_charge", Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Launches the partitioned-harvester job with a unique run-discriminator so each test
     * creates a fresh JobInstance. The identifying parameter {@code RUN_ID} differentiates
     * job instances; GRID_SIZE and CHUNK_SIZE are omitted (defaults apply and pass validation).
     *
     * @param runId unique discriminator for this job instance
     * @return the final BatchStatus of the execution
     */
    private BatchStatus runJob(String runId) throws Exception {
        PartitionedHarvesterJobPlugin plugin = new PartitionedHarvesterJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParameters params = new JobParametersBuilder()
                .addString("RUN_DATE", "2026-06-16")
                .addString("RUN_ID", runId)
                .toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        JobExecution execution = launcher.run(job, params);
        return execution.getStatus();
    }

    // ── Scenario 1: Correctness and sum ──────────────────────────────────────────

    /**
     * Full run correctness: COUNT(billing_charge)==24 and SUM(cost)==1440 after COMPLETED job.
     * Verifies REQ-03 (SC-03.2 sum) and REQ-04 (SC-04.1 count).
     */
    @Test
    void correctnessAndSum_fullRun_countAndSumMatch() throws Exception {
        BatchStatus status = runJob("correctness-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(countBillingCharges())
                .as("billing_charge must have exactly one row per usage_record")
                .isEqualTo(SEED_ROW_COUNT);
        assertThat(sumCost())
                .as("SUM(cost) must equal the pre-computed Σ(units×rate)")
                .isEqualTo(EXPECTED_SUM_COST);
    }

    /**
     * Per-record cost correctness: spot-check that each charge cost equals units × rate.
     * Verifies REQ-03 (SC-03.1).
     */
    @Test
    void correctness_perRecordCost_matchesUnitsTimesRate() throws Exception {
        BatchStatus status = runJob("per-record-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // Spot-check one charge per partition
        // id=3:  units=10, rate=5  → cost=50
        Long cost3 = jdbc.queryForObject(
                "SELECT cost FROM billing_charge WHERE source_id = 3", Long.class);
        assertThat(cost3).isEqualTo(50L);

        // id=8:  units=20, rate=3  → cost=60
        Long cost8 = jdbc.queryForObject(
                "SELECT cost FROM billing_charge WHERE source_id = 8", Long.class);
        assertThat(cost8).isEqualTo(60L);

        // id=12: units=5,  rate=8  → cost=40
        Long cost12 = jdbc.queryForObject(
                "SELECT cost FROM billing_charge WHERE source_id = 12", Long.class);
        assertThat(cost12).isEqualTo(40L);

        // id=20: units=15, rate=4  → cost=60
        Long cost20 = jdbc.queryForObject(
                "SELECT cost FROM billing_charge WHERE source_id = 20", Long.class);
        assertThat(cost20).isEqualTo(60L);
    }

    // ── Scenario 2: Parallelism ───────────────────────────────────────────────────

    /**
     * Parallelism proof: exactly 4 worker step executions with COMPLETED status exist in
     * BATCH_STEP_EXECUTION for this specific job execution. Verifies REQ-02 (SC-02.1).
     */
    @Test
    void parallelism_workerStepCount_exactlyGridSize() throws Exception {
        PartitionedHarvesterJobPlugin plugin = new PartitionedHarvesterJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParameters params = new JobParametersBuilder()
                .addString("RUN_DATE", "2026-06-16")
                .addString("RUN_ID", "parallel-" + System.nanoTime())
                .toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        JobExecution execution = launcher.run(job, params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        long jobExecutionId = execution.getId();

        // Query worker step executions for THIS job execution only
        List<Map<String, Object>> stepExecs = jdbc.queryForList(
                "SELECT STEP_NAME, STATUS FROM BATCH_STEP_EXECUTION"
                        + " WHERE JOB_EXECUTION_ID = ?"
                        + " AND STEP_NAME LIKE 'usageWorkerStep:%'"
                        + " AND STATUS = 'COMPLETED'",
                jobExecutionId);

        assertThat(stepExecs)
                .as("Exactly gridSize=4 worker step executions must be COMPLETED for this execution")
                .hasSize(4);

        // Confirm each expected partition name is present
        List<String> stepNames = stepExecs.stream()
                .map(r -> (String) r.get("STEP_NAME"))
                .toList();
        assertThat(stepNames).containsExactlyInAnyOrder(
                "usageWorkerStep:partition0",
                "usageWorkerStep:partition1",
                "usageWorkerStep:partition2",
                "usageWorkerStep:partition3");
    }

    // ── Scenario 3: 1:1 idempotency ──────────────────────────────────────────────

    /**
     * No duplicate source_id in billing_charge: COUNT == COUNT(DISTINCT source_id).
     * Verifies REQ-04 (SC-04.1).
     */
    @Test
    void idempotency_noDuplicateSourceId_afterFullRun() throws Exception {
        BatchStatus status = runJob("no-dup-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(countBillingCharges()).isEqualTo(SEED_ROW_COUNT);
        assertThat(countDistinctSourceIds())
                .as("COUNT(DISTINCT source_id) must equal COUNT(*) — no duplicates")
                .isEqualTo(SEED_ROW_COUNT);
    }

    /**
     * Re-run idempotency: a second job launch with NEW identifying params (fresh JobInstance)
     * does not duplicate billing_charge rows (guarded INSERT is a no-op for existing charges).
     * Verifies REQ-04 (SC-04.2).
     */
    @Test
    void idempotency_freshReRunAfterCompletion_countAndSumUnchanged() throws Exception {
        // First run: writes all 24 charges
        BatchStatus run1Status = runJob("idem-run1-" + System.nanoTime());
        assertThat(run1Status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(countBillingCharges()).isEqualTo(SEED_ROW_COUNT);
        assertThat(sumCost()).isEqualTo(EXPECTED_SUM_COST);

        // Second run: fresh JobInstance (different RUN_ID) — guarded INSERT skips all 24
        BatchStatus run2Status = runJob("idem-run2-" + System.nanoTime());
        assertThat(run2Status).isEqualTo(BatchStatus.COMPLETED);

        // Count and sum must be unchanged: second run is a no-op for all rows
        assertThat(countBillingCharges())
                .as("Second run with same data must not create duplicate billing_charge rows")
                .isEqualTo(SEED_ROW_COUNT);
        assertThat(sumCost())
                .as("SUM(cost) must be unchanged after idempotent re-run")
                .isEqualTo(EXPECTED_SUM_COST);
    }

    // ── Scenario 4: Bounded memory ────────────────────────────────────────────────

    /**
     * Bounded-memory proxy: inserts 1000 additional rows and runs with the default CHUNK_SIZE=100.
     *
     * <p>The job must COMPLETE and process all rows. If the cursor-based reader were
     * materialising the whole partition in memory, it would either OOM or (in this test)
     * simply have an outsized heap footprint. The assertion is correctness-under-load —
     * passing is the bounded-memory signal. Verifies REQ-06 (SC-06.1).
     *
     * <p>The extra 1000 rows use ids 25–1024 and are cleaned up after the test via a
     * compensating DELETE so they do not leak into other tests.
     */
    @Test
    void boundedMemory_largeDataset_jobCompletesCorrectly() throws Exception {
        // Insert 1000 additional rows with ids 25-1024
        // units=10, rate=2 → cost=20 each → extra total = 1000 × 20 = 20000
        long extraStart = 25L;
        long extraEnd = 1024L;
        long extraCount = extraEnd - extraStart + 1; // 1000
        long extraCost = extraCount * 20L;           // 20000
        for (long id = extraStart; id <= extraEnd; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 200L, 10L, 2L);
        }

        try {
            BatchStatus status = runJob("bounded-mem-" + System.nanoTime());
            assertThat(status).isEqualTo(BatchStatus.COMPLETED);
            // 24 seed rows + 1000 extra rows
            assertThat(countBillingCharges())
                    .as("All 1024 usage_record rows must produce exactly one billing_charge")
                    .isEqualTo((int) (SEED_ROW_COUNT + extraCount));
            assertThat(sumCost())
                    .as("SUM(cost) must include both seed rows (1290) and extra rows (20000)")
                    .isEqualTo(EXPECTED_SUM_COST + extraCost);
        } finally {
            // Clean up extra rows so they do not affect other tests
            jdbc.execute("DELETE FROM usage_record WHERE id >= 25 AND id <= 1024");
        }
    }
}
