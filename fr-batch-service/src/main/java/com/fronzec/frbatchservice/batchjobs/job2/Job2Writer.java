/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchStatus;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntityRepository;
import com.fronzec.frbatchservice.restclients.ApiClient;
import com.fronzec.frbatchservice.restclients.BatchItemsPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Dispatches recovered payloads via {@link ApiClient#sendBatch} and flips the group's {@code
 * dispatchStatus} to {@code SENT} on success.
 *
 * <p>Idempotency guard: if the group is already {@code SENT} (e.g. an earlier run recorded the
 * status before the transaction aborted), the write is skipped for that item. On failure the
 * exception propagates to the step's retry policy; exhausted retries leave the status as {@code
 * ERROR} so the next job run picks it up automatically.
 */
@Component
@StepScope
public class Job2Writer implements ItemWriter<RecoveryGroupPayload> {

  private static final Logger logger = LoggerFactory.getLogger(Job2Writer.class);

  private final ApiClient apiClient;
  private final DispatchedGroupEntityRepository dispatchedGroupEntityRepository;

  public Job2Writer(
      ApiClient apiClient,
      DispatchedGroupEntityRepository dispatchedGroupEntityRepository) {
    this.apiClient = apiClient;
    this.dispatchedGroupEntityRepository = dispatchedGroupEntityRepository;
  }

  @Override
  public void write(Chunk<? extends RecoveryGroupPayload> chunk) {
    for (RecoveryGroupPayload payload : chunk) {
      DispatchedGroupEntity group = payload.dispatchedGroup();

      // Idempotency guard: skip already-sent groups
      if (group.getDispatchStatus() == DispatchStatus.SENT) {
        logger.info(
            "Skipping dispatchedGroupId={} — already SENT (idempotent guard)", group.getId());
        continue;
      }

      BatchItemsPayload batchPayload = new BatchItemsPayload(payload.payloadItems());

      if (apiClient.sendBatch(batchPayload)) {
        group.setDispatchStatus(DispatchStatus.SENT);
        group.setRecordsIncluded(payload.payloadItems().size());
        dispatchedGroupEntityRepository.save(group);
        logger.info(
            "Successfully re-dispatched groupId={} with {} items",
            group.getId(),
            payload.payloadItems().size());
      } else {
        // sendBatch returned false (non-OK HTTP status); throw so retry policy engages
        throw new RuntimeException(
            "API rejected batch for dispatchedGroupId=" + group.getId());
      }
    }
  }
}
