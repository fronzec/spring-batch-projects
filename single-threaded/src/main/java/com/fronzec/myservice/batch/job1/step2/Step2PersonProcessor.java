package com.fronzec.myservice.batch.job1.step2;

import com.fronzec.myservice.person.PersonsEntity;
import com.fronzec.myservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step2PersonProcessor implements ItemProcessor<PersonsEntity, PersonsV2Entity> {

  @Override
  public PersonsV2Entity process(PersonsEntity item) throws Exception {
    return PersonToPersonv2Mapper.fromTo(item);
  }
}
