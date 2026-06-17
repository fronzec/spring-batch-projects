package com.fronzec.plugins.partitionedharvester.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * Unit tests for {@link PartitionedHarvesterJobParametersValidator}.
 *
 * <p>Mirrors the pattern from {@code HarvestJobParametersValidatorTest}.
 * Covers: null parameters, GRID_SIZE/CHUNK_SIZE that differ from the build-time constants
 * (rejected — overrides cannot take effect in this single-JVM plugin), missing parameters
 * (valid — defaults applied at runtime), and the build-time defaults themselves (valid),
 * per REQ-08 and SC-08.x.
 */
class PartitionedHarvesterJobParametersValidatorTest {

    private final PartitionedHarvesterJobParametersValidator validator =
            new PartitionedHarvesterJobParametersValidator(4L, 100L);

    private JobParameters params(Long gridSize, Long chunkSize) {
        JobParametersBuilder b = new JobParametersBuilder();
        if (gridSize != null) {
            b.addLong("GRID_SIZE", gridSize);
        }
        if (chunkSize != null) {
            b.addLong("CHUNK_SIZE", chunkSize);
        }
        return b.toJobParameters();
    }

    // ── Null parameters guard ─────────────────────────────────────────────────

    @Test
    void validate_nullParameters_throws() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidJobParametersException.class);
    }

    // ── SC-08.1 — GRID_SIZE = 0 is rejected ──────────────────────────────────

    @Test
    void validate_gridSizeZero_throws() {
        assertThatThrownBy(() -> validator.validate(params(0L, 100L)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("GRID_SIZE");
    }

    @Test
    void validate_gridSizeNegative_throws() {
        assertThatThrownBy(() -> validator.validate(params(-1L, 100L)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("GRID_SIZE");
    }

    // ── SC-08.2 — CHUNK_SIZE = 0 or negative is rejected ─────────────────────

    @Test
    void validate_chunkSizeZero_throws() {
        assertThatThrownBy(() -> validator.validate(params(4L, 0L)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("CHUNK_SIZE");
    }

    @Test
    void validate_chunkSizeNegative_throws() {
        assertThatThrownBy(() -> validator.validate(params(4L, -1L)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("CHUNK_SIZE");
    }

    // ── SC-08.3 — Only the build-time defaults are accepted ──────────────────

    @Test
    void validate_defaultValues_noException() {
        // Build-time constants: GRID_SIZE=4, CHUNK_SIZE=100
        assertThatCode(() -> validator.validate(params(4L, 100L)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_nonDefaultGridSize_throws() {
        // A GRID_SIZE override cannot take effect (build-time constraint) → rejected loudly
        assertThatThrownBy(() -> validator.validate(params(8L, 100L)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("GRID_SIZE");
    }

    @Test
    void validate_nonDefaultChunkSize_throws() {
        // A CHUNK_SIZE override cannot take effect (build-time constraint) → rejected loudly
        assertThatThrownBy(() -> validator.validate(params(4L, 50L)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("CHUNK_SIZE");
    }

    // ── Missing parameters — treated as valid (defaults applied at launch) ────

    @Test
    void validate_missingGridSize_noException() {
        // Only CHUNK_SIZE provided; GRID_SIZE absent → valid (default will be used)
        assertThatCode(() -> validator.validate(params(null, 100L)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_missingChunkSize_noException() {
        // Only GRID_SIZE provided; CHUNK_SIZE absent → valid (default will be used)
        assertThatCode(() -> validator.validate(params(4L, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_noParametersAtAll_noException() {
        // Both absent → valid (all defaults apply)
        assertThatCode(() -> validator.validate(params(null, null)))
                .doesNotThrowAnyException();
    }
}
