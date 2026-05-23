package com.fronzec.frbatchservice.batchjobs.job1;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.frbatchservice.batchjobs.JobCompletionNotificationListener;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Plugin implementation for {@code job1}: a 3-step ETL pipeline that reads persons from CSV,
 * transforms them, and posts the result to a remote REST API.
 *
 * <p>The three {@link Step} beans ({@code step1}, {@code step2}, {@code step3}) are declared in
 * their respective {@code Step*Configuration} classes and injected here via constructor injection.
 * This plugin only assembles the job graph — it does not redeclare or own the step beans.
 */
@Component
public class Job1Plugin implements BatchJobPlugin {

    private static final String JOB_NAME = "job1";
    private static final String VERSION = "1.0.0";

    private final JobCompletionNotificationListener listener;
    private final Step step1;
    private final Step step2;
    private final Step step3;

    public Job1Plugin(
            JobCompletionNotificationListener listener,
            @Qualifier("step1") Step step1,
            @Qualifier("step2") Step step2,
            @Qualifier("step3") Step step3) {
        this.listener = listener;
        this.step1 = step1;
        this.step2 = step2;
        this.step3 = step3;
    }

    @Override
    public String getJobName() {
        return JOB_NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public Job configureJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext parentContext) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .next(step2)
                .next(step3)
                .end()
                .build();
    }

    @Override
    public Map<String, String> getDefaultParameters() {
        return Map.of("paramname", "paramvalue");
    }

    @Override
    public List<String> getRequiredDependencies() {
        return List.of();
    }

    @Override
    public JobMetadata getMetadata() {
        return new Job1Metadata();
    }
}
