package com.fronzec.plugins.ticketbundle;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.plugins.ticketbundle.batch.BundleJobParametersValidator;
import com.fronzec.plugins.ticketbundle.batch.BundleParamsHolder;
import com.fronzec.plugins.ticketbundle.batch.BundlePersistTasklet;
import com.fronzec.plugins.ticketbundle.batch.BundleStepListener;
import com.fronzec.plugins.ticketbundle.batch.GeneratedFileRow;
import com.fronzec.plugins.ticketbundle.batch.GeneratedFileRowMapper;
import com.fronzec.plugins.ticketbundle.batch.ZipAccumulator;
import com.fronzec.plugins.ticketbundle.batch.ZipBundleItemWriter;
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
 * Spring Batch plugin that bundles all generated ticket PDFs for an event into a single
 * ZIP archive and records the bundle in the {@code generated_bundles} table.
 *
 * <p>Implemented as a shaded standalone JAR loaded dynamically by the host service.
 * No Spring annotations — instantiated via {@code getDeclaredConstructor().newInstance()}.
 *
 * <h3>Job parameters</h3>
 * <ul>
 *   <li>{@code EVENT_ID} — required; positive long identifying the event to bundle</li>
 *   <li>{@code OUTPUT_DIR} — required; directory where the output ZIP is written</li>
 *   <li>{@code DATE} — informational processing date</li>
 * </ul>
 *
 * <h3>Two-step flow</h3>
 * <ol>
 *   <li><strong>ticketBundleZipStep</strong> (chunk, size 20): reads {@code generated_files}
 *       joined through {@code event_tickets}, streams each PDF into a temp ZIP.</li>
 *   <li><strong>ticketBundlePersistStep</strong> (tasklet): uploads the ZIP via
 *       {@link com.fronzec.plugins.ticketbundle.storage.LocalFileStorage}, upserts
 *       {@code generated_bundles}, deletes the temp file.</li>
 * </ol>
 */
public class TicketBundleJobPlugin implements BatchJobPlugin {

    private static final Logger log = LoggerFactory.getLogger(TicketBundleJobPlugin.class);

    private static final String JOB_NAME = "ticket-bundle-job";
    private static final String VERSION = "1.0.0";

    /**
     * Chunk size for the ZIP assembly step.
     * Each item triggers one file-read + one ZIP-entry-write.
     * 20 balances commit overhead vs. memory (lighter work per item than the PDF job).
     */
    private static final int CHUNK_SIZE = 20;

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

        log.info("Configuring ticket-bundle-job plugin v{}", VERSION);

        // Obtain the DataSource from the host's ApplicationContext.
        DataSource dataSource = parentContext.getBean(DataSource.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Shared parameter holder — populated by BundleStepListener.beforeStep().
        BundleParamsHolder holder = new BundleParamsHolder();

        // Shared ZIP accumulator — opened by writer on first item, closed by listener.afterStep().
        ZipAccumulator accumulator = new ZipAccumulator();

        // ── Step 1 components ─────────────────────────────────────────────────────────────────
        // Placeholder SQL; the real SQL (with literal event_id) is set in BundleStepListener
        // .beforeStep() before reader.open() is called by Spring Batch.
        String placeholderSql =
                "SELECT gf.id, gf.ticket_id, gf.storage_path"
                        + " FROM generated_files gf"
                        + " JOIN event_tickets et ON gf.ticket_id = et.id"
                        + " WHERE et.event_id = 0 ORDER BY gf.ticket_id ASC";

        JdbcCursorItemReader<GeneratedFileRow> reader =
                new JdbcCursorItemReader<>(dataSource, placeholderSql, new GeneratedFileRowMapper());
        reader.setName("generatedFileReader");
        reader.setSaveState(false); // Avoid polluting step ctx with cursor position.

        ZipBundleItemWriter writer = new ZipBundleItemWriter(accumulator);

        BundleStepListener stepListener = new BundleStepListener(holder, reader, accumulator, writer);

        var step1 = new StepBuilder("ticketBundleZipStep", jobRepository)
                .<GeneratedFileRow, GeneratedFileRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .writer(writer)
                .listener(stepListener)
                .build();

        // ── Learning note ─────────────────────────────────────────────────────────────────────
        // The direct Job-ExecutionContext write in BundleStepListener.afterStep() is simple and
        // explicit. The idiomatic Spring Batch alternative is ExecutionContextPromotionListener:
        //
        //   var promotionListener = new ExecutionContextPromotionListener();
        //   promotionListener.setKeys(new String[]{
        //       BundleStepListener.CTX_TEMP_PATH, BundleStepListener.CTX_TICKET_COUNT});
        //   step1Builder.listener(promotionListener);
        //
        // That would promote step-scoped keys to job scope after a successful step, decoupling
        // steps from each other's job ctx. For pedagogical contrast both variants are shown here
        // and in BundleStepListener Javadoc. The direct write is used in production code because
        // it is explicit and avoids the need for the step to also write to its step-ctx first.
        // ─────────────────────────────────────────────────────────────────────────────────────

        // ── Step 2 components ─────────────────────────────────────────────────────────────────
        BundlePersistTasklet tasklet = new BundlePersistTasklet(holder, jdbc);

        var step2 = new StepBuilder("ticketBundlePersistStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();

        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new BundleJobParametersValidator())
                .start(step1)
                .next(step2)
                .build();
    }

    @Override
    public Map<String, String> getDefaultParameters() {
        // EVENT_ID default is blank: callers MUST supply a valid positive long.
        // The BundleJobParametersValidator rejects blank values fail-fast before steps run.
        return Map.of(
                "DATE", "2024-01-01",
                "OUTPUT_DIR", "./target/bundles",
                "EVENT_ID", "");
    }

    @Override
    public List<String> getRequiredDependencies() {
        // ZIP assembly uses java.util.zip (JDK built-in) — no third-party bundled deps.
        return List.of();
    }

    @Override
    public JobMetadata getMetadata() {
        return new TicketBundleJobMetadata();
    }

    /** Named static inner class so the compiled {@code .class} file is discoverable. */
    public static class TicketBundleJobMetadata implements JobMetadata {

        @Override
        public String getDisplayName() {
            return "Event Ticket Bundle ZIP";
        }

        @Override
        public String getDescription() {
            return "Bundles all generated ticket PDFs for an event into one ZIP archive and"
                    + " records the bundle in the generated_bundles table.";
        }

        @Override
        public String getAuthor() {
            return "fronzec";
        }

        @Override
        public List<String> getTags() {
            return List.of("ticket", "bundle", "zip");
        }

        @Override
        public Duration getEstimatedRuntime() {
            return Duration.ofSeconds(10);
        }
    }
}
