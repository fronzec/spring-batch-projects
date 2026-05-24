/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.batchjobs.job1.step3.PayloadItemInfo;
import com.fronzec.frbatchservice.personv2.PersonV2Repository;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Loads {@link PersonsV2Entity} records linked to an ERROR-flagged {@link DispatchedGroupEntity}
 * and builds a {@link RecoveryGroupPayload} ready for re-dispatch.
 *
 * <p>Uses the derived query {@code PersonV2Repository.findByFkDispatchedGroupId} moved into this
 * module's scope so the entity and its associated persons stay together through the chunk pipeline.
 */
@Component
@StepScope
public class Job2Processor implements ItemProcessor<DispatchedGroupEntity, RecoveryGroupPayload> {

  private static final Logger logger = LoggerFactory.getLogger(Job2Processor.class);

  private final PersonV2Repository personV2Repository;

  public Job2Processor(PersonV2Repository personV2Repository) {
    this.personV2Repository = personV2Repository;
  }

  @Override
  public RecoveryGroupPayload process(DispatchedGroupEntity item) {
    List<PersonsV2Entity> persons =
        personV2Repository.findByFkDispatchedGroupId(item.getId());

    List<PayloadItemInfo> payloadItems =
        persons.stream()
            .map(
                p ->
                    new PayloadItemInfo(
                        p.getUuidV4(),
                        p.getFirstName(),
                        p.getLastName(),
                        p.getEmail(),
                        p.getProfession()))
            .toList();

    logger.debug(
        "Built recovery payload for dispatchedGroupId={} with {} items",
        item.getId(),
        payloadItems.size());

    return new RecoveryGroupPayload(item, payloadItems);
  }
}
