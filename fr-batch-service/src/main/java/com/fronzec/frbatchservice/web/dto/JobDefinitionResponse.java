/* 2024-2025 */
package com.fronzec.frbatchservice.web.dto;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * DTO for job definition summary and detail views.
 *
 * <p>Used by {@code GET /jobs/definitions} (list) and
 * {@code GET /jobs/definitions/{id}} (single detail).
 *
 * <p>Fields use camelCase; Jackson's {@code SNAKE_CASE} naming strategy
 * converts to {@code snake_case} in the JSON response automatically.
 */
public record JobDefinitionResponse(
        long id,
        String jobName,
        String displayName,
        String version,
        Boolean enabled,
        String loadStatus,
        String jarFileName,
        String mainClassName,
        LocalDateTime createdAt) {

    /** Builds a {@link JobDefinitionResponse} from a persisted {@link JobDefinitionEntity}. */
    public static JobDefinitionResponse fromEntity(JobDefinitionEntity entity) {
        return new JobDefinitionResponse(
                entity.getId(),
                entity.getJobName(),
                entity.getDisplayName(),
                entity.getVersion(),
                entity.getEnabled(),
                entity.getLoadStatus(),
                Path.of(entity.getJarFilePath()).getFileName().toString(),
                entity.getMainClassName(),
                entity.getCreatedAt());
    }
}
