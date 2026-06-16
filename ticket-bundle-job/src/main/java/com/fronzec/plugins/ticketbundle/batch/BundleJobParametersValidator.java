package com.fronzec.plugins.ticketbundle.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * Fail-fast pre-flight validation of the job parameters for {@code ticket-bundle-job}.
 *
 * <p>Registered on the {@code JobBuilder}, so it runs at job launch <strong>before any step
 * starts</strong> — an invalid {@code EVENT_ID} or {@code OUTPUT_DIR} produces a clean
 * {@link InvalidJobParametersException} rather than a mid-step failure.
 *
 * <p>Validations performed:
 * <ol>
 *   <li>{@code EVENT_ID} must be present and non-blank.</li>
 *   <li>{@code EVENT_ID} must be parseable as a positive {@code long} (&gt; 0).</li>
 *   <li>{@code OUTPUT_DIR} must be present and non-blank.</li>
 * </ol>
 */
public class BundleJobParametersValidator implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        String eventId = parameters.getString("EVENT_ID");

        if (eventId == null || eventId.isBlank()) {
            throw new InvalidJobParametersException("EVENT_ID is required");
        }

        long numericEventId;
        try {
            numericEventId = Long.parseLong(eventId.trim());
        } catch (NumberFormatException e) {
            throw new InvalidJobParametersException(
                    "EVENT_ID must be a numeric value, got: " + eventId);
        }

        if (numericEventId <= 0) {
            throw new InvalidJobParametersException(
                    "EVENT_ID must be a positive long, got: " + numericEventId);
        }

        String outputDir = parameters.getString("OUTPUT_DIR");
        if (outputDir == null || outputDir.isBlank()) {
            throw new InvalidJobParametersException(
                    "OUTPUT_DIR is required and must be non-blank");
        }
    }
}
