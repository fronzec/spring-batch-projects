/* 2024-2026 */
package com.fronzec.frbatchservice.config;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-production component that auto-approves newly uploaded job definitions.
 *
 * <p>In every non-production profile ({@code @Profile("!production")}), each
 * upload is implicitly trusted and set to {@code APPROVED} — no manual approve
 * step needed. This component is NOT loaded in production, where the approval
 * guard in {@code DynamicJobLoaderService} is active and requires explicit
 * approval via the REST API.
 *
 * <p>Can be disabled within a non-production profile via
 * {@code app.plugins.approval.auto-approve=false} (defaults to {@code true}) — e.g.
 * to exercise the manual approval lifecycle in a test.
 *
 * <p>Injected into {@code JarUploadService}, which calls {@link #approve} and
 * then re-saves the entity to persist the approval.
 */
@Component
@Profile("!production")
@ConditionalOnProperty(
    name = "app.plugins.approval.auto-approve",
    havingValue = "true",
    matchIfMissing = true)
public class AutoApproveConfig {

  private static final Logger log = LoggerFactory.getLogger(AutoApproveConfig.class);

  /**
   * Marks a newly-persisted job definition as approved.
   *
   * <p>Called synchronously from {@code JarUploadService} right after the
   * entity is saved to the database.
   */
  public void approve(JobDefinitionEntity entity) {
    entity.setApprovalStatus("APPROVED");
    entity.setApprovedBy("system");
    entity.setApprovedAt(LocalDateTime.now());
    log.debug(
        "Auto-approved definition id={}, jobName={} (dev profile)",
        entity.getId(),
        entity.getJobName());
  }
}
