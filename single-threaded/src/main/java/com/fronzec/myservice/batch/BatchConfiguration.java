package com.fronzec.myservice.batch;

import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class BatchConfiguration {

  /**
   * JobBuilderFactory for JobBuilder which sets the JobRepository automatically
   */
  public final JobBuilderFactory jobBuilderFactory;

  public BatchConfiguration(JobBuilderFactory jobBuilderFactory) {
    this.jobBuilderFactory = jobBuilderFactory;
  }

  /**
   * If we need allow launch a job from an HTTP request we need to launch async,
   * To launch a Job we need the job and a JobLauncher
   *
   * @return
   * @throws Exception
   */
  @Bean
  public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
    var jobLauncher = new SimpleJobLauncher();
    jobLauncher.setJobRepository(jobRepository);
    jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor("asyncJobExecutor"));// Job exexutor
    jobLauncher.afterPropertiesSet();
    return jobLauncher;
  }

  /**
   * If we need allow launch a job from an HTTP request we need to launch async,
   * To launch a Job we need the job and a JobLauncher
   *
   * @return
   * @throws Exception
   */
  @Bean
  public JobLauncher syncJobLauncher(JobRepository jobRepository) throws Exception {
    var jobLauncher = new SimpleJobLauncher();
    jobLauncher.setJobRepository(jobRepository);
    jobLauncher.afterPropertiesSet();
    return jobLauncher;
  }
}
