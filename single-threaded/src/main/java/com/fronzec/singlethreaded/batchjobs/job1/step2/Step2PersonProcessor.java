package com.fronzec.singlethreaded.batchjobs.job1.step2;

import com.fronzec.singlethreaded.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.singlethreaded.person.PersonsEntity;
import com.fronzec.singlethreaded.personv2.PersonsV2Entity;
import com.fronzec.singlethreaded.restclients.ApiClient;
import com.fronzec.singlethreaded.restclients.DataCalculatedResponse;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@StepScope
@Component
public class Step2PersonProcessor implements ItemProcessor<PersonsEntity, ProcessIndicatorItemWrapper<PersonsV2Entity>> {

  private final ApiClient apiClient;
  private final LocalDate processingDate;

  public Step2PersonProcessor(ApiClient apiClient,
                              @Value("#{jobParameters['DATE']}") String processingDateStr) {
    this.apiClient = apiClient;
    this.processingDate = LocalDate.parse(processingDateStr);
  }

  @Override
  public ProcessIndicatorItemWrapper<PersonsV2Entity> process(PersonsEntity item) {
    DataCalculatedResponse randomValue = apiClient.getRandomValue(item.getId());
    PersonsV2Entity personsV2Entity = PersonToPersonv2Mapper.fromTo(processingDate, item, randomValue.getValue());
    return new ProcessIndicatorItemWrapper<>(item.getId(), personsV2Entity);
  }
}
