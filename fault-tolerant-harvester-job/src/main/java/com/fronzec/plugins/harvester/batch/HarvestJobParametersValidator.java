package com.fronzec.plugins.harvester.batch;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

/**
 * Fail-fast pre-flight validation of the job parameters for {@code fault-tolerant-harvester-job}.
 *
 * <p>Registered on the {@code JobBuilder}, so it runs at job launch <strong>before any step
 * starts</strong> — an invalid or missing {@code DATE} produces a clean
 * {@link InvalidJobParametersException} rather than a mid-step failure.
 *
 * <p>Validations performed:
 * <ol>
 *   <li>{@code DATE} must be present and non-blank (FTH-01-A).</li>
 *   <li>{@code DATE} must be parseable as a valid {@code yyyy-MM-dd} date (FTH-01-B).</li>
 * </ol>
 *
 * <p>{@code ATTEMPT_NUMBER} and {@code DESCRIPTION} are optional and are not validated here.
 *
 * @see com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin
 */
public class HarvestJobParametersValidator implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null) {
            throw new InvalidJobParametersException("Job parameters are required");
        }
        String date = parameters.getString("DATE");

        if (date == null || date.isBlank()) {
            throw new InvalidJobParametersException("DATE parameter is required");
        }

        try {
            LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidJobParametersException(
                    "DATE must be a valid date in yyyy-MM-dd format, got: " + date);
        }
    }
}
