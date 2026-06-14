package com.fronzec.plugins.ticketpdf.storage;

/**
 * Abstraction for writing generated ticket PDF files.
 *
 * <p>The v1 implementation is {@link LocalFileStorage}. A future {@code S3FileStorage} is a
 * drop-in replacement requiring no changes to the reader, processor, or writer.
 */
public interface FileStorage {

    /**
     * Write {@code content} to the storage backend under the given {@code key}.
     *
     * @param key     filename or object key (e.g. {@code "5.pdf"})
     * @param content raw bytes to store
     * @return a {@link StoredFile} describing the stored artifact
     */
    StoredFile write(String key, byte[] content);

    /**
     * Remove the file identified by {@code key}. Best-effort: implementations should swallow
     * {@code IOException} and log a warning rather than propagating it.
     *
     * @param key filename or object key to delete
     */
    default void delete(String key) {}
}
