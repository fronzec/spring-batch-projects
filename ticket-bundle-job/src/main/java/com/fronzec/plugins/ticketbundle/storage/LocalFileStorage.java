package com.fronzec.plugins.ticketbundle.storage;

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
 *
 * <p><strong>Output-only</strong>: this class is used exclusively to <em>write</em> the output
 * ZIP bundle. Source PDF files are read directly via {@code java.nio} over their absolute
 * {@code storage_path} — they are NOT routed through this class (which would trip the
 * {@link #resolveSafePath} sandbox). See {@code ZipBundleItemWriter} for the read side.
 */
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path baseDir;

    /**
     * Constructs a {@code LocalFileStorage} rooted at {@code baseDir}.
     *
     * @param baseDir the base directory under which all keys are resolved
     */
    public LocalFileStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public StoredFile write(String key, byte[] content) {
        Path target = resolveSafePath(key);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, content);
            String checksum = sha256Hex(content);
            return new StoredFile("LOCAL", target.toString(), checksum, content.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file with key: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafePath(key));
        } catch (IOException e) {
            log.warn("Failed to delete file with key '{}': {}", key, e.getMessage());
        }
    }

    /**
     * Resolve {@code key} against the base directory, rejecting any key that would escape it
     * (e.g. {@code ../} or an absolute path). Prevents path-traversal writes/deletes.
     *
     * @param key the storage key to resolve
     * @return the safe, normalized absolute path
     * @throws IllegalArgumentException if the key escapes the base directory
     */
    private Path resolveSafePath(String key) {
        Path base = baseDir.toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Illegal storage key (path traversal): " + key);
        }
        return target;
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
