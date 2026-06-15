package com.fronzec.plugins.ticketpdf.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

class TicketJobParametersValidatorTest {

    @TempDir Path tempDir;

    private static final String VALID_SECRET = "unit-test-secret-please-use-32+bytes!";

    private final TicketJobParametersValidator validator = new TicketJobParametersValidator();

    private JobParameters params(String outputDir, String tokenSecret) {
        JobParametersBuilder b = new JobParametersBuilder();
        if (outputDir != null) {
            b.addString("OUTPUT_DIR", outputDir);
        }
        if (tokenSecret != null) {
            b.addString("TOKEN_SECRET", tokenSecret);
        }
        return b.toJobParameters();
    }

    @Test
    void validParameters_passValidation() {
        assertThatCode(() -> validator.validate(params(tempDir.toString(), VALID_SECRET)))
            .doesNotThrowAnyException();
    }

    @Test
    void blankTokenSecret_failsBeforeStep() {
        assertThatThrownBy(() -> validator.validate(params(tempDir.toString(), "   ")))
            .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void missingTokenSecret_failsBeforeStep() {
        assertThatThrownBy(() -> validator.validate(params(tempDir.toString(), null)))
            .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void shortTokenSecret_failsBeforeStep() {
        assertThatThrownBy(() -> validator.validate(params(tempDir.toString(), "too-short")))
            .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void missingOutputDir_failsBeforeStep() {
        assertThatThrownBy(() -> validator.validate(params(null, VALID_SECRET)))
            .isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void unwritableOutputDir_failsBeforeStep() throws IOException {
        // Create a regular file, then point OUTPUT_DIR at a path *under* it — not creatable.
        Path file = Files.createFile(tempDir.resolve("not-a-dir"));
        Path badDir = file.resolve("sub");

        assertThatThrownBy(() -> validator.validate(params(badDir.toString(), VALID_SECRET)))
            .isInstanceOf(InvalidJobParametersException.class);
    }
}
