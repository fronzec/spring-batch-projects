package com.fronzec.plugins.ticketbundle.batch;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.Chunk;

/**
 * Item writer for the ZIP assembly chunk step.
 *
 * <p>For each {@link GeneratedFileRow} in the chunk:
 * <ol>
 *   <li>Verifies the source PDF exists on disk — FAIL-FAST if missing.</li>
 *   <li>Lazily opens the {@link ZipAccumulator}'s temp ZIP on the first item.</li>
 *   <li>Streams the PDF bytes into a new ZIP entry named {@code <ticketId>-<filename>}.</li>
 * </ol>
 *
 * <p><strong>Source file reads</strong>: done directly via {@code java.nio} over the
 * absolute {@code storage_path} from the database row. The {@code LocalFileStorage} sandbox
 * is NOT used on the read side — that class is output-only (write/delete).
 *
 * <p><strong>ZIP stream lifecycle</strong>: this writer does NOT close the
 * {@link ZipAccumulator}. Closing happens in {@link BundleStepListener#afterStep}
 * inside a {@code try/finally} block, guaranteeing cleanup even on mid-chunk failure.
 */
public class ZipBundleItemWriter implements ItemWriter<GeneratedFileRow> {

    private static final Logger log = LoggerFactory.getLogger(ZipBundleItemWriter.class);

    private final ZipAccumulator accumulator;
    private long eventId;

    /**
     * Constructs a writer backed by the given accumulator.
     *
     * @param accumulator shared accumulator that owns the temp ZIP stream
     */
    public ZipBundleItemWriter(ZipAccumulator accumulator) {
        this.accumulator = accumulator;
    }

    /**
     * Sets the event ID used for naming the temp file on lazy open.
     * Called by {@link BundleStepListener#beforeStep} before the reader opens.
     *
     * @param eventId the event ID from job parameters
     */
    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    @Override
    public void write(Chunk<? extends GeneratedFileRow> chunk) throws Exception {
        for (GeneratedFileRow row : chunk) {
            Path sourcePath = Path.of(row.storagePath());

            // FAIL-FAST: a missing source file means the bundle would be incomplete.
            // Silently skipping is worse than a loud failure (v1 policy).
            // See design section 7: skip-and-count is the v2 enhancement via faultTolerant().
            if (!Files.exists(sourcePath)) {
                throw new IllegalStateException(
                        "source PDF missing for ticket id=" + row.ticketId()
                                + ": " + row.storagePath());
            }

            // Lazy-open the temp ZIP on the first item of the first chunk.
            accumulator.lazyOpen(eventId);

            // Entry name always prefixed with ticketId for determinism (resolves entry-name
            // collision when two tickets share the same filename).
            String entryName = row.ticketId() + "-" + sourcePath.getFileName();
            accumulator.addEntry(entryName, sourcePath);

            log.debug("Added ZIP entry '{}' for ticket id={}", entryName, row.ticketId());
        }
    }
}
