package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Unit tests for {@link BundlePersistTasklet}.
 *
 * <p>Uses a real H2 database for the JDBC assertions, avoiding the need for Mockito.
 * The Spring Batch domain objects (StepExecution, JobExecution, ChunkContext) are
 * constructed manually with minimal wiring — no Spring context needed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BundlePersistTaskletTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void setUpDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:tasklet-test-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS generated_bundles ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " event_id BIGINT NOT NULL,"
                        + " storage_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL',"
                        + " storage_path VARCHAR(1024) NOT NULL,"
                        + " checksum_sha256 CHAR(64) NOT NULL,"
                        + " file_size_bytes BIGINT NOT NULL,"
                        + " ticket_count INT NOT NULL,"
                        + " status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " CONSTRAINT uk_gb_event UNIQUE (event_id)"
                        + ")");
    }

    @BeforeEach
    void cleanTable() {
        jdbc.execute("DELETE FROM generated_bundles");
    }

    private ChunkContext buildChunkContext(long eventId, String outputDir,
            String tempZipPath, int ticketCount) {
        JobParameters params = new JobParametersBuilder()
                .addString("EVENT_ID", String.valueOf(eventId))
                .addString("OUTPUT_DIR", outputDir)
                .addLong("run.id", System.nanoTime())
                .toJobParameters();

        JobInstance jobInstance = new JobInstance(1L, "ticket-bundle-job");
        JobExecution jobExecution = new JobExecution(1L, jobInstance, params);

        // Write the handoff keys to the JOB ExecutionContext
        ExecutionContext jobCtx = jobExecution.getExecutionContext();
        jobCtx.putInt(BundleStepListener.CTX_TICKET_COUNT, ticketCount);
        if (tempZipPath != null) {
            jobCtx.putString(BundleStepListener.CTX_TEMP_PATH, tempZipPath);
        }

        StepExecution stepExecution = new StepExecution("ticketBundlePersistStep", jobExecution);
        StepContext stepContext = new StepContext(stepExecution);
        return new ChunkContext(stepContext);
    }

    @Test
    void execute_zeroCount_skipsUploadAndInsert() throws Exception {
        BundleParamsHolder holder = new BundleParamsHolder();
        holder.setEventId("10");
        holder.setOutputDir(tempDir.toString());

        BundlePersistTasklet tasklet = new BundlePersistTasklet(holder, jdbc);

        ChunkContext ctx = buildChunkContext(10L, tempDir.toString(), null, 0);
        RepeatStatus status = tasklet.execute(null, ctx);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        // No DB insert performed
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_bundles WHERE event_id = 10", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void execute_uploadsZipAndInserts_generatedBundles() throws Exception {
        // Create a real temp zip file on disk
        Path tempZip = Files.createTempFile(tempDir, "bundle-test-", ".zip");
        byte[] zipContent = new byte[]{0x50, 0x4B, 0x05, 0x06}; // minimal zip end-of-central-dir
        Files.write(tempZip, zipContent);

        BundleParamsHolder holder = new BundleParamsHolder();
        holder.setEventId("42");
        holder.setOutputDir(tempDir.toString());

        BundlePersistTasklet tasklet = new BundlePersistTasklet(holder, jdbc);

        ChunkContext ctx = buildChunkContext(42L, tempDir.toString(), tempZip.toString(), 3);
        RepeatStatus status = tasklet.execute(null, ctx);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        // Temp file deleted
        assertThat(tempZip).doesNotExist();

        // DB row inserted
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM generated_bundles WHERE event_id = 42");
        assertThat(rows).hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(row.get("ticket_count")).isEqualTo(3);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat((String) row.get("storage_path")).isNotBlank();
        assertThat((String) row.get("checksum_sha256")).hasSize(64);
        assertThat((Long) row.get("file_size_bytes")).isGreaterThan(0L);
    }

    @Test
    void execute_deletesTemp_evenOnJdbcFailure() throws Exception {
        // Create a temp zip file
        Path tempZip = Files.createTempFile(tempDir, "fail-test-", ".zip");
        Files.write(tempZip, new byte[]{0x50, 0x4B});

        BundleParamsHolder holder = new BundleParamsHolder();
        holder.setEventId("999");
        holder.setOutputDir(tempDir.toString());

        // Use a broken JDBC template that will fail on INSERT
        JdbcDataSource badDs = new JdbcDataSource();
        badDs.setURL("jdbc:h2:mem:nonexistent-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        badDs.setUser("sa");
        badDs.setPassword("");
        JdbcTemplate brokenJdbc = new JdbcTemplate(badDs);
        // The generated_bundles table doesn't exist on the bad datasource -> INSERT will fail

        BundlePersistTasklet tasklet = new BundlePersistTasklet(holder, brokenJdbc);

        ChunkContext ctx = buildChunkContext(999L, tempDir.toString(), tempZip.toString(), 2);

        try {
            tasklet.execute(null, ctx);
        } catch (Exception ignored) {
            // Expected — INSERT fails
        }

        // Temp file MUST be deleted even on failure (finally block)
        assertThat(tempZip).doesNotExist();
    }

    @Test
    void execute_nonZeroCountWithNoTempPath_throwsIllegalStateException() throws Exception {
        // ticketCount > 0 but CTX_TEMP_PATH was never written to the job context (FIX #2):
        // this is a handoff failure and must be surfaced, not silently no-op'd.
        BundleParamsHolder holder = new BundleParamsHolder();
        holder.setEventId("55");
        holder.setOutputDir(tempDir.toString());

        BundlePersistTasklet tasklet = new BundlePersistTasklet(holder, jdbc);

        // Build a context with ticketCount=2 but no CTX_TEMP_PATH entry
        ChunkContext ctx = buildChunkContext(55L, tempDir.toString(), null, 2);

        assertThatThrownBy(() -> tasklet.execute(null, ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BundleStepListener.CTX_TEMP_PATH)
                .hasMessageContaining("ticket_count=2");
    }

    @Test
    void execute_idempotent_deleteAndReinsert() throws Exception {
        // Pre-seed a row for event 77
        jdbc.update(
                "INSERT INTO generated_bundles"
                        + " (event_id, storage_type, storage_path, checksum_sha256,"
                        + "  file_size_bytes, ticket_count, status)"
                        + " VALUES (77, 'LOCAL', '/old/path.zip', ?, 100, 2, 'COMPLETED')",
                "a".repeat(64));

        Path tempZip = Files.createTempFile(tempDir, "idempotent-", ".zip");
        Files.write(tempZip, new byte[]{0x50, 0x4B, 0x05, 0x06});

        BundleParamsHolder holder = new BundleParamsHolder();
        holder.setEventId("77");
        holder.setOutputDir(tempDir.toString());

        BundlePersistTasklet tasklet = new BundlePersistTasklet(holder, jdbc);

        ChunkContext ctx = buildChunkContext(77L, tempDir.toString(), tempZip.toString(), 5);
        tasklet.execute(null, ctx);

        // Still exactly one row after re-run
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_bundles WHERE event_id = 77", Integer.class);
        assertThat(count).isEqualTo(1);

        // ticket_count updated to new value
        Integer newCount = jdbc.queryForObject(
                "SELECT ticket_count FROM generated_bundles WHERE event_id = 77", Integer.class);
        assertThat(newCount).isEqualTo(5);
    }
}
