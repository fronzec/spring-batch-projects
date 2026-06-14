package com.fronzec.plugins.ticketpdf.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    void write_createsFile_andReturnsCorrectStoredFile() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(tempDir);
        byte[] content = "hello ticket pdf".getBytes(StandardCharsets.UTF_8);
        String key = "ticket-42.pdf";

        StoredFile result = storage.write(key, content);

        // File must exist at the returned path
        Path expectedPath = tempDir.resolve(key);
        assertThat(expectedPath).exists();
        assertThat(Files.readAllBytes(expectedPath)).isEqualTo(content);

        // storageType must be LOCAL
        assertThat(result.storageType()).isEqualTo("LOCAL");

        // path must be the absolute path
        assertThat(result.path()).isEqualTo(expectedPath.toAbsolutePath().toString());

        // checksum must match independently computed SHA-256
        assertThat(result.checksum()).isEqualTo(sha256Hex(content));

        // sizeBytes must match content length
        assertThat(result.sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void write_createsParentDirectories_whenBaseDirectoryDoesNotExist() throws IOException {
        Path nestedDir = tempDir.resolve("sub/dir");
        LocalFileStorage storage = new LocalFileStorage(nestedDir);
        byte[] content = "nested content".getBytes(StandardCharsets.UTF_8);

        StoredFile result = storage.write("file.pdf", content);

        assertThat(Path.of(result.path())).exists();
        assertThat(Files.readAllBytes(Path.of(result.path()))).isEqualTo(content);
    }

    @Test
    void write_withEmptyBytes_returnsStoredFileWithZeroSize() {
        LocalFileStorage storage = new LocalFileStorage(tempDir);
        byte[] content = new byte[0];

        StoredFile result = storage.write("empty.pdf", content);

        assertThat(result.sizeBytes()).isZero();
        assertThat(result.checksum()).isEqualTo(sha256Hex(content));
    }

    @Test
    void delete_removesExistingFile() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(tempDir);
        byte[] content = "to be deleted".getBytes(StandardCharsets.UTF_8);
        storage.write("delete-me.pdf", content);

        Path filePath = tempDir.resolve("delete-me.pdf");
        assertThat(filePath).exists();

        storage.delete("delete-me.pdf");

        assertThat(filePath).doesNotExist();
    }

    @Test
    void delete_onNonExistentKey_doesNotThrow() {
        LocalFileStorage storage = new LocalFileStorage(tempDir);

        assertThatCode(() -> storage.delete("ghost.pdf")).doesNotThrowAnyException();
    }
}
