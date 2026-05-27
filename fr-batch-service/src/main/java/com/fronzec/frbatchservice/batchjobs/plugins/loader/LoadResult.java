/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

/** Outcome of a dynamic plugin load, unload, or reload operation. */
public record LoadResult(String jobName, String status, String message) {

  /** Status constants for consistent usage across the service. */
  public static final String LOADED = "LOADED";
  public static final String UNLOADED = "UNLOADED";
  public static final String FAILED = "FAILED";
}
