/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.audit;

import java.time.LocalDateTime;

/**
 * Immutable audit event capturing a single lifecycle operation.
 *
 * @param type      what kind of operation happened
 * @param jobName   the job definition name (may be {@code null} if not applicable)
 * @param userId    who performed the action (resolved from SecurityContext, or {@code "system"})
 * @param details   free-text description of what happened or why it failed
 * @param outcome   {@code "SUCCESS"} or {@code "FAILURE"}
 * @param timestamp when the event occurred
 */
public record AuditEvent(
    AuditEventType type,
    String jobName,
    String userId,
    String details,
    String outcome,
    LocalDateTime timestamp) {

  /** Outcome constants for consistency across all audit call sites. */
  public static final String SUCCESS = "SUCCESS";
  public static final String FAILURE = "FAILURE";

  /** Canonical constructor that normalizes defaults. */
  public AuditEvent {
    if (outcome == null || outcome.isBlank()) {
      outcome = SUCCESS;
    }
  }
}
