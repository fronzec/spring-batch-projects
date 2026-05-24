/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.plugins.service;

/**
 * Thrown when an uploaded file fails JAR validation (wrong extension or missing
 * ZIP/PK magic bytes).
 */
public class InvalidJarException extends RuntimeException {

    public InvalidJarException(String message) {
        super(message);
    }
}
