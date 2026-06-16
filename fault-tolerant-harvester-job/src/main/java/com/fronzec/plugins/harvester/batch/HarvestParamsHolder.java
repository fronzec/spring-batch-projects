package com.fronzec.plugins.harvester.batch;

/**
 * Mutable parameter holder populated by {@link HarvestStepListener#beforeStep} and consumed
 * by batch components that need job parameters without relying on {@code @StepScope}.
 *
 * <p>The holder is created once inside {@code configureJob} and shared across components;
 * {@code beforeStep} fills it before any reader/processor/writer method is called.
 */
public class HarvestParamsHolder {

    private String date;
    private String attemptNumber;

    /**
     * Returns the {@code DATE} job parameter (yyyy-MM-dd).
     *
     * @return date string
     */
    public String getDate() {
        return date;
    }

    /**
     * Sets the {@code DATE} job parameter.
     *
     * @param date value from {@code JobParameters}
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * Returns the {@code ATTEMPT_NUMBER} job parameter.
     *
     * @return attempt number string, or {@code null} if not supplied
     */
    public String getAttemptNumber() {
        return attemptNumber;
    }

    /**
     * Sets the {@code ATTEMPT_NUMBER} job parameter.
     *
     * @param attemptNumber value from {@code JobParameters}
     */
    public void setAttemptNumber(String attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
}
