package com.fronzec.myservice.batch.job1.step2;

import com.fronzec.myservice.person.PersonsEntity;
import com.fronzec.myservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.AbstractPaginatedDataItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step2Configuration {

  public final StepBuilderFactory stepBuilderFactory;


  public Step2Configuration(StepBuilderFactory stepBuilderFactory) {
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @JobScope
  @Bean
  public Step step2(AbstractPaginatedDataItemReader<PersonsEntity> reader, Step2PersonProcessor processor,
          @Qualifier("step2Personv2Writer")
                  ItemWriter<PersonsV2Entity> writer) {
    return stepBuilderFactory.get("job1Step2")
            .<PersonsEntity, PersonsV2Entity>chunk(1000)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
  }


}
