package com.example.payment;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Example {@link BatchJobPlugin} that processes a sample payments CSV bundled
 * inside the plugin JAR.
 *
 * <p>The configured {@link Job} is a single-step tasklet that reads
 * {@code data/sample-payments.csv} from the classpath and logs a summary
 * (row count and total payment amount).
 *
 * <h3>Metadata</h3>
 * <ul>
 *   <li>Job name: {@code payment-job}</li>
 *   <li>Display name: {@code Payment Processing}</li>
 *   <li>Author: {@code Example Team}</li>
 * </ul>
 */
public class PaymentJobPlugin implements BatchJobPlugin {

  private static final Logger log = LoggerFactory.getLogger(PaymentJobPlugin.class);

  private static final String JOB_NAME = "payment-job";
  private static final String VERSION = "1.0.0";
  private static final String CSV_RESOURCE = "data/sample-payments.csv";

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

    Tasklet paymentTasklet =
        (contribution, chunkContext) -> {
          log.info("PaymentJobPlugin: starting CSV processing...");
          int rowCount = 0;
          double total = 0.0;

          try (var is =
                  getClass().getClassLoader().getResourceAsStream(CSV_RESOURCE);
              var reader =
                  new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            // Skip header line
            String header = reader.readLine();
            log.info("CSV header: {}", header);

            String line;
            while ((line = reader.readLine()) != null) {
              rowCount++;
              String[] fields = line.split(",");
              if (fields.length >= 3) {
                double amount = Double.parseDouble(fields[2].trim());
                total += amount;
              }
            }
          } catch (Exception e) {
            log.error("Failed to read CSV resource: {}", CSV_RESOURCE, e);
            throw new RuntimeException("CSV processing failed", e);
          }

          log.info(
              "PaymentJobPlugin: processed {} payment rows, total amount: ${:.2f}",
              rowCount,
              total);

          chunkContext.getStepContext().getStepExecution()
              .getExecutionContext()
              .put("payment.rows", rowCount);
          chunkContext.getStepContext().getStepExecution()
              .getExecutionContext()
              .put("payment.total", total);

          return RepeatStatus.FINISHED;
        };

    var step =
        new StepBuilder("paymentCsvStep", jobRepository)
            .tasklet(paymentTasklet, transactionManager)
            .build();

    return new JobBuilder(JOB_NAME, jobRepository).start(step).build();
  }

  @Override
  public Map<String, String> getDefaultParameters() {
    return Map.of("DATE", "2024-01-01");
  }

  @Override
  public List<String> getRequiredDependencies() {
    return List.of();
  }

  @Override
  public JobMetadata getMetadata() {
    return new PaymentJobMetadata();
  }

  /** Named inner class so the compiled {@code .class} file is discoverable. */
  public static class PaymentJobMetadata implements JobMetadata {

    @Override
    public String getDisplayName() {
      return "Payment Processing";
    }

    @Override
    public String getDescription() {
      return "Processes a batch of payment transactions from a CSV file and computes a summary.";
    }

    @Override
    public String getAuthor() {
      return "Example Team";
    }

    @Override
    public List<String> getTags() {
      return List.of("payment", "csv", "example");
    }

    @Override
    public Duration getEstimatedRuntime() {
      return Duration.ofSeconds(5);
    }
  }
}
