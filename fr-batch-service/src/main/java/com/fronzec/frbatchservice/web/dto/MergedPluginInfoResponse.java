/* 2024-2025 */
package com.fronzec.frbatchservice.web.dto;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import java.util.List;
import java.util.Map;

/**
 * Extended plugin-listing DTO returned by the merged {@code GET /jobs/plugins} endpoint.
 *
 * <p>Unifies classpath-registered plugins and database-registered (uploaded) job
 * definitions into a single response list. Classpath entries take precedence on
 * job-name collision.
 *
 * <p>Fields use camelCase; Jackson's {@code SNAKE_CASE} naming strategy converts
 * to {@code snake_case} in the JSON response automatically.
 */
public record MergedPluginInfoResponse(
    String jobName,
    String version,
    String displayName,
    String description,
    String author,
    List<String> tags,
    Long estimatedRuntimeSeconds,
    Map<String, String> defaultParameters,
    PluginSource source,
    PluginStatus status) {

  /** Where the plugin definition originated. */
  public enum PluginSource {
    CLASSPATH,
    UPLOADED
  }

  /** Current lifecycle state of the plugin. */
  public enum PluginStatus {
    /** Registered in JobRegistry and able to run (classpath plugins). */
    ACTIVE,
    /** DB definition with {@code enabled=true} but not yet loaded. */
    ENABLED,
    /** DB definition with {@code enabled=false}. */
    DISABLED,
    /** DB definition whose JAR has been loaded into the classloader. */
    LOADED
  }

  /**
   * Builds a {@link MergedPluginInfoResponse} from a classpath-registered
   * {@link BatchJobPlugin}.
   */
  public static MergedPluginInfoResponse fromClasspath(BatchJobPlugin plugin) {
    JobMetadata metadata = plugin.getMetadata();
    return new MergedPluginInfoResponse(
        plugin.getJobName(),
        plugin.getVersion(),
        metadata.getDisplayName(),
        metadata.getDescription(),
        metadata.getAuthor(),
        metadata.getTags(),
        metadata.getEstimatedRuntime() == null
            ? null
            : metadata.getEstimatedRuntime().toSeconds(),
        plugin.getDefaultParameters(),
        PluginSource.CLASSPATH,
        PluginStatus.ACTIVE);
  }

  /**
   * Builds a {@link MergedPluginInfoResponse} from a database-registered
   * {@link JobDefinitionEntity}.
   *
   * <p>Status is derived from the entity's {@code enabled} flag and
   * {@code loadStatus} column.
   */
  public static MergedPluginInfoResponse fromEntity(JobDefinitionEntity entity) {
    PluginStatus status;
    if ("LOADED".equalsIgnoreCase(entity.getLoadStatus())) {
      status = PluginStatus.LOADED;
    } else if (Boolean.TRUE.equals(entity.getEnabled())) {
      status = PluginStatus.ENABLED;
    } else {
      status = PluginStatus.DISABLED;
    }

    return new MergedPluginInfoResponse(
        entity.getJobName(),
        entity.getVersion(),
        entity.getDisplayName(),
        entity.getDescription(),
        /* author — not tracked in JobDefinitionEntity */ null,
        /* tags — not tracked in JobDefinitionEntity */ null,
        /* estimatedRuntimeSeconds — not tracked in JobDefinitionEntity */ null,
        /* defaultParameters — not tracked in JobDefinitionEntity */ null,
        PluginSource.UPLOADED,
        status);
  }
}
