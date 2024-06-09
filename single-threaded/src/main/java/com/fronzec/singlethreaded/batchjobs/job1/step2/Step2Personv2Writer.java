package com.fronzec.singlethreaded.batchjobs.job1.step2;

import com.fronzec.singlethreaded.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.singlethreaded.person.PersonRepository;
import com.fronzec.singlethreaded.personv2.PersonV2Repository;
import com.fronzec.singlethreaded.personv2.PersonsV2Entity;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@StepScope
@Component
@Qualifier("step2Personv2Writer")
public class Step2Personv2Writer
  implements ItemWriter<ProcessIndicatorItemWrapper<PersonsV2Entity>> {

  private final PersonV2Repository personV2Repository;

  private final PersonRepository personRepository;

  private final Logger logger = LoggerFactory.getLogger(Step2Personv2Writer.class);

  public Step2Personv2Writer(
    PersonV2Repository personV2Repository,
    PersonRepository personRepository
  ) {
    this.personV2Repository = personV2Repository;
    this.personRepository = personRepository;
  }

  @Override
  public void write(List<? extends ProcessIndicatorItemWrapper<PersonsV2Entity>> items) {
    List<PersonsV2Entity> entities = new ArrayList<>(items.size());
    List<Long> originItemIds = new ArrayList<>(items.size());
    items.forEach(item -> {
      entities.add(item.getItem());
      originItemIds.add(item.getId());
    });
    personV2Repository.saveAll(entities);
    int updated = personRepository.updateProcessedInIds(originItemIds);
    logger.info(String.format("Total updated IDs %s", updated));
  }
}
