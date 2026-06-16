package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.ticketbundle.TicketBundleJobPlugin;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * End-to-end integration test for {@link TicketBundleJobPlugin}.
 *
 * <p>Uses an isolated in-module H2 database. No shared state with the fr-batch-service
 * test suite. The test bootstraps a minimal Spring Batch JobRepository on H2, constructs
 * the plugin, and wires it with a stub ApplicationContext.
 *
 * <p>CRITICAL: source PDFs are read from their absolute {@code storage_path}, so this test
 * creates REAL temp files under {@code @TempDir} and seeds {@code generated_files.storage_path}
 * with those absolute paths.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketBundleJobIntegrationTest {

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF

    @TempDir
    Path outputDir;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JobRepository jobRepository;
    private PlatformTransactionManager txManager;
    private ApplicationContext stubContext;

    @BeforeAll
    void setUpDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ticket-bundle-it-" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
        this.txManager = new DataSourceTransactionManager(ds);

        // Apply Spring Batch H2 schema
        try (Connection conn = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
        }

        // Create application tables
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS event_tickets ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " event_id BIGINT NOT NULL,"
                        + " ticket_code VARCHAR(64) NOT NULL,"
                        + " holder_name VARCHAR(255) NOT NULL,"
                        + " event_name VARCHAR(255) NOT NULL,"
                        + " event_location VARCHAR(255) NULL,"
                        + " seat VARCHAR(64) NULL,"
                        + " event_datetime TIMESTAMP NOT NULL,"
                        + " processed BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " CONSTRAINT uk_event_tickets_ticket_code UNIQUE (ticket_code)"
                        + ")");

        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS generated_files ("
                        + " id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + " ticket_id BIGINT NOT NULL,"
                        + " storage_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL',"
                        + " storage_path VARCHAR(1024) NOT NULL,"
                        + " checksum_sha256 CHAR(64) NOT NULL,"
                        + " file_size_bytes BIGINT NOT NULL,"
                        + " generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " CONSTRAINT fk_generated_files_ticket"
                        + "     FOREIGN KEY (ticket_id) REFERENCES event_tickets (id),"
                        + " CONSTRAINT uk_generated_files_ticket UNIQUE (ticket_id)"
                        + ")");

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
                        + " CONSTRAINT uk_generated_bundles_event UNIQUE (event_id)"
                        + ")");

        // Bootstrap Spring Batch JobRepository
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(ds);
        factory.setTransactionManager(txManager);
        factory.afterPropertiesSet();
        this.jobRepository = factory.getObject();

        // Stub ApplicationContext exposes only the DataSource
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.getBeanFactory().registerSingleton("dataSource", ds);
        ctx.refresh();
        this.stubContext = ctx;
    }

    @BeforeEach
    void cleanTables() {
        jdbc.execute("DELETE FROM generated_bundles");
        jdbc.execute("DELETE FROM generated_files");
        jdbc.execute("DELETE FROM event_tickets");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private long insertTicket(String ticketCode, long eventId) {
        jdbc.update(
                "INSERT INTO event_tickets"
                        + " (event_id, ticket_code, holder_name, event_name,"
                        + "  event_location, seat, event_datetime, processed)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)",
                eventId, ticketCode, "Test Holder", "Test Event",
                "Test Venue", "A1",
                Timestamp.valueOf(LocalDateTime.of(2024, 6, 15, 18, 0)));
        return jdbc.queryForObject(
                "SELECT id FROM event_tickets WHERE ticket_code = ?", Long.class, ticketCode);
    }

    private Path createSourcePdf(String filename) throws Exception {
        Path file = outputDir.resolve(filename);
        byte[] content = ("%PDF-test-content-" + filename)
                .getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        return file;
    }

    private long insertGeneratedFile(long ticketId, Path sourcePath) {
        byte[] content;
        try {
            content = Files.readAllBytes(sourcePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String checksum = "a".repeat(64); // placeholder — not validated in IT assertions
        jdbc.update(
                "INSERT INTO generated_files"
                        + " (ticket_id, storage_type, storage_path, checksum_sha256, file_size_bytes)"
                        + " VALUES (?, 'LOCAL', ?, ?, ?)",
                ticketId, sourcePath.toAbsolutePath().toString(), checksum, content.length);
        return jdbc.queryForObject(
                "SELECT id FROM generated_files WHERE ticket_id = ?", Long.class, ticketId);
    }

    private BatchStatus runJob(long eventId) throws Exception {
        TicketBundleJobPlugin plugin = new TicketBundleJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParameters params = new JobParametersBuilder()
                .addString("DATE", "2024-06-15")
                .addString("OUTPUT_DIR", outputDir.toAbsolutePath().toString())
                .addString("EVENT_ID", String.valueOf(eventId))
                .addLong("run.id", System.nanoTime())
                .toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        JobExecution execution = launcher.run(job, params);
        return execution.getStatus();
    }

    // ── Scenarios ────────────────────────────────────────────────────────────────────────────

    @Test
    void happyPath_bundlesAllPdfsForEvent() throws Exception {
        long eventId = 10L;
        long t1 = insertTicket("BUNDLE-001", eventId);
        long t2 = insertTicket("BUNDLE-002", eventId);
        long t3 = insertTicket("BUNDLE-003", eventId);

        Path pdf1 = createSourcePdf("ticket-" + t1 + ".pdf");
        Path pdf2 = createSourcePdf("ticket-" + t2 + ".pdf");
        Path pdf3 = createSourcePdf("ticket-" + t3 + ".pdf");

        insertGeneratedFile(t1, pdf1);
        insertGeneratedFile(t2, pdf2);
        insertGeneratedFile(t3, pdf3);

        BatchStatus status = runJob(eventId);
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // ZIP file exists at deterministic path
        Path bundleZip = outputDir.resolve("bundles/event-" + eventId + ".zip");
        assertThat(bundleZip).exists();

        // ZIP contains exactly 3 entries
        List<String> entryNames = extractEntryNames(bundleZip);
        assertThat(entryNames).hasSize(3);

        // DB row exists with correct fields
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM generated_bundles WHERE event_id = ?", eventId);
        assertThat(rows).hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(row.get("ticket_count")).isEqualTo(3);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat((String) row.get("storage_path")).isNotBlank();
        assertThat((String) row.get("checksum_sha256")).hasSize(64);
        assertThat((Long) row.get("file_size_bytes")).isGreaterThan(0L);
    }

    @Test
    void eventScope_onlyBundlesMatchingEvent() throws Exception {
        // Seed 2 tickets for event 20, 2 for event 21
        long t20a = insertTicket("EVT20-001", 20L);
        long t20b = insertTicket("EVT20-002", 20L);
        long t21a = insertTicket("EVT21-001", 21L);
        long t21b = insertTicket("EVT21-002", 21L);

        insertGeneratedFile(t20a, createSourcePdf("20a.pdf"));
        insertGeneratedFile(t20b, createSourcePdf("20b.pdf"));
        insertGeneratedFile(t21a, createSourcePdf("21a.pdf"));
        insertGeneratedFile(t21b, createSourcePdf("21b.pdf"));

        BatchStatus status = runJob(20L);
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        Path bundleZip = outputDir.resolve("bundles/event-20.zip");
        assertThat(bundleZip).exists();

        List<String> entries = extractEntryNames(bundleZip);
        assertThat(entries).hasSize(2);
        // No event-21 entries
        assertThat(entries).noneMatch(n -> n.contains("21a") || n.contains("21b"));

        // No row for event 21
        Integer count21 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_bundles WHERE event_id = 21", Integer.class);
        assertThat(count21).isZero();
    }

    @Test
    void emptyEvent_completesNoOp() throws Exception {
        // No rows for event 999
        BatchStatus status = runJob(999L);
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // ZIP file must NOT exist
        Path bundleZip = outputDir.resolve("bundles/event-999.zip");
        assertThat(bundleZip).doesNotExist();

        // No DB row
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_bundles WHERE event_id = 999", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void missingSourceFile_failsFast() throws Exception {
        long eventId = 888L;
        long ticket = insertTicket("MISSING-001", eventId);

        // generated_files row points to a non-existent path
        Path nonExistent = outputDir.resolve("does-not-exist-" + System.nanoTime() + ".pdf");
        jdbc.update(
                "INSERT INTO generated_files"
                        + " (ticket_id, storage_type, storage_path, checksum_sha256, file_size_bytes)"
                        + " VALUES (?, 'LOCAL', ?, ?, ?)",
                ticket, nonExistent.toAbsolutePath().toString(), "a".repeat(64), 0L);

        BatchStatus status = runJob(eventId);
        assertThat(status).isEqualTo(BatchStatus.FAILED);

        // No bundle row inserted
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_bundles WHERE event_id = ?", Integer.class, eventId);
        assertThat(count).isZero();
    }

    @Test
    void reRun_idempotent() throws Exception {
        long eventId = 10L;
        long t1 = insertTicket("IDEM-001", eventId);
        long t2 = insertTicket("IDEM-002", eventId);

        Path pdf1 = createSourcePdf("idem1.pdf");
        Path pdf2 = createSourcePdf("idem2.pdf");
        insertGeneratedFile(t1, pdf1);
        insertGeneratedFile(t2, pdf2);

        // First run
        BatchStatus status1 = runJob(eventId);
        assertThat(status1).isEqualTo(BatchStatus.COMPLETED);

        // Second run (same event, new run.id)
        BatchStatus status2 = runJob(eventId);
        assertThat(status2).isEqualTo(BatchStatus.COMPLETED);

        // Still exactly one row after second run
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_bundles WHERE event_id = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);

        // ZIP exists
        Path bundleZip = outputDir.resolve("bundles/event-" + eventId + ".zip");
        assertThat(bundleZip).exists();
        assertThat(extractEntryNames(bundleZip)).hasSize(2);
    }

    // ── Utilities ────────────────────────────────────────────────────────────────────────────

    private List<String> extractEntryNames(Path zipFile) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }
}
