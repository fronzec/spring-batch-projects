/* 2024 */
package com.fronzec.frbatchservice.batchjobs.job1.step1;

import com.fronzec.frbatchservice.batchjobs.job1.Person;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class Step1Configuration {

    @Value("${fr-batch-service.jobs.job1.step1.chunk-size:1000}")
    private int chunkSize;

    public Step1Configuration() {}

    @JobScope
    @Bean
    public Step step1(
            FlatFileItemReader<Person> readerPersons,
            CsvProcessor processor,
            JdbcBatchItemWriter<Person> writer,
            PlatformTransactionManager transactionManager,
            JobRepository jobRepository) {
        return new StepBuilder("job1Step1", jobRepository)
                .<Person, Person>chunk(chunkSize, transactionManager)
                .reader(readerPersons)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
