package com.fronzec.singlethreaded.batchjobs.job2.step1;

import com.fronzec.singlethreaded.group.GroupedEntity;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step1Configuration {
    
    public final StepBuilderFactory stepBuilderFactory;
    @Value("${single-threaded.jobs.job2.step1.chunk-size:1000}")
    private int chunkSize;

    public Step1Configuration(StepBuilderFactory stepBuilderFactory) {
        this.stepBuilderFactory = stepBuilderFactory;
    }

    @JobScope
    @Bean
    public Step job2Step1(JdbcCursorItemReader<GroupedEntity> reader,
                          @Qualifier("job2Step1Writer")
    ItemWriter<GroupedEntity> writer) {
        return stepBuilderFactory.get("job2Step1")
                .<GroupedEntity, GroupedEntity>chunk(chunkSize)
                .reader(reader)
                .writer(writer)
                .build();
    }
}
