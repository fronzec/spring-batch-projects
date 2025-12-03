/* 2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step1;

import com.fronzec.frbatchservice.batchjobs.job1.Person;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class CsvProcessor implements ItemProcessor<Person, Person> {

    /**
     * Converts the given person's first and last names to uppercase.
     *
     * @param person the Person whose firstName and lastName will be converted to uppercase; modified in-place
     * @return the same Person instance with firstName and lastName set to uppercase
     */
    @Override
    public Person process(Person person) throws Exception {
        final String firstName = person.getFirstName().toUpperCase();
        final String lastName = person.getLastName().toUpperCase();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        return person;
    }
}