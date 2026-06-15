package com.fronzec.plugins.ticketpdf;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.plugins.ticketpdf.batch.JobParamsHolder;
import com.fronzec.plugins.ticketpdf.batch.TicketDocumentProcessor;
import com.fronzec.plugins.ticketpdf.batch.TicketFileItemWriter;
import com.fronzec.plugins.ticketpdf.batch.TicketRowMapper;
import com.fronzec.plugins.ticketpdf.batch.TicketStepListener;
import com.fronzec.plugins.ticketpdf.domain.Ticket;
import com.fronzec.plugins.ticketpdf.domain.TicketDocument;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch plugin that generates PDF tickets with embedded QR codes for events.
 *
 * <p>Implemented as a shaded standalone JAR loaded dynamically by the host service.
 * No Spring annotations — instantiated via {@code getDeclaredConstructor().newInstance()}.
 *
 * <h3>Job parameters</h3>
 * <ul>
 *   <li>{@code DATE} — processing date (informational)</li>
 *   <li>{@code OUTPUT_DIR} — directory where PDF files are written (required)</li>
 *   <li>{@code TOKEN_SECRET} — HMAC-SHA256 secret, must be at least 32 bytes UTF-8 (required)</li>
 *   <li>{@code EVENT_ID} — optional; when provided only tickets for that event are processed</li>
 * </ul>
 */
public class TicketPdfJobPlugin implements BatchJobPlugin {

    private static final Logger log = LoggerFactory.getLogger(TicketPdfJobPlugin.class);

    private static final String JOB_NAME = "ticket-pdf-job";
    private static final String VERSION = "1.0.0";
    private static final int CHUNK_SIZE = 10;

    @Override
    public String getJobName() {
        return JOB_NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public Job configureJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext parentContext) {

        // Validate required parameters at configuration time via defaults —
        // actual parameter values are validated in TicketStepListener.beforeStep.
        log.info("Configuring ticket-pdf-job plugin v{}", VERSION);

        DataSource dataSource = parentContext.getBean(DataSource.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        JobParamsHolder holder = new JobParamsHolder();

        // Build the cursor reader with a placeholder SQL; the real SQL (with optional
        // EVENT_ID filter) is set in TicketStepListener.beforeStep before reader.open().
        String placeholderSql =
                "SELECT id, event_id, ticket_code, holder_name, event_name, event_location,"
                        + " seat, event_datetime, processed"
                        + " FROM event_tickets WHERE processed = FALSE ORDER BY id ASC";
        JdbcCursorItemReader<Ticket> reader =
                new JdbcCursorItemReader<Ticket>(dataSource, placeholderSql, new TicketRowMapper());
        reader.setName("ticketReader");
        reader.setSaveState(false);

        TicketDocumentProcessor processor = new TicketDocumentProcessor(holder);
        TicketFileItemWriter writer = new TicketFileItemWriter(holder, jdbc);
        TicketStepListener stepListener = new TicketStepListener(holder, reader);

        var step = new StepBuilder("ticketPdfStep", jobRepository)
                .<Ticket, TicketDocument>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(stepListener)
                .build();

        return new JobBuilder(JOB_NAME, jobRepository)
                .start(step)
                .build();
    }

    @Override
    public Map<String, String> getDefaultParameters() {
        return Map.of(
                "DATE", "2024-01-01",
                "OUTPUT_DIR", "./target/tickets",
                "TOKEN_SECRET", "change-me-this-secret-must-be-32-bytes",
                "EVENT_ID", "");
    }

    @Override
    public List<String> getRequiredDependencies() {
        return List.of("openpdf", "zxing");
    }

    @Override
    public JobMetadata getMetadata() {
        return new TicketPdfJobMetadata();
    }

    /** Named static inner class so the compiled {@code .class} file is discoverable. */
    public static class TicketPdfJobMetadata implements JobMetadata {

        @Override
        public String getDisplayName() {
            return "Event Ticket PDF Generation";
        }

        @Override
        public String getDescription() {
            return "Generates signed PDF tickets with embedded QR codes for all unprocessed"
                    + " event_tickets rows. Tracks generated files in generated_files table.";
        }

        @Override
        public String getAuthor() {
            return "fronzec";
        }

        @Override
        public List<String> getTags() {
            return List.of("ticket", "pdf", "qr");
        }

        @Override
        public Duration getEstimatedRuntime() {
            return Duration.ofSeconds(10);
        }
    }
}
