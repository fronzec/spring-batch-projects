package com.fronzec.myservice.batch.job1.step1;

import com.fronzec.myservice.batch.job1.Person;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step1Configuration {

  /**
   * Convenient factory for a StepBuilder which sets the JobRepository automatically
   */
  public final StepBuilderFactory stepBuilderFactory;

  public Step1Configuration(StepBuilderFactory stepBuilderFactory) {
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @Bean
  public Step step1(FlatFileItemReader<Person> readerPersons, CsvProcessor processor, JdbcBatchItemWriter<Person> writer) {
    return stepBuilderFactory.get("step1")
            .<Person, Person> chunk(10)
            .reader(readerPersons)
            .processor(processor)
            .writer(writer)
            .build();
  }

}
