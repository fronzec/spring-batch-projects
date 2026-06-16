package com.fronzec.plugins.harvester.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * Unit tests for {@link HarvestJobParametersValidator}.
 *
 * <p>Covers FTH-01-A (missing/blank DATE), FTH-01-B (unparseable DATE),
 * and FTH-01-C (valid params).
 */
class HarvestJobParametersValidatorTest {

    private static final String VALID_DATE = "2026-06-15";

    private final HarvestJobParametersValidator validator = new HarvestJobParametersValidator();

    private JobParameters params(String date) {
        JobParametersBuilder b = new JobParametersBuilder();
        if (date != null) {
            b.addString("DATE", date);
        }
        return b.toJobParameters();
    }

    // ── FTH-01-A: missing DATE ────────────────────────────────────────────────

    @Test
    void validate_missingDate_throws() {
        // DATE absent from parameters map
        assertThatThrownBy(() -> validator.validate(params(null)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("DATE");
    }

    @Test
    void validate_blankDate_throws() {
        // DATE present but whitespace-only
        assertThatThrownBy(() -> validator.validate(params("   ")))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("DATE");
    }

    // ── FTH-01-B: unparseable DATE ────────────────────────────────────────────

    @Test
    void validate_nonDateString_throws() {
        assertThatThrownBy(() -> validator.validate(params("not-a-date")))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void validate_partialDate_throws() {
        // Valid numbers but wrong format (missing day)
        assertThatThrownBy(() -> validator.validate(params("2026-06")))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void validate_dateWithTime_throws() {
        // ISO datetime — not a plain LocalDate
        assertThatThrownBy(() -> validator.validate(params("2026-06-15T10:00:00")))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    // ── FTH-01-C: valid params ────────────────────────────────────────────────

    @Test
    void validate_validDate_noException() {
        // DATE present and parseable — no ATTEMPT_NUMBER (optional)
        assertThatCode(() -> validator.validate(params(VALID_DATE)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_validDateWithAttemptNumber_noException() {
        // Both DATE and ATTEMPT_NUMBER present
        JobParameters paramsWithAttempt =
                new JobParametersBuilder()
                        .addString("DATE", VALID_DATE)
                        .addString("ATTEMPT_NUMBER", "1")
                        .toJobParameters();

        assertThatCode(() -> validator.validate(paramsWithAttempt))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_validDateWithOptionalDescription_noException() {
        // DESCRIPTION is optional and must not cause validation to fail
        JobParameters paramsWithDesc =
                new JobParametersBuilder()
                        .addString("DATE", VALID_DATE)
                        .addString("DESCRIPTION", "manual harvest run")
                        .toJobParameters();

        assertThatCode(() -> validator.validate(paramsWithDesc))
                .doesNotThrowAnyException();
    }
}
