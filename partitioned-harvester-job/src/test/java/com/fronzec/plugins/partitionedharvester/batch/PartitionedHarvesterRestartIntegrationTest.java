package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.partitionedharvester.PartitionedHarvesterJobPlugin;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
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
 * Integration test proving partitioned restart with NO double-billing for
 * {@link PartitionedHarvesterJobPlugin} (spec REQ-05, SC-05.1, SC-05.2).
 *
 * <h3>Failure-injection mechanism</h3>
 * <p>No production code is changed. The already-built {@link RatingProcessor} calls
 * {@link Math#multiplyExact} to compute {@code cost = units × rate}. By inserting a
 * POISON row with {@code units = Long.MAX_VALUE / 2 + 1} and {@code rate = 3}, the
 * multiplication overflows and {@code Math.multiplyExact} throws {@link ArithmeticException}.
 * Since the chunk step has no skip or retry configuration, the exception propagates
 * immediately and the worker step for that partition FAILS.
 *
 * <h3>Partition layout (MIN=1, MAX=24, gridSize=4, rangeSize=5)</h3>
 * <pre>
 *   partition0: ids [1,5]   — normal rows, complete and commit 5 charges in Run 1
 *   partition1: ids [6,10]  — normal rows, complete and commit 5 charges in Run 1
 *   partition2: ids [11,15] — contains POISON row at id=13, worker FAILS in Run 1
 *   partition3: ids [16,24] — normal rows, complete and commit 9 charges in Run 1
 * </pre>
 *
 * <h3>Restart scenario</h3>
 * <ol>
 *   <li><b>Seed</b>: 24 usage_record rows; id=13 has {@code units=Long.MAX_VALUE/2+1, rate=3}
 *       (overflow poison). All other rows are normal.</li>
 *   <li><b>Run 1</b>: launched with identifying params {@code RUN_DATE="2026-06-16"}.
 *       partition0, partition1, and partition3 complete and commit their charges.
 *       partition2 processes ids 11, 12 then fails on id=13 ({@code ArithmeticException}).
 *       Chunk rollback removes any partial partition2 writes. Job status: FAILED.</li>
 *   <li><b>Data fix</b>: UPDATE id=13 to safe values ({@code units=5, rate=8}).
 *       JobParameters are NOT changed — this is the operator fix, not a new run.</li>
 *   <li><b>Run 2</b>: re-launched with <em>identical</em> identifying params
 *       ({@code RUN_DATE="2026-06-16"}). Spring Batch finds the existing FAILED execution
 *       for the same {@code JobInstance} and restarts. Completed partitions (0, 1, 3) are
 *       SKIPPED. Only partition2 resumes; its {@code JdbcCursorItemReader} restores cursor
 *       offset from {@code BATCH_STEP_EXECUTION_CONTEXT} (saveState=true). Job: COMPLETED.</li>
 * </ol>
 *
 * <h3>Key assertions</h3>
 * <ul>
 *   <li>Run 1 status is FAILED; {@code billing_charge} has the 19 charges from partitions 0, 1, 3
 *       but 0 charges from partition2 (whole chunk rolled back).</li>
 *   <li>Run 2 status is COMPLETED; {@code COUNT(billing_charge) == 24} (no double-billing).</li>
 *   <li>{@code SUM(cost) == 1290} (expected total for the full 24-row seed).</li>
 *   <li>{@code COUNT(DISTINCT source_id) == 24} — explicit no-duplicate proof.</li>
 *   <li>Both executions share exactly ONE {@code JOB_INSTANCE_ID} in {@code BATCH_JOB_EXECUTION}.</li>
 * </ul>
 *
 * <h3>Why DB_CLOSE_DELAY=-1 is critical</h3>
 * <p>The H2 URL uses {@code DB_CLOSE_DELAY=-1}, which keeps the in-memory database alive for
 * the duration of the JVM process. Both launches share the same {@link DataSource},
 * {@link JobRepository}, and {@link StaticApplicationContext}, so Spring Batch metadata
 * (BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION, BATCH_STEP_EXECUTION_CONTEXT)
 * persists between runs. Only the {@link Job} instance is re-created for each launch.
 *
 * <h3>Restart pitfall (identical JobParameters required)</h3>
 * <p>Run 2 uses exactly the same identifying parameters as Run 1. Changing ANY identifying
 * parameter would create a NEW {@code JobInstance} — a fresh run from scratch, not a restart.
 * See the module README for the full explanation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartitionedHarvesterRestartIntegrationTest {

    /**
     * Fixed identifying job parameter shared by both launches.
     * Both runs must use the same value to target the same {@code JobInstance}.
     */
    private static final String RUN_DATE = "2026-06-16";

    /**
     * The poison row id. Placed inside partition2 range [11,15] so that partition2 fails
     * while partitions 0, 1, and 3 complete in Run 1.
     */
    private static final long POISON_ID = 13L;

    /**
     * units value that causes Math.multiplyExact(units, rate) to overflow with rate=3.
     * Long.MAX_VALUE / 2 + 1 = 4611686018427387904; multiplied by 3 overflows long.
     */
    private static final long POISON_UNITS = Long.MAX_VALUE / 2 + 1;
    private static final long POISON_RATE = 3L;

    /**
     * Expected Σcost for all 24 rows after the data fix (id=13 restored to units=5, rate=8).
     * partition0: ids 1-5   → 5×50 = 250
     * partition1: ids 6-10  → 5×60 = 300
     * partition2: ids 11-15 → 5×40 = 200 (id=13: after fix units=5,rate=8 → cost=40)
     * partition3: ids 16-24 → 9×60 = 540
     * Total: 250 + 300 + 200 + 540 = 1290
     */
    private static final long EXPECTED_FULL_SUM = 1290L;

    /** Charges committed by partitions 0, 1, 3 in Run 1 (partition2 rolls back entirely). */
    private static final int PARTIAL_CHARGE_COUNT = 19; // 5 + 5 + 9

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JobRepository jobRepository;
    private PlatformTransactionManager txManager;
    private StaticApplicationContext stubContext;

    @BeforeAll
    void setUpDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the entire JVM lifetime,
        // which is required for Spring Batch metadata to survive BOTH launches in one JVM.
        // DATABASE_TO_LOWER=TRUE normalises H2 column names to lowercase so
        // jdbc.queryForList result map keys are lowercase (matching the query column aliases).
        ds.setURL("jdbc:h2:mem:partitioned-restart-it-"
                + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.txManager = new DataSourceTransactionManager(ds);

        // Bootstrap Spring Batch metadata schema
        try (Connection conn = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
        }

        // Create application tables (mirrors V8 Flyway migration DDL)
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

        // Bootstrap a single shared JobRepository — metadata persists between both launches
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(ds);
        factory.setTransactionManager(txManager);
        factory.afterPropertiesSet();
        this.jobRepository = factory.getObject();

        // Stub ApplicationContext exposing only the DataSource
        stubContext = new StaticApplicationContext();
        stubContext.getBeanFactory().registerSingleton("dataSource", ds);
        stubContext.refresh();

        // Seed 24 usage_record rows. id=13 has overflow-triggering values.
        seedUsageRecords();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /**
     * Inserts 24 usage_record rows. id=13 is the POISON row.
     *
     * <p>Partition ranges (MIN=1, MAX=24, gridSize=4, rangeSize=5):
     * <pre>
     *   partition0: ids 1-5   subscriber=100 units=10 rate=5  cost=50 (normal)
     *   partition1: ids 6-10  subscriber=101 units=20 rate=3  cost=60 (normal)
     *   partition2: ids 11-15 subscriber=102 units=5  rate=8  cost=40 (normal)
     *                id=13:   units=POISON_UNITS rate=3 → overflow in RatingProcessor
     *   partition3: ids 16-24 subscriber=103 units=15 rate=4  cost=60 (normal)
     * </pre>
     */
    private void seedUsageRecords() {
        // partition0: ids 1-5
        for (long id = 1L; id <= 5L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 100L, 10L, 5L);
        }
        // partition1: ids 6-10
        for (long id = 6L; id <= 10L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 101L, 20L, 3L);
        }
        // partition2: ids 11-15 — id=13 is the POISON row (overflow)
        for (long id = 11L; id <= 15L; id++) {
            if (id == POISON_ID) {
                // Poison: units × rate overflows Math.multiplyExact
                jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                        id, 102L, POISON_UNITS, POISON_RATE);
            } else {
                jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                        id, 102L, 5L, 8L);
            }
        }
        // partition3: ids 16-24
        for (long id = 16L; id <= 24L; id++) {
            jdbc.update("INSERT INTO usage_record (id, subscriber_id, units, rate) VALUES (?,?,?,?)",
                    id, 103L, 15L, 4L);
        }
    }

    /**
     * Launches the partitioned-harvester job with the fixed identifying parameters.
     * A new {@link Job} instance is built each time (matching production behaviour), but
     * {@link JobRepository} and {@link DataSource} are shared so Spring Batch finds the
     * existing FAILED execution on the second call and restarts.
     *
     * @return the final {@link BatchStatus} of the execution
     */
    private JobExecution launchJob() throws Exception {
        PartitionedHarvesterJobPlugin plugin = new PartitionedHarvesterJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParameters params = new JobParametersBuilder()
                .addString("RUN_DATE", RUN_DATE)
                .toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        return launcher.run(job, params);
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

    // ── Restart scenario ──────────────────────────────────────────────────────────

    /**
     * Proves partitioned restart with no double-billing.
     *
     * <p>This is the headline capability of {@code partitioned-harvester-job}: a FAILED job
     * execution is restarted with identical parameters, completed partitions are skipped,
     * and the previously-failed partition resumes — producing exactly one {@code billing_charge}
     * per {@code usage_record} with no duplicates.
     */
    @Test
    void restart_poisonPartition2_resumesAndCompletes_noDoubleBilling() throws Exception {

        // ── Run 1: FAIL ───────────────────────────────────────────────────────────
        // partition2 fails on POISON_ID=13 (Math.multiplyExact overflow → ArithmeticException).
        // partitions 0, 1, 3 complete and commit their charges before or concurrently.
        // The chunk in partition2 rolls back entirely (chunkSize=100 > 5 rows in partition2
        // range, so all 5 rows are in one chunk and none commit before the failure).
        JobExecution run1 = launchJob();

        assertThat(run1.getStatus())
                .as("Run 1 must FAIL because partition2 hits ArithmeticException on id=13")
                .isEqualTo(BatchStatus.FAILED);

        // Partitions 0, 1, 3 committed: 5 + 5 + 9 = 19 charges
        // Partition 2 (ids 11-15) rolled back entirely: 0 charges from it
        assertThat(countBillingCharges())
                .as("After Run 1 FAIL: billing_charge must contain only the 19 committed charges "
                        + "from completed partitions (5+5+9); partition2 chunk rolled back")
                .isEqualTo(PARTIAL_CHARGE_COUNT);

        // No duplicate source_id in partial result
        assertThat(countDistinctSourceIds())
                .as("No duplicate source_id must exist after partial Run 1")
                .isEqualTo(PARTIAL_CHARGE_COUNT);

        // ── Operator fix: repair the poison row ───────────────────────────────────
        // In production: operator corrects the bad source data before requesting a restart.
        // This is a DATA fix — JobParameters do NOT change. The same identifying param
        // (RUN_DATE="2026-06-16") will target the same JobInstance on restart.
        jdbc.update("UPDATE usage_record SET units = 5, rate = 8 WHERE id = ?", POISON_ID);

        // ── Run 2: RESTART with identical parameters ──────────────────────────────
        // Spring Batch detects the existing FAILED execution for RUN_DATE="2026-06-16",
        // finds the same JobInstance, and restarts the job. Completed partitions (0, 1, 3)
        // are skipped. Only partition2 replays — its JdbcCursorItemReader restores the cursor
        // offset from BATCH_STEP_EXECUTION_CONTEXT (saveState=true).
        JobExecution run2 = launchJob();

        assertThat(run2.getStatus())
                .as("Run 2 must COMPLETE after the poison row is repaired")
                .isEqualTo(BatchStatus.COMPLETED);

        // ── No double-billing: exact count and sum ────────────────────────────────
        assertThat(countBillingCharges())
                .as("billing_charge must contain exactly one row per usage_record (24 total)")
                .isEqualTo(24);

        assertThat(sumCost())
                .as("SUM(cost) must equal the full expected total (1290) after restart")
                .isEqualTo(EXPECTED_FULL_SUM);

        assertThat(countDistinctSourceIds())
                .as("COUNT(DISTINCT source_id)==24 proves there is no double-billing: "
                        + "partitions 0,1,3 are not re-processed, partition2 fills the gap")
                .isEqualTo(24);

        // ── Spring Batch metadata: 2 executions, 1 shared JobInstance ────────────
        List<Map<String, Object>> executions = jdbc.queryForList(
                "SELECT JOB_INSTANCE_ID, STATUS FROM BATCH_JOB_EXECUTION"
                        + " ORDER BY JOB_EXECUTION_ID");

        assertThat(executions)
                .as("Exactly 2 JobExecutions must exist (Run 1 FAILED + Run 2 COMPLETED)")
                .hasSize(2);

        Long instanceId1 = (Long) executions.get(0).get("JOB_INSTANCE_ID");
        Long instanceId2 = (Long) executions.get(1).get("JOB_INSTANCE_ID");
        assertThat(instanceId1)
                .as("Both executions must share the same JobInstance (identical identifying params)")
                .isEqualTo(instanceId2);

        assertThat(executions.get(0).get("STATUS"))
                .as("Run 1 must be recorded as FAILED in BATCH_JOB_EXECUTION")
                .isEqualTo("FAILED");
        assertThat(executions.get(1).get("STATUS"))
                .as("Run 2 must be recorded as COMPLETED in BATCH_JOB_EXECUTION")
                .isEqualTo("COMPLETED");

        // ── Completed partitions were skipped, not re-run ─────────────────────────
        // In Run 2, only partition2 runs. Its step executions exist; partitions 0, 1, 3
        // should NOT have new COMPLETED step executions under Run 2's JOB_EXECUTION_ID.
        long run2ExecutionId = run2.getId();
        List<Map<String, Object>> run2WorkerSteps = jdbc.queryForList(
                "SELECT STEP_NAME, STATUS FROM BATCH_STEP_EXECUTION"
                        + " WHERE JOB_EXECUTION_ID = ?"
                        + " AND STEP_NAME LIKE 'usageWorkerStep:%'",
                run2ExecutionId);

        // Run 2 should execute only partition2 (the only non-COMPLETED partition)
        assertThat(run2WorkerSteps)
                .as("Run 2 must execute only the failed partition2 worker step")
                .hasSize(1);
        assertThat(run2WorkerSteps.get(0).get("STEP_NAME"))
                .as("The resumed step must be partition2")
                .isEqualTo("usageWorkerStep:partition2");
        assertThat(run2WorkerSteps.get(0).get("STATUS"))
                .as("partition2 must complete successfully in Run 2")
                .isEqualTo("COMPLETED");
    }
}
