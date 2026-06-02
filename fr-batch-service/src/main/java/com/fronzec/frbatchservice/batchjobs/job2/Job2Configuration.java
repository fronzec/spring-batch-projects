/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

/**
 * Declares the single fault-tolerant chunk step for job2's failed-dispatch recovery pipeline.
 *
 * <p>The reader uses {@code JpaPagingItemReader} with a JPQL query filtering by {@code
 * dispatchStatus = 'ERROR'}. The step's retry policy uses Spring Framework 7.0 {@link RetryPolicy}
 * scoped to {@link RestClientException} with configurable max attempts and fixed backoff.
 */
@Configuration
public class Job2Configuration {

  @Value("${fr-batch-service.jobs.job2.chunk-size:10}")
  private int chunkSize;

  @Value("${fr-batch-service.jobs.job2.retry.max-attempts:3}")
  private long maxRetries;

  @Value("${fr-batch-service.jobs.job2.retry.backoff-millis:2000}")
  private long backoffMillis;

  @Bean
  @JobScope
  public JpaPagingItemReader<DispatchedGroupEntity> job2Reader(EntityManagerFactory emf) {
    JpaPagingItemReader<DispatchedGroupEntity> reader = new JpaPagingItemReader<>(emf);
    reader.setQueryString(
        "SELECT d FROM DispatchedGroupEntity d WHERE d.dispatchStatus = 'ERROR'");
    reader.setPageSize(chunkSize);
    return reader;
  }

  @Bean
  @JobScope
  public Step job2Step(
      JpaPagingItemReader<DispatchedGroupEntity> job2Reader,
      Job2Processor job2Processor,
      Job2Writer job2Writer,
      PlatformTransactionManager transactionManager,
      JobRepository jobRepository) {

    RetryPolicy retryPolicy =
        RetryPolicy.builder()
            .maxRetries(maxRetries)
            .delay(Duration.ofMillis(backoffMillis))
            .includes(RestClientException.class)
            .build();

    return new StepBuilder("job2Step", jobRepository)
        .<DispatchedGroupEntity, RecoveryGroupPayload>chunk(chunkSize)
        .transactionManager(transactionManager)
        .reader(job2Reader)
        .processor(job2Processor)
        .writer(job2Writer)
        .faultTolerant()
        .retryPolicy(retryPolicy)
        .build();
  }
}
