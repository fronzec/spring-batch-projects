package com.fronzec.myservice.batch.job1.step2;

import com.fronzec.myservice.batch.persons.ProcessIndicatorItemWrapper;
import com.fronzec.myservice.person.PersonsEntity;
import com.fronzec.myservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step2PersonProcessor implements ItemProcessor<PersonsEntity, ProcessIndicatorItemWrapper<PersonsV2Entity>> {

  @Override
  public ProcessIndicatorItemWrapper<PersonsV2Entity> process(PersonsEntity item) {
    PersonsV2Entity personsV2Entity = PersonToPersonv2Mapper.fromTo(item);
    return new ProcessIndicatorItemWrapper<>(item.getId(), personsV2Entity);
  }
}
