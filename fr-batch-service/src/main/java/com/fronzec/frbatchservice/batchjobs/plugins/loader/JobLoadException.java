/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

/** Thrown when a dynamic plugin JAR fails to load or configure. */
public class JobLoadException extends RuntimeException {

  public JobLoadException(String message) {
    super(message);
  }

  public JobLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
