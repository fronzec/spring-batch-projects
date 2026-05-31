package com.fronzec.frbatchservice.batchjobs.plugins.health;

import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the operational health of the plugin system.
 *
 * <p>Evaluates three signals:
 * <ul>
 *   <li><b>loaded</b> — number of plugins currently registered in {@link PluginRegistryService}</li>
 *   <li><b>failed</b> — count of job definitions whose {@code loadStatus} is {@code FAILED}</li>
 *   <li><b>jarDirAccessible</b> — whether the configured JAR storage directory is readable</li>
 * </ul>
 *
 * <p>The overall status is {@code DOWN} when any FAILED definition exists or the JAR
 * directory is not accessible; otherwise {@code UP}.
 */
@Component
public class PluginHealthIndicator implements HealthIndicator {

  private final PluginRegistryService pluginRegistryService;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final String jarDir;

  public PluginHealthIndicator(
      PluginRegistryService pluginRegistryService,
      JobDefinitionRepository jobDefinitionRepository,
      @Value("${fr-batch-service.plugins.jar-dir}") String jarDir) {
    this.pluginRegistryService = pluginRegistryService;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.jarDir = jarDir;
  }

  @Override
  public Health health() {
    int loaded = pluginRegistryService.getPlugins().size();
    long failed = jobDefinitionRepository.countByLoadStatus("FAILED");
    boolean jarDirAccessible = Path.of(jarDir).toFile().canRead();

    Health.Builder builder =
        (failed > 0 || !jarDirAccessible) ? Health.down() : Health.up();

    builder
        .withDetail("loaded", loaded)
        .withDetail("failed", failed)
        .withDetail("jarDirAccessible", jarDirAccessible);

    return builder.build();
  }
}
