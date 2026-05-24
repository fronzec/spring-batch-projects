/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.plugins.service;

/**
 * Thrown when a JAR upload attempts to create a job definition that already
 * exists for the given job name.
 */
public class DuplicateJobDefinitionException extends RuntimeException {

    public DuplicateJobDefinitionException(String message) {
        super(message);
    }
}
