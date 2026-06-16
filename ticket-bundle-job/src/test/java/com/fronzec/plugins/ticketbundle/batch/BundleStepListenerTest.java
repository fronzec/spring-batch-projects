package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;

/**
 * Unit tests for {@link BundleStepListener#afterStep}, covering the ZIP close-failure
 * path (FIX #4): when accumulator.close() throws, afterStep must return ExitStatus.FAILED
 * and must NOT write CTX_TEMP_PATH to the job ExecutionContext.
 */
class BundleStepListenerTest {

    @TempDir
    Path tempDir;

    /**
     * Test double: a ZipAccumulator whose close() always throws IOException.
     * Extends ZipAccumulator directly because no methods are final.
     */
    private static class ThrowingZipAccumulator extends ZipAccumulator {
        private final Path stubbedZipPath;

        ThrowingZipAccumulator(Path stubbedZipPath) {
            this.stubbedZipPath = stubbedZipPath;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("simulated ZIP finalization failure");
        }

        @Override
        public Path zipPath() {
            return stubbedZipPath;
        }

        @Override
        public boolean isOpened() {
            return true;
        }

        @Override
        public int ticketCount() {
            return 3;
        }
    }

    private StepExecution buildStepExecution() {
        JobParameters params = new JobParametersBuilder()
                .addString("EVENT_ID", "42")
                .addString("OUTPUT_DIR", tempDir.toString())
                .toJobParameters();
        JobInstance instance = new JobInstance(1L, "ticket-bundle-job");
        JobExecution jobExecution = new JobExecution(1L, instance, params);
        return new StepExecution("ticketBundleZipStep", jobExecution);
    }

    @Test
    void afterStep_closeFailure_returnsFailed_andDoesNotWriteTempPath() throws Exception {
        // Create a real file on disk so the best-effort delete in afterStep can run without NPE.
        Path fakeZip = tempDir.resolve("fake-bundle.zip");
        Files.writeString(fakeZip, "fake zip content");

        ThrowingZipAccumulator throwingAccumulator = new ThrowingZipAccumulator(fakeZip);

        // BundleStepListener wires the accumulator; reader and writer can be null here
        // because beforeStep is not called — we exercise afterStep only.
        BundleParamsHolder holder = new BundleParamsHolder();
        BundleStepListener listener = new BundleStepListener(holder, null, throwingAccumulator, null);

        StepExecution stepExecution = buildStepExecution();
        ExitStatus result = listener.afterStep(stepExecution);

        // Must report FAILED so Spring Batch does not proceed to step 2.
        assertThat(result.getExitCode()).isEqualTo("FAILED");
        assertThat(result.getExitDescription()).contains("ZIP finalization failure");

        // CTX_TEMP_PATH must NOT have been written — step 2 must not pick up a corrupt bundle.
        ExecutionContext jobCtx = stepExecution.getJobExecution().getExecutionContext();
        assertThat(jobCtx.containsKey(BundleStepListener.CTX_TEMP_PATH)).isFalse();

        // The orphaned temp file should have been deleted by the best-effort cleanup.
        assertThat(fakeZip).doesNotExist();
    }

    @Test
    void afterStep_successPath_writesBothHandoffKeys() throws Exception {
        // Use a real accumulator that is already in "opened with 2 entries" state via reset+open.
        ZipAccumulator accumulator = new ZipAccumulator();
        Path sourceFile = tempDir.resolve("entry.pdf");
        Files.writeString(sourceFile, "%PDF-test", StandardCharsets.UTF_8);
        accumulator.lazyOpen(42L);
        accumulator.addEntry("42-entry.pdf", sourceFile);
        // Do NOT close yet — the listener's afterStep will close it.

        BundleParamsHolder holder = new BundleParamsHolder();
        BundleStepListener listener = new BundleStepListener(holder, null, accumulator, null);

        StepExecution stepExecution = buildStepExecution();
        // Default exit status for a step that completes without errors
        stepExecution.setExitStatus(ExitStatus.COMPLETED);

        ExitStatus result = listener.afterStep(stepExecution);

        assertThat(result).isEqualTo(ExitStatus.COMPLETED);

        ExecutionContext jobCtx = stepExecution.getJobExecution().getExecutionContext();
        assertThat(jobCtx.containsKey(BundleStepListener.CTX_TEMP_PATH)).isTrue();
        assertThat(jobCtx.getInt(BundleStepListener.CTX_TICKET_COUNT, -1)).isEqualTo(1);
    }
}
