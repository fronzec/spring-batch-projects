/* 2024-2025 */
package com.fronzec.frbatchservice.web.dto;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;

/**
 * DTO returned by {@code POST /jobs/upload} on success.
 *
 * <p>Effective URL (with context-path): {@code POST /api/batch-service/jobs/upload}
 */
public record JarUploadResponse(
        long id, String jobName, String version, String jarChecksum, String jarFilePath) {

    /** Builds a {@link JarUploadResponse} from a persisted {@link JobDefinitionEntity}. */
    public static JarUploadResponse fromEntity(JobDefinitionEntity entity) {
        return new JarUploadResponse(
                entity.getId(),
                entity.getJobName(),
                entity.getVersion(),
                entity.getJarChecksum(),
                entity.getJarFilePath());
    }
}
