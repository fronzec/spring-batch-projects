/* 2024-2026 */
package com.fronzec.test.dynamic;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Minimal {@link BatchJobPlugin} implementation for integration testing of the dynamic job loading
 * pipeline.
 *
 * <p>This class is compiled by Maven on the test classpath and packaged into a programmatic JAR at
 * test runtime by {@code DynamicJobLoadingIntegrationTest}. It is <em>not</em> a Spring bean —
 * only discovered when its JAR is loaded via {@code DynamicJobClassLoader}.
 *
 * <p>The configured {@link Job} is a single-step no-op tasklet that always completes successfully.
 */
public class DynamicTestPlugin implements BatchJobPlugin {

  private static final String JOB_NAME = "dynamic-test-job";
  private static final String VERSION = "1.0.0";

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
        .start(
            new StepBuilder("noop-step", jobRepository)
                .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED)
                .build())
        .build();
  }

  @Override
  public Map<String, String> getDefaultParameters() {
    return Collections.emptyMap();
  }

  @Override
  public List<String> getRequiredDependencies() {
    return Collections.emptyList();
  }

  @Override
  public JobMetadata getMetadata() {
    return new TestJobMetadata();
  }

  /** Named inner class so the compiled {@code .class} file is discoverable via filesystem walk. */
  public static class TestJobMetadata implements JobMetadata {

    @Override
    public String getDisplayName() {
      return "Dynamic Test Plugin";
    }

    @Override
    public String getDescription() {
      return "Minimal BatchJobPlugin for integration tests of the dynamic loading pipeline";
    }

    @Override
    public String getAuthor() {
      return "Integration Test";
    }

    @Override
    public List<String> getTags() {
      return List.of("test", "dynamic", "noop");
    }

    @Override
    public Duration getEstimatedRuntime() {
      return Duration.ofSeconds(1);
    }
  }
}
