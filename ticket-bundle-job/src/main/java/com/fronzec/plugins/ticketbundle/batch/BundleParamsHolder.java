package com.fronzec.plugins.ticketbundle.batch;

import java.nio.file.Path;

/**
 * Mutable holder for job parameters, populated by {@link BundleStepListener} before the
 * reader opens. All batch components share a single instance of this holder.
 *
 * <p>Because the plugin is instantiated without a Spring container, {@code @StepScope}
 * late-binding is unavailable. This holder is the replacement: the step listener reads
 * {@code JobParameters} in {@code beforeStep} and populates the holder before the reader's
 * {@code open()} is called.
 */
public class BundleParamsHolder {

    private String eventId;
    private String outputDir;

    /**
     * Returns the output directory as a resolved {@link Path}. Must be called after
     * {@code beforeStep} populates the holder.
     *
     * @return resolved output directory path
     * @throws IllegalStateException if {@code OUTPUT_DIR} has not been set
     */
    public Path resolvedOutputDir() {
        if (outputDir == null || outputDir.isBlank()) {
            throw new IllegalStateException("OUTPUT_DIR is not set in BundleParamsHolder");
        }
        return Path.of(outputDir);
    }

    /**
     * Returns the raw EVENT_ID string as provided in job parameters.
     *
     * @return event ID string, or {@code null} if not yet set
     */
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Returns {@code true} when {@code EVENT_ID} is present and non-blank.
     *
     * @return {@code true} if event ID is set
     */
    public boolean hasEventId() {
        return eventId != null && !eventId.isBlank();
    }
}
