/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import com.fronzec.api.JobMetadata;
import java.time.Duration;
import java.util.List;

/** Metadata descriptor for the job2 failed-dispatch recovery pipeline. Package-private — consumed
 * only by {@link Job2Plugin}. */
class Job2Metadata implements JobMetadata {

  @Override
  public String getDisplayName() {
    return "Job2 Failed Dispatch Recovery";
  }

  @Override
  public String getDescription() {
    return "Recovers ERROR-flagged dispatched groups by re-building and re-sending payloads";
  }

  @Override
  public String getAuthor() {
    return "fronzec";
  }

  @Override
  public List<String> getTags() {
    return List.of("recovery", "dispatch", "retry");
  }

  @Override
  public Duration getEstimatedRuntime() {
    return Duration.ofMinutes(2);
  }
}
