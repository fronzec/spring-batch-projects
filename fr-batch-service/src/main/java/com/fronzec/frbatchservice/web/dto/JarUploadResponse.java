/* 2024-2025 */
package com.fronzec.frbatchservice.web.dto;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import java.nio.file.Path;

/**
 * DTO returned by {@code POST /jobs/upload} on success.
 *
 * <p>Exposes only the safe JAR filename — never the server-side filesystem path.
 */
public record JarUploadResponse(
        long id, String jobName, String version, String jarChecksum, String jarFileName) {

    /** Builds a {@link JarUploadResponse} from a persisted {@link JobDefinitionEntity}. */
    public static JarUploadResponse fromEntity(JobDefinitionEntity entity) {
        return new JarUploadResponse(
                entity.getId(),
                entity.getJobName(),
                entity.getVersion(),
                entity.getJarChecksum(),
                Path.of(entity.getJarFilePath()).getFileName().toString());
    }
}
