package com.fronzec.plugins.ticketbundle.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir Path tempDir;

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void write_returnsStoredFile_withLocalTypeAndChecksum() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(tempDir);
        byte[] content = "hello bundle zip".getBytes(StandardCharsets.UTF_8);
        String key = "bundle-test.zip";

        StoredFile result = storage.write(key, content);

        Path expectedPath = tempDir.resolve(key);
        assertThat(expectedPath).exists();
        assertThat(Files.readAllBytes(expectedPath)).isEqualTo(content);

        // storageType must be LOCAL
        assertThat(result.storageType()).isEqualTo("LOCAL");

        // path must end with the key filename
        assertThat(result.path()).endsWith(key);

        // checksum must be a valid 64-char hex string matching the content
        assertThat(result.checksum()).hasSize(64);
        assertThat(result.checksum()).isEqualTo(sha256Hex(content));

        // sizeBytes must match content length
        assertThat(result.sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void write_createsSubdirectory() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(tempDir);
        byte[] content = "sub dir content".getBytes(StandardCharsets.UTF_8);

        StoredFile result = storage.write("sub/file.zip", content);

        assertThat(Path.of(result.path())).exists();
        assertThat(Files.readAllBytes(Path.of(result.path()))).isEqualTo(content);
    }

    @Test
    void resolveSafePath_rejectsTraversal() {
        LocalFileStorage storage = new LocalFileStorage(tempDir);

        assertThatThrownBy(
                () -> storage.write("../escape.zip", "x".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path traversal");
    }

    @Test
    void delete_removesFile() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(tempDir);
        byte[] content = "to be deleted".getBytes(StandardCharsets.UTF_8);
        storage.write("remove-me.zip", content);

        Path filePath = tempDir.resolve("remove-me.zip");
        assertThat(filePath).exists();

        storage.delete("remove-me.zip");

        assertThat(filePath).doesNotExist();
    }

    @Test
    void delete_onNonExistentKey_doesNotThrow() {
        LocalFileStorage storage = new LocalFileStorage(tempDir);

        assertThatCode(() -> storage.delete("ghost.zip")).doesNotThrowAnyException();
    }

    @Test
    void write_rejectsAbsoluteKey() {
        LocalFileStorage storage = new LocalFileStorage(tempDir);

        // An absolute key that starts outside baseDir must be rejected
        assertThatThrownBy(
                () -> storage.write("/etc/passwd", "x".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
