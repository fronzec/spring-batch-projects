package com.fronzec.frbatchservice.batchjobs.job1.step1;

import com.fronzec.frbatchservice.batchjobs.job1.Person;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class CsvProcessor implements ItemProcessor<Person, Person> {

  @Override
  public Person process(Person person) throws Exception {
    final String firstName = person.getFirstName().toUpperCase();
    final String lastName = person.getLastName().toUpperCase();
    person.setFirstName(firstName);
    person.setLastName(lastName);
    return person;
  }
}
