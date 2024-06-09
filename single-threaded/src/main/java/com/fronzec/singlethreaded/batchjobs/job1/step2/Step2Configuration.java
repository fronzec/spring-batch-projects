package com.fronzec.singlethreaded.batchjobs.job1.step2;

import com.fronzec.singlethreaded.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.singlethreaded.person.PersonsEntity;
import com.fronzec.singlethreaded.personv2.PersonsV2Entity;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.AbstractPaginatedDataItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step2Configuration {

  public final StepBuilderFactory stepBuilderFactory;

  @Value("${single-threaded.jobs.job1.step2.chunk-size:1000}")
  private int chunkSize;

  public Step2Configuration(StepBuilderFactory stepBuilderFactory) {
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @JobScope
  @Bean
  public Step step2(
      AbstractPaginatedDataItemReader<PersonsEntity> reader,
      Step2PersonProcessor processor,
      @Qualifier("step2Personv2Writer")
          ItemWriter<ProcessIndicatorItemWrapper<PersonsV2Entity>> writer) {
    return stepBuilderFactory
        .get("job1Step2")
        .<PersonsEntity, ProcessIndicatorItemWrapper<PersonsV2Entity>>chunk(chunkSize)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
  }
}
