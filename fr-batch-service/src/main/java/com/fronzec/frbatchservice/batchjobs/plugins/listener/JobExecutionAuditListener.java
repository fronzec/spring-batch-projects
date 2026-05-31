/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.listener;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobExecutionAuditEntity;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobExecutionAuditRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Spring Batch listener that captures job execution lifecycle events into the
 * {@code job_executions_audit} table.
 *
 * <p>Each execution pair (before → after) produces one audit row:
 * <ul>
 *   <li>{@link #beforeJob(JobExecution)} — creates the row with start metadata</li>
 *   <li>{@link #afterJob(JobExecution)} — updates the row with outcome, duration</li>
 * </ul>
 *
 * <p>The {@link JobDefinitionEntity} is resolved by the job name from the execution's
 * job instance. If no matching definition exists (e.g., a classpath-registered job
 * with no DB row), the execution is silently skipped.
 */
@Component
public class JobExecutionAuditListener implements JobExecutionListener {

  private static final Logger log = LoggerFactory.getLogger(JobExecutionAuditListener.class);

  /** Key used in {@code JobExecution.getExecutionContext()} to pass the audit row ID. */
  private static final String AUDIT_ENTITY_ID_KEY = "_auditEntityId";

  private final JobExecutionAuditRepository auditRepository;
  private final JobDefinitionRepository jobDefinitionRepository;

  public JobExecutionAuditListener(
      JobExecutionAuditRepository auditRepository,
      JobDefinitionRepository jobDefinitionRepository) {
    this.auditRepository = auditRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    String jobName = jobExecution.getJobInstance().getJobName();

    // Only audit plugin-managed definitions (resolvable by name in our registry)
    JobDefinitionEntity def =
        jobDefinitionRepository.findByJobName(jobName).orElse(null);
    if (def == null) {
      log.debug(
          "No job definition found for '{}' — skipping execution audit", jobName);
      return;
    }

    JobExecutionAuditEntity entity = new JobExecutionAuditEntity();
    entity.setJobDefinitionId(def.getId());
    entity.setJobExecutionId(jobExecution.getId());
    entity.setJobVersion(def.getVersion());
    entity.setExecutionMetadata(
        metadataJson(jobExecution.getStartTime(), null, null, null));

    JobExecutionAuditEntity saved = auditRepository.save(entity);

    // Stash the row ID in the execution context so afterJob can find it
    jobExecution.getExecutionContext().put(AUDIT_ENTITY_ID_KEY, saved.getId());

    log.debug(
        "Audit row created: id={}, jobName={}, executionId={}",
        saved.getId(),
        jobName,
        jobExecution.getId());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    Object rawId = jobExecution.getExecutionContext().get(AUDIT_ENTITY_ID_KEY);
    if (!(rawId instanceof Long entityId)) {
      log.debug(
          "No audit entity ID in execution context for executionId={} — skipping after-job audit",
          jobExecution.getId());
      return;
    }

    auditRepository
        .findById(entityId)
        .ifPresentOrElse(
            entity -> {
              long durationMs =
                  Duration.between(
                          jobExecution.getStartTime(),
                          jobExecution.getEndTime() != null
                              ? jobExecution.getEndTime()
                              : jobExecution.getStartTime())
                      .toMillis();
              String outcome = mapOutcome(jobExecution.getExitStatus());

              entity.setExecutionMetadata(
                  metadataJson(
                      jobExecution.getStartTime(),
                      jobExecution.getEndTime(),
                      durationMs,
                      outcome));
              auditRepository.save(entity);

              log.debug(
                  "Audit row updated: id={}, outcome={}, durationMs={}",
                  entityId,
                  outcome,
                  durationMs);
            },
            () ->
                log.warn(
                    "Audit entity id={} not found in DB for after-job update (executionId={})",
                    entityId,
                    jobExecution.getId()));
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /**
   * Maps Spring Batch's {@link ExitStatus} to a simple outcome string.
   *
   * <p>{@code COMPLETED} → {@code SUCCESS}; anything else → {@code FAILURE}.
   */
  static String mapOutcome(ExitStatus exitStatus) {
    return ExitStatus.COMPLETED.getExitCode().equals(exitStatus.getExitCode())
        ? "SUCCESS"
        : "FAILURE";
  }

  /**
   * Builds a compact JSON snapshot for {@code execution_metadata}.
   *
   * <p>Uses string concatenation to avoid a dependency on Jackson for the listener.
   */
  static String metadataJson(
      LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs, String outcome) {
    StringBuilder sb = new StringBuilder(256);
    sb.append('{');

    appendField(sb, "startedAt", startedAt != null ? startedAt.toString() : null);
    if (finishedAt != null) {
      sb.append(',');
      appendField(sb, "finishedAt", finishedAt.toString());
    }
    if (durationMs != null) {
      sb.append(",\"durationMs\":").append(durationMs);
    }
    if (outcome != null) {
      sb.append(",\"outcome\":\"").append(outcome).append('"');
    }

    sb.append('}');
    return sb.toString();
  }

  private static void appendField(StringBuilder sb, String key, String value) {
    sb.append('"').append(key).append("\":\"");
    sb.append(value != null ? value : "");
    sb.append('"');
  }
}
