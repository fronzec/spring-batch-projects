package com.fronzec.plugins.ticketpdf.batch;

import com.fronzec.plugins.ticketpdf.domain.TicketDocument;
import com.fronzec.plugins.ticketpdf.storage.FileStorage;
import com.fronzec.plugins.ticketpdf.storage.LocalFileStorage;
import com.fronzec.plugins.ticketpdf.storage.StoredFile;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes each {@link TicketDocument} to the file system via {@link FileStorage}, then records
 * a row in {@code generated_files} and flips {@code event_tickets.processed = TRUE}.
 *
 * <p>Ordering: file FIRST, DB SECOND. The file write is not transactional, but the DB
 * writes ARE within the chunk transaction (JPA-managed). On DB failure, a best-effort
 * {@link FileStorage#delete} is attempted before re-throwing so the orphan is minimized;
 * idempotent re-run overwrites by key ({@code ticketId.pdf}).
 */
public class TicketFileItemWriter implements ItemWriter<TicketDocument> {

    private static final Logger log = LoggerFactory.getLogger(TicketFileItemWriter.class);

    private static final String INSERT_GENERATED_FILE =
            "INSERT INTO generated_files"
                    + " (ticket_id, storage_type, storage_path, checksum_sha256, file_size_bytes)"
                    + " VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_PROCESSED =
            "UPDATE event_tickets SET processed = TRUE WHERE id = ?";

    private final JobParamsHolder holder;
    private final JdbcTemplate jdbc;

    /** Lazily-initialized once OUTPUT_DIR is known (populated by {@link TicketStepListener}). */
    private FileStorage storage;

    public TicketFileItemWriter(JobParamsHolder holder, JdbcTemplate jdbc) {
        this.holder = holder;
        this.jdbc = jdbc;
    }

    @Override
    public void write(Chunk<? extends TicketDocument> chunk) throws Exception {
        FileStorage fs = getStorage();
        List<? extends TicketDocument> items = chunk.getItems();

        for (TicketDocument doc : items) {
            String key = doc.ticketId() + ".pdf";
            StoredFile sf = null;
            try {
                // 1. Write file (not transactional)
                sf = fs.write(key, doc.pdfBytes());

                // 2. Insert generated_files row (within chunk tx)
                int inserted = jdbc.update(INSERT_GENERATED_FILE,
                        doc.ticketId(),
                        sf.storageType(),
                        sf.path(),
                        sf.checksum(),
                        sf.sizeBytes());
                if (inserted != 1) {
                    throw new IllegalStateException(
                            "Expected 1 generated_files insert for ticket id=" + doc.ticketId()
                                    + " but affected " + inserted);
                }

                // 3. Mark ticket as processed (within chunk tx)
                int updated = jdbc.update(UPDATE_PROCESSED, doc.ticketId());
                if (updated != 1) {
                    throw new IllegalStateException(
                            "Expected to mark 1 ticket processed for id=" + doc.ticketId()
                                    + " but affected " + updated);
                }

                log.debug("Written ticket id={} -> {}", doc.ticketId(), sf.path());
            } catch (Exception e) {
                // Best-effort cleanup: try to remove the orphan file
                if (sf != null) {
                    try {
                        fs.delete(key);
                        log.warn("Deleted orphan file '{}' after DB failure for ticket id={}",
                                key, doc.ticketId());
                    } catch (Exception ignored) {
                        log.warn("Could not delete orphan file '{}': {}", key, ignored.getMessage());
                    }
                }
                throw e; // fail-fast: chunk rolls back
            }
        }
    }

    private FileStorage getStorage() {
        if (storage == null) {
            storage = new LocalFileStorage(holder.resolvedOutputDir());
        }
        return storage;
    }
}
