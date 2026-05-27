/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

/**
 * Thrown when an unload is blocked because the job has running executions and {@code force}
 * was not requested. Maps to HTTP 409 Conflict in the controller layer.
 */
public class JobUnloadConflictException extends RuntimeException {

  public JobUnloadConflictException(String message) {
    super(message);
  }

  public JobUnloadConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
