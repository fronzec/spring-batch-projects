package com.fronzec.plugins.ticketbundle.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/**
 * Unit tests for {@link BundleJobParametersValidator}.
 */
class BundleJobParametersValidatorTest {

    private static final String VALID_EVENT_ID = "42";
    private static final String VALID_OUTPUT_DIR = "/tmp/bundles";

    private final BundleJobParametersValidator validator = new BundleJobParametersValidator();

    private JobParameters params(String eventId, String outputDir) {
        JobParametersBuilder b = new JobParametersBuilder();
        if (eventId != null) {
            b.addString("EVENT_ID", eventId);
        }
        if (outputDir != null) {
            b.addString("OUTPUT_DIR", outputDir);
        }
        return b.toJobParameters();
    }

    @Test
    void validate_missingEventId_throws() {
        assertThatThrownBy(() -> validator.validate(params(null, VALID_OUTPUT_DIR)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("EVENT_ID");
    }

    @Test
    void validate_blankEventId_throws() {
        assertThatThrownBy(() -> validator.validate(params("   ", VALID_OUTPUT_DIR)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("EVENT_ID");
    }

    @Test
    void validate_zeroEventId_throws() {
        assertThatThrownBy(() -> validator.validate(params("0", VALID_OUTPUT_DIR)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void validate_negativeEventId_throws() {
        assertThatThrownBy(() -> validator.validate(params("-5", VALID_OUTPUT_DIR)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void validate_nonNumericEventId_throws() {
        assertThatThrownBy(() -> validator.validate(params("not-a-number", VALID_OUTPUT_DIR)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void validate_missingOutputDir_throws() {
        assertThatThrownBy(() -> validator.validate(params(VALID_EVENT_ID, null)))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("OUTPUT_DIR");
    }

    @Test
    void validate_blankOutputDir_throws() {
        assertThatThrownBy(() -> validator.validate(params(VALID_EVENT_ID, "  ")))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("OUTPUT_DIR");
    }

    @Test
    void validate_validParams_noException() {
        assertThatCode(() -> validator.validate(params(VALID_EVENT_ID, VALID_OUTPUT_DIR)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_largeEventId_passes() {
        assertThatCode(() -> validator.validate(params("9999999999", VALID_OUTPUT_DIR)))
                .doesNotThrowAnyException();
    }
}
