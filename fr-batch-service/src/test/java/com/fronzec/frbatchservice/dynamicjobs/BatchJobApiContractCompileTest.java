/* 2026 */
package com.fronzec.frbatchservice.dynamicjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

class BatchJobApiContractCompileTest {

  @Test
  void shouldCompileAgainstBatchJobApi() {
    BatchJobPlugin plugin = new DummyPlugin();
    assertEquals("dummy-job", plugin.getJobName());
    assertNotNull(plugin.getMetadata());
  }

  private static final class DummyPlugin implements BatchJobPlugin {
    @Override
    public String getJobName() {
      return "dummy-job";
    }

    @Override
    public String getVersion() {
      return "0.0.0";
    }

    @Override
    public Job configureJob(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ApplicationContext parentContext) {
      return org.mockito.Mockito.mock(Job.class);
    }

    @Override
    public Map<String, String> getDefaultParameters() {
      return Map.of();
    }

    @Override
    public List<String> getRequiredDependencies() {
      return List.of();
    }

    @Override
    public JobMetadata getMetadata() {
      return new JobMetadata() {
        @Override
        public String getDisplayName() {
          return "Dummy Job";
        }

        @Override
        public String getDescription() {
          return "Dummy job used to validate compile-time API contract.";
        }

        @Override
        public String getAuthor() {
          return "fr-batch-service";
        }

        @Override
        public List<String> getTags() {
          return List.of("test");
        }

        @Override
        public Duration getEstimatedRuntime() {
          return Duration.ZERO;
        }
      };
    }
  }
}
