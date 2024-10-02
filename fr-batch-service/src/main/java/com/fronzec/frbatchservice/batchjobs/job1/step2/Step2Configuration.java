/* (C)2024 */
package com.fronzec.frbatchservice.batchjobs.job1.step2;

import com.fronzec.frbatchservice.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.frbatchservice.person.PersonsEntity;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.AbstractPaginatedDataItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class Step2Configuration {

    @Value("${fr-batch-service.jobs.job1.step2.chunk-size:1000}")
    private int chunkSize;

    public Step2Configuration() {}

    @JobScope
    @Bean
    public Step step2(
            AbstractPaginatedDataItemReader<PersonsEntity> reader,
            Step2PersonProcessor processor,
            @Qualifier("step2Personv2Writer")
                    ItemWriter<ProcessIndicatorItemWrapper<PersonsV2Entity>> writer,
            PlatformTransactionManager transactionManager,
            JobRepository jobRepository) {
        return new StepBuilder("job1Step2", jobRepository)
                .<PersonsEntity, ProcessIndicatorItemWrapper<PersonsV2Entity>>chunk(
                        chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
