package com.fronzec.frbatchservice.batchjobs.job1.step3;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchStatus;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntityRepository;
import com.fronzec.frbatchservice.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.frbatchservice.personv2.PersonV2Repository;
import com.fronzec.frbatchservice.restclients.ApiClient;
import com.fronzec.frbatchservice.restclients.BatchItemsPayload;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
@StepScope
public class Step3Writer implements ItemWriter<ProcessIndicatorItemWrapper<PayloadItemInfo>> {

  Logger logger = Logger.getLogger(Step3Writer.class.getName());

  private final ApiClient apiClient;
  private final DispatchedGroupEntityRepository dispatchedGroupEntityRepository;
  private final PersonV2Repository personV2Repository;

  public Step3Writer(
      ApiClient apiClient,
      DispatchedGroupEntityRepository dispatchedGroupEntityRepository,
      PersonV2Repository personV2Repository) {
    this.apiClient = apiClient;
    this.dispatchedGroupEntityRepository = dispatchedGroupEntityRepository;
    this.personV2Repository = personV2Repository;
  }

  @Override
  public void write(Chunk<? extends ProcessIndicatorItemWrapper<PayloadItemInfo>> chunk) {
    List<? extends ProcessIndicatorItemWrapper<PayloadItemInfo>> items = chunk.getItems();
    List<PayloadItemInfo> payloadItemInfos =
        items.stream().map(ProcessIndicatorItemWrapper::getItem).collect(Collectors.toList());
    DispatchedGroupEntity dispatchedGroup = new DispatchedGroupEntity();
    dispatchedGroup.setUuidV4(UUID.randomUUID().toString());
    dispatchedGroupEntityRepository.save(dispatchedGroup);
    if (apiClient.sendBatch(new BatchItemsPayload(payloadItemInfos))) {
      logger.info("items size sent -> " + items.size());
      dispatchedGroup.setDispatchStatus(DispatchStatus.SENT);
      dispatchedGroup.setRecordsIncluded(items.size());
      List<Long> ids = new ArrayList<>(items.size());
      items.forEach(item -> ids.add(item.getId()));
      personV2Repository.updateDispatchedGroupId(dispatchedGroup.getId(), ids);
    } else {
      // TODO: 13/09/2022 finalize the job manually
      logger.info("cannot send items -> " + items.size());
      dispatchedGroup.setDispatchStatus(DispatchStatus.ERROR);
    }
  }
}
