package com.fronzec.plugins.ticketpdf.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fronzec.plugins.ticketpdf.TicketPdfJobPlugin;
import com.fronzec.plugins.ticketpdf.domain.HmacTokenService;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.util.FileCopyUtils;

/**
 * End-to-end integration test for {@link TicketPdfJobPlugin}.
 *
 * <p>Uses approach (a): an isolated in-module H2 database. No shared state with the
 * fr-batch-service test suite. The test bootstraps a minimal Spring Batch JobRepository
 * on H2, constructs the plugin, and wires it with a stub ApplicationContext.
 *
 * <p>TOKEN_SECRET is 38+ bytes (satisfies the {@literal >=} 32-byte requirement).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketPdfJobIntegrationTest {

    private static final String TOKEN_SECRET =
            "integration-test-secret-must-be-32-bytes!";
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
        // Isolated in-memory H2 — unique name prevents collision with any other test JVM
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ticket-pdf-it-" + System.nanoTime()
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

        // Apply application tables (event_tickets + generated_files)
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

    @AfterAll
    void tearDown() {
        jdbc.execute("DELETE FROM generated_files");
        jdbc.execute("DELETE FROM event_tickets");
    }

    private long insertTicket(String ticketCode, String holderName, String eventName,
            long eventId) {
        jdbc.update(
                "INSERT INTO event_tickets"
                        + " (event_id, ticket_code, holder_name, event_name,"
                        + "  event_location, seat, event_datetime, processed)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)",
                eventId, ticketCode, holderName, eventName,
                "Buenos Aires", "A1", Timestamp.valueOf(LocalDateTime.of(2024, 6, 15, 18, 0)));
        return jdbc.queryForObject(
                "SELECT id FROM event_tickets WHERE ticket_code = ?", Long.class, ticketCode);
    }

    @Test
    void happyPath_threeTickets_allProcessed() throws Exception {
        // Seed 3 tickets
        long id1 = insertTicket("CODE-001", "Alice Smith", "Spring Batch Conf", 10L);
        long id2 = insertTicket("CODE-002", "Bob Jones", "Spring Batch Conf", 10L);
        long id3 = insertTicket("CODE-003", "Carol White", "Spring Batch Conf", 10L);

        Path jobOutputDir = outputDir.resolve("happy-path");

        // Run the job
        BatchStatus status = runJob(jobOutputDir, null);
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // 3 PDF files exist
        List<Path> pdfs = Files.walk(jobOutputDir)
                .filter(p -> p.toString().endsWith(".pdf"))
                .toList();
        assertThat(pdfs).hasSize(3);

        // All PDF files start with %PDF
        for (Path pdf : pdfs) {
            byte[] bytes = Files.readAllBytes(pdf);
            assertThat(bytes).startsWith(PDF_MAGIC);
        }

        // 3 generated_files rows
        Integer gfCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_files WHERE ticket_id IN (?, ?, ?)",
                Integer.class, id1, id2, id3);
        assertThat(gfCount).isEqualTo(3);

        // All tickets marked processed
        Integer unprocessed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_tickets"
                        + " WHERE id IN (?, ?, ?) AND processed = FALSE",
                Integer.class, id1, id2, id3);
        assertThat(unprocessed).isEqualTo(0);

        // QR token is verifiable for ticket 1
        String expectedToken = HmacTokenService.sign(id1, "CODE-001", TOKEN_SECRET);
        String storedPath = jdbc.queryForObject(
                "SELECT storage_path FROM generated_files WHERE ticket_id = ?",
                String.class, id1);
        assertThat(storedPath).isNotBlank();
        // Token correctness: split on last dot, left == "{id}|{code}"
        int lastDot = expectedToken.lastIndexOf('.');
        assertThat(expectedToken.substring(0, lastDot)).isEqualTo(id1 + "|CODE-001");
    }

    @Test
    void eventIdFilter_processesOnlyMatchingTickets() throws Exception {
        long id4 = insertTicket("CODE-004", "Dan Brown", "Event A", 20L);
        long id5 = insertTicket("CODE-005", "Eve Stone", "Event B", 21L);

        Path jobOutputDir = outputDir.resolve("event-filter");

        // Run with EVENT_ID=20 — should only process id4
        BatchStatus status = runJob(jobOutputDir, "20");
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // Only 1 file for the matching event
        List<Path> pdfs = Files.walk(jobOutputDir)
                .filter(p -> p.toString().endsWith(".pdf"))
                .toList();
        assertThat(pdfs).hasSize(1);
        assertThat(pdfs.get(0).getFileName().toString()).isEqualTo(id4 + ".pdf");

        // id4 is processed, id5 is not
        Boolean id4Processed = jdbc.queryForObject(
                "SELECT processed FROM event_tickets WHERE id = ?", Boolean.class, id4);
        Boolean id5Processed = jdbc.queryForObject(
                "SELECT processed FROM event_tickets WHERE id = ?", Boolean.class, id5);
        assertThat(id4Processed).isTrue();
        assertThat(id5Processed).isFalse();
    }

    @Test
    void zeroTickets_completesWithNoOutput() throws Exception {
        // No unprocessed tickets for event_id=999
        Path jobOutputDir = outputDir.resolve("zero-tickets");
        Files.createDirectories(jobOutputDir);

        // Run with a filter that matches nothing
        BatchStatus status = runJob(jobOutputDir, "999");
        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // No PDF files written
        long pdfCount = Files.exists(jobOutputDir)
                ? Files.walk(jobOutputDir)
                        .filter(p -> p.toString().endsWith(".pdf"))
                        .count()
                : 0L;
        assertThat(pdfCount).isEqualTo(0L);
    }

    private BatchStatus runJob(Path jobOutput, String eventId) throws Exception {
        TicketPdfJobPlugin plugin = new TicketPdfJobPlugin();
        Job job = plugin.configureJob(jobRepository, txManager, stubContext);

        JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                .addString("DATE", "2024-06-15")
                .addString("OUTPUT_DIR", jobOutput.toAbsolutePath().toString())
                .addString("TOKEN_SECRET", TOKEN_SECRET)
                .addLong("run.id", System.nanoTime()); // uniquifier

        if (eventId != null && !eventId.isBlank()) {
            paramsBuilder.addString("EVENT_ID", eventId);
        } else {
            paramsBuilder.addString("EVENT_ID", "");
        }

        JobParameters params = paramsBuilder.toJobParameters();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();

        JobExecution execution = launcher.run(job, params);
        return execution.getStatus();
    }
}
