/* 2024-2025 */
package com.fronzec.frbatchservice.web;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import java.util.List;
import java.util.Map;

/**
 * DTO returned by {@code GET /jobs/plugins}.
 *
 * <p>Effective URL (with context-path): {@code GET /api/batch-service/jobs/plugins}
 */
public record PluginInfoResponse(
        String jobName,
        String version,
        String displayName,
        String description,
        String author,
        List<String> tags,
        Long estimatedRuntimeSeconds,
        Map<String, String> defaultParameters) {

    /**
     * Builds a {@link PluginInfoResponse} from a registered {@link BatchJobPlugin}.
     *
     * <p>{@link JobMetadata#getEstimatedRuntime()} is serialized as seconds (Long) to keep the JSON
     * portable without requiring Jackson's {@code JavaTimeModule} configuration.
     */
    public static PluginInfoResponse from(BatchJobPlugin plugin) {
        JobMetadata metadata = plugin.getMetadata();
        return new PluginInfoResponse(
                plugin.getJobName(),
                plugin.getVersion(),
                metadata.getDisplayName(),
                metadata.getDescription(),
                metadata.getAuthor(),
                metadata.getTags(),
                metadata.getEstimatedRuntime() == null
                        ? null
                        : metadata.getEstimatedRuntime().toSeconds(),
                plugin.getDefaultParameters());
    }
}
