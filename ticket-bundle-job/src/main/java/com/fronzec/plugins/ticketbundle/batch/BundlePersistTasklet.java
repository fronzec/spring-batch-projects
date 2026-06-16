package com.fronzec.plugins.ticketbundle.batch;

import com.fronzec.plugins.ticketbundle.storage.LocalFileStorage;
import com.fronzec.plugins.ticketbundle.storage.StoredFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tasklet for step 2 of the ticket-bundle-job.
 *
 * <p>Reads the temp ZIP path and ticket count written to the Job ExecutionContext by
 * {@link BundleStepListener#afterStep}, uploads the ZIP via {@link LocalFileStorage},
 * and upserts one row in {@code generated_bundles} (DELETE + INSERT — portable across
 * H2 MODE=MySQL and MySQL; no dialect-specific {@code MERGE} or {@code ON DUPLICATE KEY}).
 *
 * <p><strong>Empty-event no-op</strong>: when {@code bundle.ticket.count == 0} (no rows
 * found for the event), the tasklet logs a warning and returns {@code FINISHED} without
 * uploading or inserting anything.
 *
 * <p><strong>Temp file cleanup</strong>: the temp ZIP is deleted in a {@code finally}
 * block so it is always removed, even when the JDBC upsert fails.
 *
 * <p><strong>Idempotency</strong>: the upload key {@code bundles/event-{id}.zip} is
 * deterministic, so re-uploading overwrites the previous file. The DELETE+INSERT replaces
 * the existing {@code generated_bundles} row. A re-run for the same event always converges
 * to exactly one zip file and one DB row.
 */
public class BundlePersistTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(BundlePersistTasklet.class);

    private static final String DELETE_SQL =
            "DELETE FROM generated_bundles WHERE event_id = ?";

    private static final String INSERT_SQL =
            "INSERT INTO generated_bundles"
                    + " (event_id, storage_type, storage_path, checksum_sha256,"
                    + "  file_size_bytes, ticket_count, status)"
                    + " VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED')";

    private final BundleParamsHolder holder;
    private final JdbcTemplate jdbc;

    /**
     * Constructs the tasklet.
     *
     * @param holder shared parameter holder (provides event ID and output directory)
     * @param jdbc   JDBC template bound to the DataSource from the parent context
     */
    public BundlePersistTasklet(BundleParamsHolder holder, JdbcTemplate jdbc) {
        this.holder = holder;
        this.jdbc = jdbc;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {

        // Read handoff keys from the JOB ExecutionContext (written by BundleStepListener).
        var jobCtx = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        int ticketCount = jobCtx.getInt(BundleStepListener.CTX_TICKET_COUNT, 0);
        String tempPath = jobCtx.containsKey(BundleStepListener.CTX_TEMP_PATH)
                ? jobCtx.getString(BundleStepListener.CTX_TEMP_PATH)
                : null;

        long eventId = Long.parseLong(holder.getEventId().trim());

        // EMPTY-EVENT NO-OP: step 1 found no rows; skip upload and insert.
        if (ticketCount == 0) {
            log.warn("no generated files for event {}; nothing to bundle", eventId);
            return RepeatStatus.FINISHED;
        }

        // HANDOFF GUARD: a non-zero ticket count without a temp path is a handoff failure —
        // step 1 wrote tickets but the ZIP path was never propagated to the job context.
        if (tempPath == null || tempPath.isBlank()) {
            throw new IllegalStateException(
                    "Missing job context key '" + BundleStepListener.CTX_TEMP_PATH
                            + "' for non-empty bundle (ticket_count=" + ticketCount + ")");
        }

        Path tempZip = Path.of(tempPath);
        try {
            // Read the temp ZIP into memory (single artifact — acceptable).
            // Note: a streaming upload would be the S3-era improvement for very large bundles.
            byte[] zipBytes = Files.readAllBytes(tempZip);

            // Upload via LocalFileStorage (output sandbox); key is deterministic for idempotency.
            LocalFileStorage storage = new LocalFileStorage(holder.resolvedOutputDir());
            String uploadKey = "bundles/event-" + eventId + ".zip";
            StoredFile stored = storage.write(uploadKey, zipBytes);

            log.info("Uploaded bundle for event_id={} -> {} ({} bytes, sha256={})",
                    eventId, stored.path(), stored.sizeBytes(), stored.checksum());

            // UPSERT: DELETE then INSERT (portable; no MERGE / ON DUPLICATE KEY).
            // Both statements run within the tasklet's chunk transaction — the DELETE is
            // rolled back if the INSERT fails, leaving no partial state.
            jdbc.update(DELETE_SQL, eventId);

            int inserted = jdbc.update(INSERT_SQL,
                    eventId,
                    stored.storageType(),
                    stored.path(),
                    stored.checksum(),
                    stored.sizeBytes(),
                    ticketCount);

            if (inserted != 1) {
                throw new IllegalStateException(
                        "Expected 1 generated_bundles insert for event_id=" + eventId
                                + " but affected " + inserted);
            }

            log.info("Recorded generated_bundles row: event_id={}, ticket_count={}, status=COMPLETED",
                    eventId, ticketCount);

        } finally {
            // Always delete the temp file — even when the JDBC step fails.
            try {
                Files.deleteIfExists(tempZip);
                log.debug("Deleted temp ZIP: {}", tempZip);
            } catch (Exception e) {
                log.warn("Failed to delete temp ZIP '{}': {}", tempZip, e.getMessage());
            }
        }

        return RepeatStatus.FINISHED;
    }
}
