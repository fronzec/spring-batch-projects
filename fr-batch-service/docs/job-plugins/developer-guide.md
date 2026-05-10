# Job Plugins Developer Guide

This guide explains how to create and package a Spring Batch job as an external plugin compatible with the `batch-job-api` contract.

## What you build

A job plugin is an executable JAR that:

- Implements `com.fronzec.api.BatchJobPlugin`
- Provides metadata through `com.fronzec.api.JobMetadata`
- Is discoverable via Java Service Provider Interface (SPI)

Later phases of the platform will load this JAR dynamically at runtime.

## Prerequisites

- Java 21
- Maven 3.9+
- Access to `com.fronzec:batch-job-api:0.0.1-SNAPSHOT`

If you're developing locally, install the API module first:

```bash
mvn -f batch-job-api/pom.xml clean install
```

## Plugin project structure (Maven)

Recommended structure:

```
example-payment-job/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/payment/
        │       └── PaymentJobPlugin.java
        └── resources/
            └── META-INF/
                └── services/
                    └── com.fronzec.api.BatchJobPlugin
```

## pom.xml example

Key ideas:

- `batch-job-api` should be `provided` (the platform provides it)
- Spring Batch dependencies should be `provided` (the platform provides them)
- Job-specific dependencies can be packaged (via Shade)

```xml
<project>
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>example-payment-job</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.fronzec</groupId>
      <artifactId>batch-job-api</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <scope>provided</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework.batch</groupId>
      <artifactId>spring-batch-core</artifactId>
      <version>5.2.0</version>
      <scope>provided</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.0</version>
      <scope>provided</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-tx</artifactId>
      <version>6.2.0</version>
      <scope>provided</scope>
    </dependency>

    <!-- Job-specific dependencies (example) -->
    <!--
    <dependency>
      <groupId>com.some-vendor</groupId>
      <artifactId>payment-sdk</artifactId>
      <version>2.1.0</version>
    </dependency>
    -->
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.6.0</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <artifactSet>
                <excludes>
                  <!-- Exclude platform-provided APIs -->
                  <exclude>com.fronzec:batch-job-api</exclude>
                  <exclude>org.springframework.batch:spring-batch-core</exclude>
                  <exclude>org.springframework:spring-context</exclude>
                  <exclude>org.springframework:spring-tx</exclude>
                </excludes>
              </artifactSet>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

Note: use the same Spring versions as the platform to avoid compatibility problems.

## Implementing BatchJobPlugin

Minimal example:

```java
package com.example.payment;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

public class PaymentJobPlugin implements BatchJobPlugin {

  @Override
  public String getJobName() {
    return "payment-processing";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public Job configureJob(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ApplicationContext parentContext) {

    // Example: retrieve shared platform beans
    // var dataSource = parentContext.getBean(DataSource.class);

    var step =
        new StepBuilder("paymentProcessingStep", jobRepository)
            .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
            .build();

    return new JobBuilder(getJobName(), jobRepository).start(step).build();
  }

  @Override
  public Map<String, String> getDefaultParameters() {
    return Map.of("DATE", "2024-01-01", "BATCH_SIZE", "1000");
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
        return "Payment Processing Job";
      }

      @Override
      public String getDescription() {
        return "Processes daily payment transactions and generates reports.";
      }

      @Override
      public String getAuthor() {
        return "Payment Team";
      }

      @Override
      public List<String> getTags() {
        return List.of("payment", "financial", "daily");
      }

      @Override
      public Duration getEstimatedRuntime() {
        return Duration.ofMinutes(30);
      }
    };
  }
}
```

## SPI registration (ServiceLoader)

Create the file:

`src/main/resources/META-INF/services/com.fronzec.api.BatchJobPlugin`

With the fully qualified class name of your plugin implementation:

```
com.example.payment.PaymentJobPlugin
```

## Build the plugin JAR

```bash
mvn clean package
```

The resulting shaded artifact should be a single JAR you can upload to the platform (in later phases).

## Versioning guidance

- Use Semantic Versioning (e.g. `1.2.3`) for `getVersion()`
- Do not change `getJobName()` across versions of the same job
- If you introduce breaking changes, publish a new major version

## Compatibility checklist

- The plugin compiles against `batch-job-api`
- Platform-provided libraries are marked as `provided` and not shaded into the plugin
- The SPI file is present and correct
- `getJobName()` is stable and unique
- Defaults returned by `getDefaultParameters()` are safe for non-production environments
