package com.fronzec.api;

import java.util.List;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

public interface BatchJobPlugin {
  String getJobName();

  String getVersion();

  Job configureJob(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ApplicationContext parentContext);

  Map<String, String> getDefaultParameters();

  List<String> getRequiredDependencies();

  JobMetadata getMetadata();
}
