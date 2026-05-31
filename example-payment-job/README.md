# example-payment-job

Example Spring Batch job plugin that processes a CSV of payment transactions and logs a summary (row count + total amount).

## Prerequisites

- Java 21
- Maven 3.9+
- `com.fronzec:batch-job-api:0.0.1-SNAPSHOT` installed locally

Install the API module first if you are working from the monorepo:

```bash
mvn -f batch-job-api/pom.xml clean install
```

## Build

```bash
cd example-payment-job
mvn clean package
```

This produces a shaded JAR at `target/example-payment-job-1.0.0.jar`. Only job-specific dependencies are bundled — platform libraries (Spring Batch, batch-job-api) are excluded via the shade plugin.

## Upload to the Platform

```bash
curl -u admin:admin123 \
  -F "file=@target/example-payment-job-1.0.0.jar" \
  -F "jobName=payment-job" \
  -F "version=1.0.0" \
  -F "mainClassName=com.example.payment.PaymentJobPlugin" \
  http://localhost:8080/api/batch-service/jobs/upload
```

## Approve the Job Definition

```bash
curl -u admin:admin123 \
  -X PUT \
  -H "Content-Type: application/json" \
  -d '{"approvedBy":"admin"}' \
  http://localhost:8080/api/batch-service/jobs/definitions/1/approve
```

## Load the Plugin

```bash
curl -u admin:admin123 \
  -X POST \
  http://localhost:8080/api/batch-service/jobs/definitions/1/load
```

## Execute the Job

```bash
curl -u admin:admin123 \
  -X POST \
  -H "Content-Type: application/json" \
  -H "X-User: admin" \
  -d '{"job_bean_name":"payment-job","date":"2024-03-15","execution_attempt_number":"1","description":"Manual test run"}' \
  http://localhost:8080/api/batch-service/jobs/start-single/sync
```

## Unload the Plugin

```bash
curl -u admin:admin123 \
  -X POST \
  http://localhost:8080/api/batch-service/jobs/definitions/1/unload
```

## Project Structure

```
example-payment-job/
├── pom.xml                                      # Maven build (shade plugin, provided-scope deps)
└── src/main/
    ├── java/com/example/payment/
    │   └── PaymentJobPlugin.java                # BatchJobPlugin implementation
    └── resources/
        ├── data/
        │   └── sample-payments.csv              # Sample input data
        └── META-INF/services/
            └── com.fronzec.api.BatchJobPlugin   # SPI registration
```

## Key Design Decisions

- **Provided scope**: `batch-job-api`, `spring-batch-core`, `spring-context`, and `spring-tx` are declared `provided` — the platform supplies them at runtime.
- **Shade plugin**: Bundles job-specific dependencies while excluding platform libraries to avoid classpath conflicts.
- **Named inner class**: `PaymentJobMetadata` is a named inner class so its `.class` file is discoverable by the dynamic classloader.
