package com.fronzec.myservice.batch.job1.step1;

import com.fronzec.myservice.batch.job1.Person;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class CsvProcessor implements ItemProcessor <Person, Person> {

  @Override
  public Person process(Person person) throws Exception {
    final String firstName = person.getFirstName()
            .toUpperCase();
    final String lastName = person.getLastName()
            .toUpperCase();
    person.setFirstName(firstName);
    person.setLastName(lastName);
    return person;
  }
}
