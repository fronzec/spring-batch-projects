/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Auto-loads all enabled, not-yet-loaded plugin definitions on application startup.
 *
 * <p>Runs via {@link ApplicationRunner} after the full application context is
 * initialised, so repository, registry, and transaction manager dependencies are
 * ready. Individual plugin failures are logged at {@code WARN} level but do
 * <strong>not</strong> block startup.
 *
 * <p>Package-private scope — this is infrastructure wiring, not a public API
 * entry point.
 */
@Component
class PluginAutoLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PluginAutoLoader.class);

  private final DynamicJobLoaderService loaderService;

  PluginAutoLoader(DynamicJobLoaderService loaderService) {
    this.loaderService = loaderService;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("Starting auto-load of enabled plugins...");
    try {
      Map<String, LoadResult> results = loaderService.loadAllEnabled();

      long loadedCount =
          results.values().stream().filter(r -> LoadResult.LOADED.equals(r.status())).count();
      long alreadyLoadedCount =
          results.values().stream()
              .filter(r -> LoadResult.LOADED.equals(r.status()) && "Already loaded".equals(r.message()))
              .count();
      long failedCount =
          results.values().stream().filter(r -> LoadResult.FAILED.equals(r.status())).count();

      log.info(
          "Auto-load complete: {} newly loaded, {} already loaded, {} failed ({} total)",
          loadedCount - alreadyLoadedCount,
          alreadyLoadedCount,
          failedCount,
          results.size());

      if (failedCount > 0) {
        results.values().stream()
            .filter(r -> LoadResult.FAILED.equals(r.status()))
            .forEach(r -> log.warn("  Failed to auto-load '{}': {}", r.jobName(), r.message()));
      }
    } catch (Exception e) {
      log.warn("Auto-load skipped — database may not be ready: {}", e.getMessage());
    }
  }
}
