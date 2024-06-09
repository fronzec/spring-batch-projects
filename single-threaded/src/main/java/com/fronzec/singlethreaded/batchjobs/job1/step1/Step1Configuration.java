package com.fronzec.singlethreaded.batchjobs.job1.step1;

import com.fronzec.singlethreaded.batchjobs.job1.Person;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step1Configuration {

  /** Convenient factory for a StepBuilder which sets the JobRepository automatically */
  public final StepBuilderFactory stepBuilderFactory;

  @Value("${single-threaded.jobs.job1.step1.chunk-size:1000}")
  private int chunkSize;

  public Step1Configuration(StepBuilderFactory stepBuilderFactory) {
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @JobScope
  @Bean
  public Step step1(
      FlatFileItemReader<Person> readerPersons,
      CsvProcessor processor,
      JdbcBatchItemWriter<Person> writer) {
    return stepBuilderFactory
        .get("job1Step1")
        .<Person, Person>chunk(chunkSize)
        .reader(readerPersons)
        .processor(processor)
        .writer(writer)
        .build();
  }
}
