package com.fronzec.myservice.batch.job1.step3;

import com.fronzec.myservice.batch.persons.ProcessIndicatorItemWrapper;
import com.fronzec.myservice.restclients.ApiClient;
import com.fronzec.myservice.restclients.BatchItemsPayload;
import java.util.List;
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

  public Step3Writer(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  @Override
  public void write(List<? extends ProcessIndicatorItemWrapper<PayloadItemInfo>> items) {
    List<PayloadItemInfo> payloadItemInfos = items.stream()
            .map(ProcessIndicatorItemWrapper::getItem)
            .collect(Collectors.toList());
    if (apiClient.sendBatch(new BatchItemsPayload(payloadItemInfos))) {
      logger.info("items size sent -> " + items.size());
    } else {
      // TODO: 13/09/2022 finalize the job manually
      logger.info("cannot send items -> " + items.size());
    }
  }
}
