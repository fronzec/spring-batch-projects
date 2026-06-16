package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin;
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
 * Integration test demonstrating Spring Batch true restart behaviour for
 * {@link FaultTolerantHarvesterJobPlugin} (spec FTH-08).
 *
 * <h3>Restart scenario (FTH-08-A)</h3>
 * <ol>
 *   <li><b>Seed</b>: 10 rows in {@code harvest_source}. Rows 1–5 are normal. Row 6 has
 *       {@code abort_flag=TRUE} — the "poison input" that aborts the job. Rows 7–10 are
 *       normal.</li>
 *   <li><b>Run 1</b>: launched with identifying params {@code DATE="2026-01-01"} and
 *       {@code ATTEMPT_NUMBER="1"}. Chunk 1 (rows 1–5) commits successfully
 *       ({@code processed=TRUE}). Row 6 triggers {@link AbortJobException} — non-skippable,
 *       non-retryable — so the step fails and the job ends with {@code BatchStatus.FAILED}.
 *       Rows 7–10 remain {@code processed=FALSE}.</li>
 *   <li><b>Operator fix</b>: the abort row is "repaired" by clearing its
 *       {@code abort_flag} to {@code FALSE}. This simulates the real-world workflow:
 *       fix the bad input, then restart the job.</li>
 *   <li><b>Run 2</b>: re-launched with <em>identical</em> params
 *       ({@code DATE="2026-01-01"}, {@code ATTEMPT_NUMBER="1"}). Spring Batch detects the
 *       existing {@code FAILED} execution for the same {@code JobInstance} and resumes from
 *       the last committed chunk offset — the reader fast-forwards past rows 1–5 using the
 *       saved {@code currentItemCount} in {@code BATCH_STEP_EXECUTION_CONTEXT}. Rows 6–10
 *       are processed; the job ends with {@code BatchStatus.COMPLETED}.</li>
 * </ol>
 *
 * <h3>Key assertions</h3>
 * <ul>
 *   <li>{@code BATCH_JOB_EXECUTION} has exactly 2 rows sharing one {@code JOB_INSTANCE_ID}.</li>
 *   <li>Rows 1–5 are {@code processed=TRUE} after both runs (committed in chunk 1 of run 1).</li>
 *   <li>Rows 1–5 are NOT reprocessed by run 2 (idempotent writer + reader fast-forward).</li>
 *   <li>Rows 6–10 are {@code processed=TRUE} after run 2.</li>
 *   <li>No dead-letter rows exist (row 6's abort was cleared before run 2).</li>
 * </ul>
 *
 * <h3>Why same-JVM restart works here</h3>
 * <p>The H2 URL uses {@code DB_CLOSE_DELAY=-1}, which keeps the in-memory database alive for
 * the duration of the JVM process. Both launches share the same {@link DataSource},
 * {@link JobRepository}, and {@link StaticApplicationContext}, so Spring Batch metadata
 * persists between runs. Only the {@link Job} instance (returned by
 * {@link FaultTolerantHarvesterJobPlugin#configureJob}) is re-created for each launch — this
 * is the same pattern used in production where the same plugin JAR services multiple launches.
 *
 * <h3>Restart pitfall</h3>
 * <p>A true restart requires IDENTICAL identifying parameters ({@code DATE} +
 * {@code ATTEMPT_NUMBER}). Incrementing {@code ATTEMPT_NUMBER} would create a NEW
 * {@code JobInstance}, triggering a fresh run from row 1 — not a restart. See also the
 * module README for the full explanation of this pitfall.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FaultTolerantHarvesterRestartIntegrationTest {

    /**
     * Fixed identifying job parameters. Both launches use the same values so Spring Batch
     * finds the existing FAILED JobExecution on the second launch and restarts rather than
     * creating a new JobInstance.
     */
    private static final String DATE_PARAM = "2026-01-01";
    private static final String ATTEMPT_PARAM = "1";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JobRepository jobRepository;
    private PlatformTransactionManager txManager;
    private StaticApplicationContext stubContext;

    @BeforeAll
    void setUpDatabase() throws Exception {
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive for the entire JVM lifetime,
        // which is required for Spring Batch metadata to survive BOTH launches in one JVM.
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:harvest-restart-it-"
                + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.txManager = new DataSourceTransactionManager(ds);

        // Bootstrap Spring Batch metadata schema (BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, …)
        try (Connection conn = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
        }

        // Create application tables
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS harvest_source ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " payload VARCHAR(2048) NOT NULL,"
                        + " poison_flag BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " transient_fail_until_attempt INT NOT NULL DEFAULT 0,"
                        + " abort_flag BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " processed BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS harvest_dead_letter ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " source_id BIGINT NULL,"
                        + " raw_payload VARCHAR(2048) NULL,"
                        + " failure_phase VARCHAR(16) NOT NULL,"
                        + " failure_type VARCHAR(16) NOT NULL,"
                        + " exception_class VARCHAR(512) NOT NULL,"
                        + " exception_msg VARCHAR(2048) NULL,"
                        + " attempt_count INT NOT NULL DEFAULT 1,"
                        + " job_execution_id BIGINT NOT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        // Bootstrap a single shared JobRepository — metadata persists between both launches
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(ds);
        factory.setTransactionManager(txManager);
        factory.afterPropertiesSet();
        this.jobRepository = factory.getObject();

        // Stub application context exposing only the DataSource (mirrors existing IT pattern)
        stubContext = new StaticApplicationContext();
        stubContext.getBeanFactory().registerSingleton("dataSource", ds);
        stubContext.refresh();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private void insertNormalRow(long id) {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag,"
                        + " transient_fail_until_attempt, abort_flag, processed)"
                        + " VALUES (?, ?, FALSE, 0, FALSE, FALSE)",
                id, "payload-" + id);
    }

    private void insertAbortRow(long id) {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag,"
                        + " transient_fail_until_attempt, abort_flag, processed)"
                        + " VALUES (?, ?, FALSE, 0, TRUE, FALSE)",
                id, "abort-row-" + id);
    }

    private boolean isProcessed(long id) {
        Boolean result = jdbc.queryForObject(
                "SELECT processed FROM harvest_source WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(result);
    }

    private int deadLetterCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM harvest_dead_letter", Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Launches the job with the fixed identifying parameters.
     *
     * <p>A new {@link Job} instance is built each time (matching production behaviour where
     * the plugin is re-loaded), but the {@link JobRepository} and {@link DataSource} are shared
     * so Spring Batch can find the existing FAILED execution on the second call.
     *
     * @return the final {@link BatchStatus} of the execution
     */
    private BatchStatus launchJob() throws Exception {
        FaultTolerantHarvesterJobPlugin plugin = new FaultTolerantHarvesterJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParameters params = new JobParametersBuilder()
                .addString("DATE", DATE_PARAM)
                .addString("ATTEMPT_NUMBER", ATTEMPT_PARAM)
                .toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        JobExecution execution = launcher.run(job, params);
        return execution.getStatus();
    }

    // ── FTH-08-A: Resume after mid-job abort ──────────────────────────────────────────────────

    /**
     * Proves that re-launching with identical job parameters resumes from the last committed
     * chunk rather than reprocessing the entire dataset from scratch.
     *
     * <p>Data layout:
     * <pre>
     *   id  1-5  : normal rows  → processed in chunk 1 of run 1
     *   id  6    : abort_flag=TRUE → triggers AbortJobException → job FAILS (run 1)
     *   id  7-10 : normal rows  → NOT reached in run 1
     * </pre>
     *
     * <p>After clearing {@code abort_flag} on id 6, re-launching with the same params causes
     * Spring Batch to detect the FAILED JobExecution and restart the step. The reader
     * fast-forwards past rows 1-5 (saved {@code currentItemCount} in
     * {@code BATCH_STEP_EXECUTION_CONTEXT}). Rows 6-10 are processed; job COMPLETES.
     *
     * <p>Idempotency is confirmed: rows 1-5 are already {@code processed=TRUE} from run 1 and
     * the writer's {@code WHERE processed = FALSE} guard ensures they are not touched in run 2.
     */
    @Test
    void restart_afterAbortOnRow6_resumesFromChunk2AndCompletes() throws Exception {
        // ── Seed ──────────────────────────────────────────────────────────────────────────────
        // Normal rows fill chunk 1 (ids 1-5, chunkSize=5)
        for (long i = 1L; i <= 5L; i++) {
            insertNormalRow(i);
        }
        // Row 6 has abort_flag=TRUE — positioned at the START of chunk 2 so chunk 1 always
        // commits cleanly before the AbortJobException is thrown.
        insertAbortRow(6L);
        // Normal rows following the abort row
        for (long i = 7L; i <= 10L; i++) {
            insertNormalRow(i);
        }

        // ── Run 1: FAIL ───────────────────────────────────────────────────────────────────────
        BatchStatus run1Status = launchJob();

        assertThat(run1Status)
                .as("Run 1 must fail because abort_flag=TRUE on row 6 throws AbortJobException")
                .isEqualTo(BatchStatus.FAILED);

        // Chunk 1 (rows 1-5) committed before the abort row was encountered
        for (long i = 1L; i <= 5L; i++) {
            assertThat(isProcessed(i))
                    .as("Row %d should be processed=TRUE after chunk 1 committed in run 1", i)
                    .isTrue();
        }
        // Row 6 itself was not written (AbortJobException prevented the writer from running)
        assertThat(isProcessed(6L))
                .as("Abort row 6 must not be processed=TRUE (step failed before writer)")
                .isFalse();
        // Rows 7-10 were never reached
        for (long i = 7L; i <= 10L; i++) {
            assertThat(isProcessed(i))
                    .as("Row %d should not be processed=TRUE (not reached in run 1)", i)
                    .isFalse();
        }

        // ── Operator fix: clear the abort trigger ─────────────────────────────────────────────
        // In production this simulates the operator removing or correcting the bad input row
        // before requesting a restart. The row is now a normal processable row.
        jdbc.update("UPDATE harvest_source SET abort_flag = FALSE WHERE id = 6");

        // Count how many times rows 1-5 were written before the restart (must stay at 1 each)
        // We can verify this indirectly: processed=TRUE rows will not be re-updated by the
        // idempotent writer (UPDATE ... WHERE processed = FALSE), so their count in the writer
        // stays at exactly 1 across both runs.

        // ── Run 2: RESTART with identical parameters ──────────────────────────────────────────
        // Spring Batch sees the existing FAILED JobExecution for these exact params, finds
        // the same JobInstance, and restarts the step from the saved reader offset.
        BatchStatus run2Status = launchJob();

        assertThat(run2Status)
                .as("Run 2 must complete after the abort trigger is cleared")
                .isEqualTo(BatchStatus.COMPLETED);

        // All rows must be processed after run 2
        for (long i = 1L; i <= 10L; i++) {
            assertThat(isProcessed(i))
                    .as("Row %d must be processed=TRUE after successful restart", i)
                    .isTrue();
        }

        // No dead-letter rows — abort raises AbortJobException (not skippable), and row 6
        // was fixed before run 2, so no skip/retry-exhaustion occurred.
        assertThat(deadLetterCount())
                .as("No dead-letter rows expected (AbortJobException is not skippable)")
                .isZero();

        // ── Spring Batch metadata: 2 executions, 1 JobInstance ───────────────────────────────
        // This is the definitive proof that run 2 was a RESTART and not a fresh run.
        List<Map<String, Object>> executions = jdbc.queryForList(
                "SELECT * FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID");
        assertThat(executions)
                .as("Exactly 2 JobExecutions must exist (run 1 FAILED + run 2 COMPLETED)")
                .hasSize(2);

        Long instanceId1 = (Long) executions.get(0).get("JOB_INSTANCE_ID");
        Long instanceId2 = (Long) executions.get(1).get("JOB_INSTANCE_ID");
        assertThat(instanceId1)
                .as("Both executions must share the same JobInstance (same identifying params)")
                .isEqualTo(instanceId2);

        // run 1 status is stored as 'FAILED' in the metadata
        assertThat(executions.get(0).get("STATUS"))
                .as("First execution status must be FAILED in BATCH_JOB_EXECUTION")
                .isEqualTo("FAILED");

        // run 2 status is stored as 'COMPLETED' in the metadata
        assertThat(executions.get(1).get("STATUS"))
                .as("Second execution status must be COMPLETED in BATCH_JOB_EXECUTION")
                .isEqualTo("COMPLETED");

        // ── Confirm rows 1-5 were NOT reprocessed by run 2 ───────────────────────────────────
        // The write count for already-processed rows should be 0 in run 2.
        // We verify this via the step execution context: the read count in run 2 should
        // be <= 5 (rows 6-10) if the reader fast-forwarded past rows 1-5.
        //
        // Read the step execution for run 2 and check READ_COUNT.
        // chunkSize=5, rows resumed from offset 5 → rows 6-10 = 5 rows read in run 2.
        Long run2ExecutionId = (Long) executions.get(1).get("JOB_EXECUTION_ID");
        List<Map<String, Object>> stepExecs = jdbc.queryForList(
                "SELECT * FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?",
                run2ExecutionId);
        assertThat(stepExecs).hasSize(1);
        // READ_COUNT in run 2 must be 5 (rows 6-10), not 10 (all rows from scratch).
        // This proves the reader fast-forwarded past the 5 already-committed rows.
        Object readCount = stepExecs.get(0).get("READ_COUNT");
        assertThat(((Number) readCount).intValue())
                .as("Run 2 should read only rows 6-10 (5 items), not restart from row 1")
                .isEqualTo(5);
    }
}
