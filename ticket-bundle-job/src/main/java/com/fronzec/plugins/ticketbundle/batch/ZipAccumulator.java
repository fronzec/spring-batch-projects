package com.fronzec.plugins.ticketbundle.batch;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful accumulator that manages the lifecycle of a temporary ZIP file being built
 * during the chunk step.
 *
 * <p>The ZIP stream is opened lazily on the first call to {@link #lazyOpen(long)} and
 * must be closed exactly once by {@link BundleStepListener#afterStep} (in a {@code try/finally}
 * block), regardless of step success or failure.
 *
 * <p><strong>Thread safety</strong>: this class is NOT thread-safe. It is designed for
 * single-threaded Spring Batch step execution.
 */
public class ZipAccumulator {

    private static final Logger log = LoggerFactory.getLogger(ZipAccumulator.class);

    private Path zipPath;
    private ZipOutputStream zos;
    private int ticketCount;
    private boolean opened;

    /**
     * Lazily opens the temporary ZIP file on the first call. Subsequent calls are no-ops.
     *
     * @param eventId used to name the temp file for traceability
     * @throws IOException if the temp file cannot be created or the stream cannot be opened
     */
    public void lazyOpen(long eventId) throws IOException {
        if (!opened) {
            zipPath = Files.createTempFile("ticket-bundle-" + eventId + "-", ".zip");
            zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)));
            opened = true;
            log.debug("Opened temp ZIP at {}", zipPath);
        }
    }

    /**
     * Streams the bytes from {@code sourcePath} into a new ZIP entry named {@code entryName}.
     *
     * <p>Uses {@link Files#copy(Path, java.io.OutputStream)} for streaming copy (8 KB buffer)
     * rather than reading all bytes at once — keeping memory flat for large events.
     *
     * @param entryName the name of the ZIP entry (e.g. {@code "42-ticket.pdf"})
     * @param sourcePath the local file to read
     * @throws IOException if reading {@code sourcePath} or writing to the ZIP stream fails
     */
    public void addEntry(String entryName, Path sourcePath) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(sourcePath, zos);
        zos.closeEntry();
        ticketCount++;
    }

    /**
     * Closes the underlying {@link ZipOutputStream}, finalising the ZIP central directory.
     * Safe to call even if the accumulator was never opened (no-op).
     *
     * <p>Called from {@link BundleStepListener#afterStep} inside a {@code try/finally}
     * so the stream is closed even if the step fails mid-chunk.
     *
     * @throws IOException if closing the stream fails
     */
    public void close() throws IOException {
        if (opened && zos != null) {
            zos.close();
            log.debug("Closed temp ZIP at {}", zipPath);
        }
    }

    /**
     * Returns the path to the temporary ZIP file, or {@code null} if the accumulator has
     * not been opened yet (i.e. zero items were processed).
     *
     * @return temp ZIP path, or {@code null}
     */
    public Path zipPath() {
        return zipPath;
    }

    /**
     * Returns the number of ZIP entries written so far.
     *
     * @return ticket count
     */
    public int ticketCount() {
        return ticketCount;
    }

    /**
     * Returns {@code true} if {@link #lazyOpen(long)} has been called at least once.
     *
     * @return {@code true} if the ZIP stream has been opened
     */
    public boolean isOpened() {
        return opened;
    }
}
