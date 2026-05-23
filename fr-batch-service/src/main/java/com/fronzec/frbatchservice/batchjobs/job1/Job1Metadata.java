package com.fronzec.frbatchservice.batchjobs.job1;

import com.fronzec.api.JobMetadata;
import java.time.Duration;
import java.util.List;

/** Metadata descriptor for the job1 ETL pipeline. Package-private — consumed only by {@link Job1Plugin}. */
class Job1Metadata implements JobMetadata {

    @Override
    public String getDisplayName() {
        return "Job1 ETL Pipeline";
    }

    @Override
    public String getDescription() {
        return "3-step ETL: CSV → DB → transform → REST";
    }

    @Override
    public String getAuthor() {
        return "fronzec";
    }

    @Override
    public List<String> getTags() {
        return List.of("etl", "csv", "rest");
    }

    @Override
    public Duration getEstimatedRuntime() {
        return Duration.ofMinutes(5);
    }
}
