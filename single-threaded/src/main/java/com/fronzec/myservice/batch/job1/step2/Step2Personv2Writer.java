package com.fronzec.myservice.batch.job1.step2;

import com.fronzec.myservice.personv2.PersonV2Repository;
import com.fronzec.myservice.personv2.PersonsV2Entity;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@StepScope
@Component
@Qualifier("step2Personv2Writer")
public class Step2Personv2Writer implements ItemWriter<PersonsV2Entity> {

  private final PersonV2Repository personV2Repository;

  public Step2Personv2Writer(PersonV2Repository personV2Repository) {
    this.personV2Repository = personV2Repository;
  }

  @Override
  public void write(List<? extends PersonsV2Entity> items) throws Exception {
    personV2Repository.saveAll(items);
  }
}
