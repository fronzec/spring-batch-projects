package com.fronzec.frbatchservice.batchjobs.job1.step3;

import com.fronzec.frbatchservice.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step3Configuration {

  private final StepBuilderFactory stepBuilderFactory;

  @Value("${fr-batch-service.jobs.job1.step3.chunk-size:1000}")
  private int chunkSize;

  public Step3Configuration(StepBuilderFactory stepBuilderFactory) {
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @JobScope
  @Bean
  public Step step3(
      JdbcPagingItemReader<PersonsV2Entity> reader, Step3Processor processor, Step3Writer writter) {
    return stepBuilderFactory
        .get("job1Step3")
        .<PersonsV2Entity, ProcessIndicatorItemWrapper<PayloadItemInfo>>chunk(chunkSize)
        .reader(reader)
        .processor(processor)
        .writer(writter)
        .build();
  }
}
