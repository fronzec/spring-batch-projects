package com.fronzec.frbatchservice.batchjobs.plugins;

/** Thrown when a {@link com.fronzec.api.BatchJobPlugin} fails to register at application startup. */
public class PluginRegistrationException extends RuntimeException {

    public PluginRegistrationException(String message) {
        super(message);
    }

    public PluginRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
