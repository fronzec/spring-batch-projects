/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class BatchConfiguration {

    /**
     * If we need allow launch a job from an HTTP request we need to launch async, To launch a Job we
     * need the job and a JobLauncher
     *
     * @return
     * @throws Exception
     */
    @Bean
    public JobOperator asyncJobLauncher(JobRepository jobRepository, JobRegistry jobRegistry)
            throws Exception {
        var jobLauncher = new TaskExecutorJobOperator();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setJobRegistry(jobRegistry);
        jobLauncher.setTaskExecutor(
                new SimpleAsyncTaskExecutor("asyncJobExecutor")); // Job exexutor
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }

    /**
     * Sync task executor. To launch a Job we need the job and a JobLauncher
     *
     * @return
     * @throws Exception
     */
    @Bean
    public JobOperator syncJobLauncher(JobRepository jobRepository, JobRegistry jobRegistry)
            throws Exception {
        var jobLauncher = new TaskExecutorJobOperator();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setJobRegistry(jobRegistry);
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }
}
