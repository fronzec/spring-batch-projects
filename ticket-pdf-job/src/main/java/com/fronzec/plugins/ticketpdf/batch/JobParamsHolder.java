package com.fronzec.plugins.ticketpdf.batch;

import java.nio.file.Path;

/**
 * Mutable holder for job parameters, populated by {@link TicketStepListener} before the
 * reader opens. All batch components receive a shared instance of this holder.
 *
 * <p>Because the plugin is instantiated without a Spring container, {@code @StepScope}
 * late-binding is unavailable. This holder is the replacement: the step listener reads
 * {@code JobParameters} in {@code beforeStep} and populates the holder before the reader's
 * {@code open()} is called.
 */
public class JobParamsHolder {

    private String outputDir;
    private String eventId;
    private String tokenSecret;
    private String date;

    /** The output directory as a resolved {@link Path}. Populated in {@code beforeStep}. */
    public Path resolvedOutputDir() {
        if (outputDir == null || outputDir.isBlank()) {
            throw new IllegalStateException("OUTPUT_DIR is not set in JobParamsHolder");
        }
        return Path.of(outputDir);
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Optional EVENT_ID parameter. {@code null} or blank means "all events".
     */
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    public void setTokenSecret(String tokenSecret) {
        this.tokenSecret = tokenSecret;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    /** {@code true} when EVENT_ID is present and non-blank. */
    public boolean hasEventId() {
        return eventId != null && !eventId.isBlank();
    }
}
