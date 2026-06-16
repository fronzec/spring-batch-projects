package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;

/**
 * Unit tests for {@link ZipBundleItemWriter}.
 *
 * <p>Uses real temp files on disk (no mocks) and a real {@link ZipAccumulator}.
 */
class ZipBundleItemWriterTest {

    @TempDir Path tempDir;

    private static final long EVENT_ID = 99L;

    private Path createSourceFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void write_zipsAllItems_correctEntries() throws Exception {
        // Create 3 real source PDF-like files on disk
        Path file1 = createSourceFile("ticket1.pdf", "%PDF-content-1");
        Path file2 = createSourceFile("ticket2.pdf", "%PDF-content-2");
        Path file3 = createSourceFile("ticket3.pdf", "%PDF-content-3");

        ZipAccumulator accumulator = new ZipAccumulator();
        ZipBundleItemWriter writer = new ZipBundleItemWriter(accumulator);
        writer.setEventId(EVENT_ID);

        List<GeneratedFileRow> items = List.of(
                new GeneratedFileRow(1L, 10L, file1.toString()),
                new GeneratedFileRow(2L, 20L, file2.toString()),
                new GeneratedFileRow(3L, 30L, file3.toString()));

        writer.write(new Chunk<>(items));
        accumulator.close();

        assertThat(accumulator.ticketCount()).isEqualTo(3);
        assertThat(accumulator.zipPath()).isNotNull();

        // Open the produced zip and verify entries
        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(
                new FileInputStream(accumulator.zipPath().toFile()))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertThat(entryNames).containsExactlyInAnyOrder(
                "10-ticket1.pdf",
                "20-ticket2.pdf",
                "30-ticket3.pdf");
    }

    @Test
    void write_missingSourceFile_throwsIllegalStateException() throws Exception {
        Path nonExistent = tempDir.resolve("ghost.pdf");

        ZipAccumulator accumulator = new ZipAccumulator();
        ZipBundleItemWriter writer = new ZipBundleItemWriter(accumulator);
        writer.setEventId(EVENT_ID);

        List<GeneratedFileRow> items = List.of(
                new GeneratedFileRow(5L, 55L, nonExistent.toString()));

        assertThatThrownBy(() -> writer.write(new Chunk<>(items)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source PDF missing")
                .hasMessageContaining("55"); // ticketId in message
    }

    @Test
    void write_alwaysPrefixesTicketId_inEntryName() throws Exception {
        // Two source files with the SAME filename — entry names must be distinct via ticketId prefix
        Path file1 = createSourceFile("same-name.pdf", "%PDF-A");
        Path file2 = tempDir.resolve("subdir");
        Files.createDirectories(file2);
        Path file2Path = file2.resolve("same-name.pdf");
        Files.writeString(file2Path, "%PDF-B");

        ZipAccumulator accumulator = new ZipAccumulator();
        ZipBundleItemWriter writer = new ZipBundleItemWriter(accumulator);
        writer.setEventId(EVENT_ID);

        List<GeneratedFileRow> items = List.of(
                new GeneratedFileRow(1L, 100L, file1.toString()),
                new GeneratedFileRow(2L, 200L, file2Path.toString()));

        writer.write(new Chunk<>(items));
        accumulator.close();

        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(
                new FileInputStream(accumulator.zipPath().toFile()))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        // Both entries present, each with a distinct ticketId prefix
        assertThat(entryNames).containsExactlyInAnyOrder(
                "100-same-name.pdf",
                "200-same-name.pdf");
    }

    @Test
    void write_preservesSourceFileContent_inZipEntry() throws Exception {
        byte[] originalContent = "%PDF-hello-world".getBytes(StandardCharsets.UTF_8);
        Path sourceFile = tempDir.resolve("content-check.pdf");
        Files.write(sourceFile, originalContent);

        ZipAccumulator accumulator = new ZipAccumulator();
        ZipBundleItemWriter writer = new ZipBundleItemWriter(accumulator);
        writer.setEventId(EVENT_ID);

        writer.write(new Chunk<>(List.of(new GeneratedFileRow(1L, 77L, sourceFile.toString()))));
        accumulator.close();

        // Extract the entry bytes and compare
        byte[] entryBytes;
        try (ZipInputStream zis = new ZipInputStream(
                new FileInputStream(accumulator.zipPath().toFile()))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            entryBytes = zis.readAllBytes();
        }

        assertThat(entryBytes).isEqualTo(originalContent);
    }
}
