package com.fronzec.myservice.batch.job1.step2;

import com.fronzec.myservice.person.PersonRepository;
import com.fronzec.myservice.person.PersonsEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.data.AbstractPaginatedDataItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step2KeySetPagingItemReader extends AbstractPaginatedDataItemReader<PersonsEntity> {

  long lastPersonId = 0;

  @Value("${single-threaded.jobs.job1.readers.chunk-size:1000}")
  private int chunkRead;

  private final PersonRepository personRepository;

  private List<PersonsEntity> persons = new ArrayList<>();

  public Step2KeySetPagingItemReader(PersonRepository personRepository) {
    this.personRepository = personRepository;
    // note: required set the name for spring batch
    setName(Step2KeySetPagingItemReader.class.getName());
  }

  @Override
  protected Iterator<PersonsEntity> doPageRead() {
    PageRequest pageRequest = PageRequest.of(0, chunkRead);
    lastPersonId = getLastChukId(persons);
    persons = personRepository.findByIdGreaterThanAndProcessedIsFalseOrderByIdAsc(lastPersonId, pageRequest);
    return persons.iterator();
  }

  private long getLastChukId(List<PersonsEntity> persons) {
    if (persons == null || persons.isEmpty()) {
      return 0L;
    }
    return persons.get(persons.size() - 1)
            .getId();
  }
}
