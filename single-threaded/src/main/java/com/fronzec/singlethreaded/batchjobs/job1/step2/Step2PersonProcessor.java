package com.fronzec.singlethreaded.batchjobs.job1.step2;

import com.fronzec.singlethreaded.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.singlethreaded.person.PersonsEntity;
import com.fronzec.singlethreaded.personv2.PersonsV2Entity;
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
