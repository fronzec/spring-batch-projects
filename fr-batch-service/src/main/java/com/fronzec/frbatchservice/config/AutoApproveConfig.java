/* 2024-2026 */
package com.fronzec.frbatchservice.config;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev-profile component that auto-approves newly uploaded job definitions.
 *
 * <p>In the {@code dev} profile, every upload is implicitly trusted and set to
 * {@code APPROVED} — no manual approve step needed. This component is NOT loaded
 * in production where the approval guard in {@code DynamicJobLoaderService} is
 * active and requires explicit approval via the REST API.
 *
 * <p>Injected into {@code JarUploadService} so the auto-approval happens
 * atomically at persist time.
 */
@Component
@Profile("dev")
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
