package com.fronzec.plugins.ticketpdf.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link FileStorage} implementation that writes files to the local filesystem.
 *
 * <p>Computes a SHA-256 hex checksum of the written content and returns a {@link StoredFile} with
 * {@code storageType = "LOCAL"}.
 */
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path baseDir;

    public LocalFileStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public StoredFile write(String key, byte[] content) {
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(key);
            Files.write(target, content);
            String checksum = sha256Hex(content);
            return new StoredFile("LOCAL", target.toAbsolutePath().toString(), checksum, content.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file with key: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(baseDir.resolve(key));
        } catch (IOException e) {
            log.warn("Failed to delete file with key '{}': {}", key, e.getMessage());
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
