/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.audit;

import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobExecutionAuditRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Synchronous audit trail backed by SLF4J structured logging with MDC correlation
 * IDs for request tracing.
 *
 * <p>Design decision (Phase 5): audit is synchronous — simple, no external infra
 * dependency, and reliable. All lifecycle events (upload, load, unload, delete,
 * enable, disable, approve, reject) are captured here.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final JobExecutionAuditRepository auditRepository;

  public AuditService(JobExecutionAuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  /**
   * Logs a structured audit event with MDC correlation ID.
   *
   * <p>A new UUID correlation ID is attached to the current thread's MDC for the
   * duration of this call, then cleared. The event is written to the application
   * log in a structured format suitable for log-aggregation tooling.
   */
  public void logEvent(AuditEvent event) {
    startCorrelation();
    try {
      log.info(
          "AUDIT | type={} | job={} | user={} | outcome={} | details={} | timestamp={}",
          event.type(),
          coalesce(event.jobName(), "-"),
          coalesce(event.userId(), "system"),
          event.outcome(),
          coalesce(event.details(), "-"),
          event.timestamp());
    } finally {
      clearCorrelation();
    }
  }

  /** Generates a fresh correlation ID and pushes it onto MDC. */
  public void startCorrelation() {
    MDC.put("correlationId", UUID.randomUUID().toString());
  }

  /** Removes the correlation ID from MDC. Safe to call even if none was set. */
  public void clearCorrelation() {
    MDC.remove("correlationId");
  }

  /** Resolves the currently-authenticated user, or {@code "system"} as fallback. */
  public static String currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && !"anonymousUser".equals(auth.getName())) {
      return auth.getName();
    }
    return "system";
  }

  /** Returns the first non-null, non-blank argument, or {@code null}. */
  private static String coalesce(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
