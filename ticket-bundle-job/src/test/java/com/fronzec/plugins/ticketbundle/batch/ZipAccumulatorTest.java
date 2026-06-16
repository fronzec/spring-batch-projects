package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ZipAccumulator}, covering the reset/reuse lifecycle (FIX #6).
 */
class ZipAccumulatorTest {

    @TempDir
    Path tempDir;

    @Test
    void reset_afterOpenAddClose_clearsAllState() throws Exception {
        ZipAccumulator accumulator = new ZipAccumulator();

        // Simulate a first execution: open, add an entry, close.
        Path sourceFile = tempDir.resolve("ticket.pdf");
        Files.writeString(sourceFile, "%PDF-content");

        accumulator.lazyOpen(1L);
        accumulator.addEntry("1-ticket.pdf", sourceFile);
        Path firstPath = accumulator.zipPath();
        accumulator.close();

        assertThat(accumulator.isOpened()).isTrue();
        assertThat(accumulator.ticketCount()).isEqualTo(1);
        assertThat(firstPath).isNotNull();

        // Reset — simulates what BundleStepListener.beforeStep() does before each new execution.
        accumulator.reset();

        assertThat(accumulator.isOpened()).isFalse();
        assertThat(accumulator.ticketCount()).isEqualTo(0);
        assertThat(accumulator.zipPath()).isNull();
    }

    @Test
    void reset_thenLazyOpen_opensNewTempFile() throws Exception {
        ZipAccumulator accumulator = new ZipAccumulator();

        // First execution
        Path sourceFile = tempDir.resolve("first.pdf");
        Files.writeString(sourceFile, "%PDF-1");
        accumulator.lazyOpen(10L);
        accumulator.addEntry("1-first.pdf", sourceFile);
        Path firstPath = accumulator.zipPath();
        accumulator.close();

        // Reset and simulate a second execution
        accumulator.reset();

        Path sourceFile2 = tempDir.resolve("second.pdf");
        Files.writeString(sourceFile2, "%PDF-2");
        accumulator.lazyOpen(20L);
        accumulator.addEntry("1-second.pdf", sourceFile2);
        Path secondPath = accumulator.zipPath();
        accumulator.close();

        // The second run opened a distinct temp file — not the closed one from run 1.
        assertThat(secondPath).isNotNull();
        assertThat(secondPath).isNotEqualTo(firstPath);
        assertThat(accumulator.ticketCount()).isEqualTo(1);
        assertThat(accumulator.isOpened()).isTrue();
    }
}
