package com.fronzec.singlethreaded.batchjobs.job1;

import com.fronzec.singlethreaded.batchjobs.JobCompletionNotificationListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Job1Configuration {

  /** JobBuilderFactory for JobBuilder which sets the JobRepository automatically */
  public final JobBuilderFactory jobBuilderFactory;

  public Job1Configuration(JobBuilderFactory jobBuilderFactory) {
    this.jobBuilderFactory = jobBuilderFactory;
  }

  /**
   * Job 1 configuration with complex flow
   *
   * @param listener our job completion listener
   * @param step1 the step 1
   * @param step2 the step 2
   * @param step3 the step 3
   * @return the Job
   */
  @Bean(name = "job1")
  public Job job1(JobCompletionNotificationListener listener, Step step1, Step step2, Step step3) {
    return jobBuilderFactory
        .get(
            "job1") // JobName: The jobname could be different from bean name but is common to have
                    // the
        // same value
        .incrementer(new RunIdIncrementer()) // Job id increment identifier
        .listener(listener) // Job listeners that allow tracking of job lifecycle events
        .flow(step1) // Configure the first step for this job
        .next(step2) // Configure the second step for this job
        .next(step3) // Configure the third step for this job
        .end() // No more steps for our job, ready to build
        .build();
  }
}
