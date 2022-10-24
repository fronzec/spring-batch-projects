package com.fronzec.singlethreaded.batchjobs.job1.step3;

import com.fronzec.singlethreaded.batchjobs.dispatchedgroups.DispatchStatus;
import com.fronzec.singlethreaded.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.singlethreaded.batchjobs.dispatchedgroups.DispatchedGroupEntityRepository;
import com.fronzec.singlethreaded.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.singlethreaded.restclients.ApiClient;
import com.fronzec.singlethreaded.restclients.BatchItemsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class Step3Writer implements ItemWriter<ProcessIndicatorItemWrapper<PayloadItemInfo>> {

  Logger logger = Logger.getLogger(Step3Writer.class.getName());

  private final ApiClient apiClient;
  private final DispatchedGroupEntityRepository dispatchedGroupEntityRepository;

  public Step3Writer(ApiClient apiClient, DispatchedGroupEntityRepository dispatchedGroupEntityRepository) {
    this.apiClient = apiClient;
    this.dispatchedGroupEntityRepository = dispatchedGroupEntityRepository;
  }

  @Override
  public void write(List<? extends ProcessIndicatorItemWrapper<PayloadItemInfo>> items) {
    List<PayloadItemInfo> payloadItemInfos = items.stream()
            .map(ProcessIndicatorItemWrapper::getItem)
            .collect(Collectors.toList());
    DispatchedGroupEntity dispatchedGroup = new DispatchedGroupEntity();
    dispatchedGroup.setUuidV4(UUID.randomUUID().toString());
    dispatchedGroupEntityRepository.save(dispatchedGroup);
    if (apiClient.sendBatch(new BatchItemsPayload(payloadItemInfos))) {
      logger.info("items size sent -> " + items.size());
      dispatchedGroup.setDispatchStatus(DispatchStatus.SENT);
      dispatchedGroup.setRecordsIncluded(items.size());
      List<Long> ids = new ArrayList<>(items.size());
      items.forEach(item -> ids.add(item.getId()));
    } else {
      // TODO: 13/09/2022 finalize the job manually
      logger.info("cannot send items -> " + items.size());
      dispatchedGroup.setDispatchStatus(DispatchStatus.ERROR);
    }
  }
}
