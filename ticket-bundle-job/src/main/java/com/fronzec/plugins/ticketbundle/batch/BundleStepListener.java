package com.fronzec.plugins.ticketbundle.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;

/**
 * Populates {@link BundleParamsHolder} from job parameters before the reader opens, and
 * closes the {@link ZipAccumulator} stream after the step (success or failure).
 *
 * <p>This is the mechanism for injecting job parameters without {@code @StepScope}: the
 * step listener reads {@code JobParameters} in {@code beforeStep} and populates the shared
 * holder before {@link JdbcCursorItemReader#open} is called.
 *
 * <p><strong>ZipOutputStream lifecycle</strong>: {@code afterStep} closes the accumulator's
 * stream in a {@code try/finally} block — guaranteeing that the temp ZIP is finalised and
 * released even when the step fails mid-chunk (addresses design Risk R6).
 *
 * <p><strong>ExecutionContext handoff</strong>: after closing the stream, {@code afterStep}
 * writes two keys to the <em>Job</em> ExecutionContext so {@link BundlePersistTasklet} (step 2)
 * can read them:
 * <ul>
 *   <li>{@code bundle.zip.temp.path} — absolute path of the temp ZIP (absent when zero items)</li>
 *   <li>{@code bundle.ticket.count} — number of PDFs zipped (0 for empty event)</li>
 * </ul>
 *
 * <p><em>Alternative (not implemented here)</em>: {@code ExecutionContextPromotionListener}
 * is the idiomatic Spring Batch way to promote step-scoped context keys to job scope after
 * a successful step. That avoids the tight coupling between this listener and the job context.
 * It is documented in {@link com.fronzec.plugins.ticketbundle.TicketBundleJobPlugin#configureJob}
 * as a learning note.
 */
public class BundleStepListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BundleStepListener.class);

    /** Job ExecutionContext key for the temporary ZIP file path. */
    static final String CTX_TEMP_PATH = "bundle.zip.temp.path";

    /** Job ExecutionContext key for the count of zipped PDFs. */
    static final String CTX_TICKET_COUNT = "bundle.ticket.count";

    private final BundleParamsHolder holder;
    private final JdbcCursorItemReader<GeneratedFileRow> reader;
    private final ZipAccumulator accumulator;
    private final ZipBundleItemWriter writer;

    /**
     * Constructs the listener.
     *
     * @param holder     shared parameter holder to populate in {@code beforeStep}
     * @param reader     the cursor reader whose SQL is configured in {@code beforeStep}
     * @param accumulator the ZIP accumulator to close in {@code afterStep}
     * @param writer     the item writer that needs the event ID before step open
     */
    public BundleStepListener(
            BundleParamsHolder holder,
            JdbcCursorItemReader<GeneratedFileRow> reader,
            ZipAccumulator accumulator,
            ZipBundleItemWriter writer) {
        this.holder = holder;
        this.reader = reader;
        this.accumulator = accumulator;
        this.writer = writer;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        var params = stepExecution.getJobParameters();

        String eventId = params.getString("EVENT_ID");
        String outputDir = params.getString("OUTPUT_DIR");

        holder.setEventId(eventId);
        holder.setOutputDir(outputDir);

        // Pass the numeric event ID to the writer so it can name the temp file.
        long numericEventId = Long.parseLong(eventId.trim());
        writer.setEventId(numericEventId);

        // Set the reader SQL with the literal event ID (safe: already validated as a
        // positive long by BundleJobParametersValidator before any step runs).
        reader.setSql(buildSql(eventId.trim()));

        log.info("Configured ticket-bundle-job for event_id={}", eventId);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // Close the ZipOutputStream in try/finally so the stream is always released,
        // even when the chunk step fails mid-way (prevents corrupt/locked temp files).
        try {
            accumulator.close();
        } catch (Exception e) {
            log.error("Failed to close ZIP accumulator after step", e);
        } finally {
            // Write handoff keys to the JOB ExecutionContext so step 2 can read them.
            var jobCtx = stepExecution.getJobExecution().getExecutionContext();
            jobCtx.putInt(CTX_TICKET_COUNT, accumulator.ticketCount());
            if (accumulator.isOpened() && accumulator.zipPath() != null) {
                jobCtx.putString(CTX_TEMP_PATH, accumulator.zipPath().toString());
            }
            log.info("Step finished: ticket_count={}, temp_zip={}",
                    accumulator.ticketCount(),
                    accumulator.isOpened() ? accumulator.zipPath() : "<none>");
        }
        return stepExecution.getExitStatus();
    }

    /**
     * Builds the reader SQL with the event ID literal-inlined.
     * The event ID is already validated as a positive long before this runs.
     *
     * @param eventId string representation of the numeric event ID
     * @return SQL string for the cursor reader
     */
    static String buildSql(String eventId) {
        return "SELECT gf.id, gf.ticket_id, gf.storage_path"
                + " FROM generated_files gf"
                + " JOIN event_tickets et ON gf.ticket_id = et.id"
                + " WHERE et.event_id = " + Long.parseLong(eventId)
                + " ORDER BY gf.ticket_id ASC";
    }
}
