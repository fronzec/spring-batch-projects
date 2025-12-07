/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step3;

import com.fronzec.frbatchservice.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class Step3Configuration {

    @Value("${fr-batch-service.jobs.job1.step3.chunk-size:1000}")
    private int chunkSize;

    public Step3Configuration() {}

    @JobScope
    @Bean
    public Step step3(
            JdbcPagingItemReader<PersonsV2Entity> reader,
            Step3Processor processor,
            Step3Writer writter,
            PlatformTransactionManager transactionManager,
            JobRepository jobRepository) {
        return new StepBuilder("job1Step3", jobRepository)
                .<PersonsV2Entity, ProcessIndicatorItemWrapper<PayloadItemInfo>>chunk(
                        chunkSize)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writter)
                .build();
    }
}
