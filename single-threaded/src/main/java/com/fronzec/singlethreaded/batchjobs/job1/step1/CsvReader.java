package com.fronzec.singlethreaded.batchjobs.job1.step1;

import com.fronzec.singlethreaded.batchjobs.job1.Person;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class CsvReader {

  @StepScope
  @Bean
  public FlatFileItemReader<Person> readerPersons() {
    return new FlatFileItemReaderBuilder<Person>().name("personsItemReader")
            .resource(new ClassPathResource("sample-persons-1k.csv"))
            .delimited()
            .names("firstName", "lastName", "email", "profession")
            .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
              setTargetType(Person.class);
            }})
            .build();
  }

}
