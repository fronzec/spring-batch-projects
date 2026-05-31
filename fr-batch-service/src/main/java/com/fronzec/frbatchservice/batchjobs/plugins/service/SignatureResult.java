/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.service;

import java.util.List;

/**
 * Immutable result of JAR signature verification.
 *
 * @param valid    whether the JAR signature is considered acceptable under the current mode
 * @param signer   the distinguished name or CN of the signer (may be {@code null} for unsigned JARs)
 * @param warnings human-readable descriptions of any issues found
 */
public record SignatureResult(boolean valid, String signer, List<String> warnings) {

  /** Canonical constructor that makes {@code warnings} an unmodifiable copy. */
  public SignatureResult {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /**
   * Convenience factory for an unsigned-JAR result.
   *
   * @param valid whether the current signature mode accepts unsigned JARs
   * @return a result with the standard "no signature" warning
   */
  public static SignatureResult unsigned(boolean valid) {
    return new SignatureResult(valid, null, List.of("JAR is not signed — no signature files found in META-INF"));
  }
}
