/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

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
 * Plugin implementation for {@code job2}: a single-step recovery pipeline that reads ERROR-flagged
 * {@code DispatchedGroupEntity} rows, rebuilds their payloads, and re-dispatches them via the REST
 * API with configurable retry.
 *
 * <p>The step bean {@code job2Step} is declared in {@link Job2Configuration} and injected here via
 * constructor injection with a {@code @Qualifier}. This plugin only assembles the job graph — it
 * does not redeclare or own the step bean.
 */
@Component
public class Job2Plugin implements BatchJobPlugin {

  private static final String JOB_NAME = "job2";
  private static final String VERSION = "1.0.0";

  private final JobCompletionNotificationListener listener;
  private final Step job2Step;

  public Job2Plugin(
      JobCompletionNotificationListener listener,
      @Qualifier("job2Step") Step job2Step) {
    this.listener = listener;
    this.job2Step = job2Step;
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
        .flow(job2Step)
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
    return new Job2Metadata();
  }
}
