package com.fronzec.plugins.ticketpdf.storage;

/**
 * Value returned by {@link FileStorage#write} describing the stored artifact.
 *
 * @param storageType backend identifier, e.g. {@code "LOCAL"} or {@code "S3"}
 * @param path        absolute path or URI to the stored file
 * @param checksum    hex-encoded SHA-256 of the written bytes
 * @param sizeBytes   exact byte count of the written content
 */
public record StoredFile(String storageType, String path, String checksum, long sizeBytes) {}
