package com.fronzec.myservice.batch.job1.step3;

import com.fronzec.myservice.batch.persons.ProcessIndicatorItemWrapper;
import com.fronzec.myservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step3Processor implements ItemProcessor<PersonsV2Entity, ProcessIndicatorItemWrapper<PayloadItemInfo>> {

  @Override
  public ProcessIndicatorItemWrapper<PayloadItemInfo> process(PersonsV2Entity item) {
    PayloadItemInfo payloadItemInfo =
            new PayloadItemInfo(item.getUuidV4(), item.getFirstName(), item.getLastName(), item.getEmail(), item.getProfession());
    return new ProcessIndicatorItemWrapper<>(item.getId(), payloadItemInfo);
  }

}
