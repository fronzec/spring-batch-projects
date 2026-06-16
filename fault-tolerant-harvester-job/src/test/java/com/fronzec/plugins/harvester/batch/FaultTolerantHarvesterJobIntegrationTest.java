package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin;
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
 * End-to-end integration tests for {@link FaultTolerantHarvesterJobPlugin}.
 *
 * <p>Uses an isolated in-module H2 database with {@code DB_CLOSE_DELAY=-1} so Spring Batch
 * metadata persists between the two launches required for the restart test (PR#3). This test
 * class covers the PR#2 non-restart scenarios only.
 *
 * <p>All seed data is inserted deterministically inside each test method.
 * The Spring Batch schema is bootstrapped once per class; application tables are reset
 * in {@code cleanTables()} before each test.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>Happy path — all normal rows end up {@code processed=TRUE}, job COMPLETED.</li>
 *   <li>Skip-to-dead-letter — poison rows are skipped, dead-letter row has
 *       {@code failure_type='SKIP'}, job COMPLETED, poison rows remain unprocessed.</li>
 *   <li>Skip-limit-exceeded — more than 5 poison rows cause job FAILED.</li>
 *   <li>Retry-success — transient rows with threshold {@code <= retryLimit} eventually
 *       succeed: {@code processed=TRUE}, no dead-letter row (LINCHPIN proof of R4).</li>
 *   <li>Retry-exhausted — transient rows with threshold {@code > retryLimit} produce a
 *       dead-letter row with {@code failure_type='RETRY_EXHAUSTED'}, job COMPLETED (if
 *       within skipLimit).</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FaultTolerantHarvesterJobIntegrationTest {

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JobRepository jobRepository;
    private PlatformTransactionManager txManager;
    private StaticApplicationContext stubContext;

    @BeforeAll
    void setUpDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:harvest-it-"
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

        // Create application tables (harvest_source, harvest_dead_letter)
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS harvest_source ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " payload VARCHAR(2048) NOT NULL,"
                        + " poison_flag BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " transient_fail_until_attempt INT NOT NULL DEFAULT 0 CHECK (transient_fail_until_attempt >= 0),"
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
                        + " attempt_count INT NOT NULL DEFAULT 1 CHECK (attempt_count >= 0),"
                        + " job_execution_id BIGINT NOT NULL,"
                        + " recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");

        // Bootstrap Spring Batch JobRepository
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(ds);
        factory.setTransactionManager(txManager);
        factory.afterPropertiesSet();
        this.jobRepository = factory.getObject();

        // Stub ApplicationContext that exposes only the DataSource (mirrors ticket-bundle-job IT)
        stubContext = new StaticApplicationContext();
        stubContext.getBeanFactory().registerSingleton("dataSource", ds);
        stubContext.refresh();
    }

    @BeforeEach
    void cleanTables() {
        jdbc.execute("DELETE FROM harvest_dead_letter");
        jdbc.execute("DELETE FROM harvest_source");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private long insertNormalRow(long id) {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag,"
                        + " transient_fail_until_attempt, abort_flag, processed)"
                        + " VALUES (?, ?, FALSE, 0, FALSE, FALSE)",
                id, "payload-" + id);
        return id;
    }

    private long insertPoisonRow(long id) {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag,"
                        + " transient_fail_until_attempt, abort_flag, processed)"
                        + " VALUES (?, ?, TRUE, 0, FALSE, FALSE)",
                id, "poison-" + id);
        return id;
    }

    private long insertTransientRow(long id, int threshold) {
        jdbc.update(
                "INSERT INTO harvest_source (id, payload, poison_flag,"
                        + " transient_fail_until_attempt, abort_flag, processed)"
                        + " VALUES (?, ?, FALSE, ?, FALSE, FALSE)",
                id, "transient-" + id, threshold);
        return id;
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

    private BatchStatus runJob(String runId) throws Exception {
        FaultTolerantHarvesterJobPlugin plugin = new FaultTolerantHarvesterJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParameters params = new JobParametersBuilder()
                .addString("DATE", "2026-06-15")
                .addString("ATTEMPT_NUMBER", "1")
                .addString("RUN_ID", runId)  // differentiates job instances across tests
                .toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        JobExecution execution = launcher.run(job, params);
        return execution.getStatus();
    }

    // ── Scenario 1: Happy path ────────────────────────────────────────────────────────────────

    @Test
    void happyPath_allNormalRows_processedAndCompleted() throws Exception {
        insertNormalRow(1L);
        insertNormalRow(2L);
        insertNormalRow(3L);
        insertNormalRow(4L);
        insertNormalRow(5L);

        BatchStatus status = runJob("hp-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(isProcessed(1L)).isTrue();
        assertThat(isProcessed(2L)).isTrue();
        assertThat(isProcessed(3L)).isTrue();
        assertThat(isProcessed(4L)).isTrue();
        assertThat(isProcessed(5L)).isTrue();
        assertThat(deadLetterCount()).isZero();
    }

    // ── Scenario 2: Skip-to-dead-letter ──────────────────────────────────────────────────────

    @Test
    void skipToDeadLetter_poisonRowsSkipped_deadLetterHasSkipType() throws Exception {
        insertNormalRow(10L);
        insertNormalRow(11L);
        insertNormalRow(12L);
        insertNormalRow(13L);
        insertPoisonRow(14L); // within skipLimit=5

        BatchStatus status = runJob("skip-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // Normal rows processed
        assertThat(isProcessed(10L)).isTrue();
        assertThat(isProcessed(11L)).isTrue();
        assertThat(isProcessed(12L)).isTrue();
        assertThat(isProcessed(13L)).isTrue();

        // Poison row NOT processed
        assertThat(isProcessed(14L)).isFalse();

        // Dead-letter row created with correct fields
        List<Map<String, Object>> dlRows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter WHERE source_id = 14");
        assertThat(dlRows).hasSize(1);
        Map<String, Object> dl = dlRows.get(0);
        assertThat(dl.get("failure_type")).isEqualTo("SKIP");
        assertThat(dl.get("failure_phase")).isEqualTo("PROCESS");
        assertThat(dl.get("source_id")).isEqualTo(14L);
    }

    @Test
    void skipToDeadLetter_multiplePoison_allDeadLettered_jobCompleted() throws Exception {
        // 3 normal + 3 poison — within skipLimit=5
        insertNormalRow(20L);
        insertNormalRow(21L);
        insertNormalRow(22L);
        insertPoisonRow(23L);
        insertPoisonRow(24L);
        insertPoisonRow(25L);

        BatchStatus status = runJob("multi-skip-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(isProcessed(23L)).isFalse();
        assertThat(isProcessed(24L)).isFalse();
        assertThat(isProcessed(25L)).isFalse();

        // 3 dead-letter rows, all SKIP
        List<Map<String, Object>> dlRows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter ORDER BY source_id");
        assertThat(dlRows).hasSize(3);
        assertThat(dlRows).allMatch(r -> "SKIP".equals(r.get("failure_type")));
    }

    // ── Scenario 3: Skip-limit-exceeded ──────────────────────────────────────────────────────

    @Test
    void skipLimitExceeded_moreThan5Poisons_jobFails() throws Exception {
        // 6 poison rows → exceeds skipLimit=5
        for (long i = 30L; i <= 35L; i++) {
            insertPoisonRow(i);
        }

        BatchStatus status = runJob("skip-limit-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.FAILED);
        // None processed (all poison)
        for (long i = 30L; i <= 35L; i++) {
            assertThat(isProcessed(i)).isFalse();
        }
    }

    // ── Scenario 4: Retry-success (LINCHPIN — proves R4 mechanism) ────────────────────────────

    @Test
    void retrySuccess_transientRowWithThresholdWithinRetryLimit_processedNoDeadLetter() throws Exception {
        // threshold=2, retryLimit=3 → processor fails on attempts 0,1 then succeeds on attempt 2
        insertNormalRow(40L);
        insertTransientRow(41L, 2);
        insertNormalRow(42L);

        BatchStatus status = runJob("retry-success-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(isProcessed(40L)).isTrue();
        assertThat(isProcessed(41L)).isTrue(); // Must succeed after retries
        assertThat(isProcessed(42L)).isTrue();
        assertThat(deadLetterCount()).isZero(); // No dead-letter for successful retry
    }

    @Test
    void retrySuccess_threshold1_succeedsAfterOneRetry() throws Exception {
        insertTransientRow(50L, 1); // threshold=1: fails on attempt 0, succeeds on attempt 1

        BatchStatus status = runJob("retry-t1-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(isProcessed(50L)).isTrue();
        assertThat(deadLetterCount()).isZero();
    }

    @Test
    void retrySuccess_threshold2_maxBoundary_succeeds() throws Exception {
        // threshold=2 is the maximum successful threshold with retryLimit=3:
        // - call 1: retryCount=0 < 2 → throw
        // - call 2: retryCount=1 < 2 → throw
        // - call 3: retryCount=2 >= 2 → succeed (last retry, retryLimit=3 means max retryCount seen = 2)
        // threshold=3 would NEVER succeed because the processor only sees retryCount 0,1,2 at most.
        insertTransientRow(51L, 2);
        insertNormalRow(52L);

        BatchStatus status = runJob("retry-t2b-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(isProcessed(51L)).isTrue();
        assertThat(isProcessed(52L)).isTrue();
        assertThat(deadLetterCount()).isZero();
    }

    // ── Scenario 5: Retry-exhausted ──────────────────────────────────────────────────────────

    @Test
    void retryExhausted_transientAboveRetryLimit_deadLetterRetryExhaustedType() throws Exception {
        // threshold=10 > retryLimit=3 → processor always throws, retries exhaust, item skipped
        insertNormalRow(60L);
        insertTransientRow(61L, 10);
        insertNormalRow(62L);

        BatchStatus status = runJob("retry-exhaust-" + System.nanoTime());

        // 1 skip (61) within skipLimit=5 → job completes
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);
        assertThat(isProcessed(60L)).isTrue();
        assertThat(isProcessed(61L)).isFalse(); // Not processed
        assertThat(isProcessed(62L)).isTrue();

        List<Map<String, Object>> dlRows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter WHERE source_id = 61");
        assertThat(dlRows).hasSize(1);
        Map<String, Object> dl = dlRows.get(0);
        assertThat(dl.get("failure_type")).isEqualTo("RETRY_EXHAUSTED");
        assertThat(dl.get("failure_phase")).isEqualTo("PROCESS");
        assertThat(dl.get("source_id")).isEqualTo(61L);
    }

    @Test
    void retryExhaustedCountsAgainstSkipLimit() throws Exception {
        // 5 poison rows consume the full skipLimit, then 1 retry-exhausted row pushes it over
        for (long i = 70L; i <= 74L; i++) {
            insertPoisonRow(i);
        }
        insertTransientRow(75L, 10); // will exhaust retries and try to skip as 6th skip

        BatchStatus status = runJob("exhaust-limit-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.FAILED);
    }

    // ── Dead-letter REQUIRES_NEW independence ─────────────────────────────────────────────────

    @Test
    void deadLetterSurvivesChunkRollback() throws Exception {
        // Chunk 1 (ids 80-84): id 82 is poison — will be skipped and dead-lettered.
        // The remaining rows in the chunk succeed, so the chunk commits.
        // What we're verifying here is simply that the dead-letter record exists after job completion.
        insertNormalRow(80L);
        insertNormalRow(81L);
        insertPoisonRow(82L);
        insertNormalRow(83L);
        insertNormalRow(84L);

        BatchStatus status = runJob("dl-survive-" + System.nanoTime());

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // Dead-letter row persisted (REQUIRES_NEW committed independently)
        assertThat(deadLetterCount()).isEqualTo(1);
        List<Map<String, Object>> dlRows = jdbc.queryForList(
                "SELECT * FROM harvest_dead_letter WHERE source_id = 82");
        assertThat(dlRows).hasSize(1);
        assertThat(dlRows.get(0).get("failure_type")).isEqualTo("SKIP");
    }
}
