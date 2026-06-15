package com.fronzec.plugins.ticketpdf.batch;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * Fail-fast pre-flight validation of the job parameters.
 *
 * <p>Registered on the {@code JobBuilder}, so it runs at job launch <strong>before any step
 * starts</strong> — an invalid {@code TOKEN_SECRET} or {@code OUTPUT_DIR} produces a clean
 * {@link InvalidJobParametersException} instead of a mid-step failure once tickets are already
 * being processed.
 */
public class TicketJobParametersValidator implements JobParametersValidator {

    /** HMAC-SHA256 key-strength floor; mirrors the guard in HmacTokenService. */
    private static final int MIN_SECRET_BYTES = 32;

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        String tokenSecret = parameters.getString("TOKEN_SECRET");
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new InvalidJobParametersException("TOKEN_SECRET is required and must not be blank");
        }
        if (tokenSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new InvalidJobParametersException(
                    "TOKEN_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HMAC-SHA256 strength");
        }

        String outputDir = parameters.getString("OUTPUT_DIR");
        if (outputDir == null || outputDir.isBlank()) {
            throw new InvalidJobParametersException("OUTPUT_DIR is required and must not be blank");
        }
        Path dir = Path.of(outputDir);
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new InvalidJobParametersException(
                    "OUTPUT_DIR is not creatable: " + outputDir + " (" + e.getMessage() + ")");
        }
        if (!Files.isWritable(dir)) {
            throw new InvalidJobParametersException("OUTPUT_DIR is not writable: " + outputDir);
        }
    }
}
