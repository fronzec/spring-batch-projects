/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fronzec.frbatchservice.batchjobs.plugins.health.PluginHealthIndicator;
import com.fronzec.frbatchservice.batchjobs.plugins.metrics.PluginMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test verifying the health indicator and metrics beans are correctly wired into the
 * Spring context and produce expected output.
 */
@SpringBootTest
@ActiveProfiles("test")
class PluginHealthMetricsIntegrationTest {

  @Autowired private PluginHealthIndicator pluginHealthIndicator;

  @Autowired private PluginMetrics pluginMetrics;

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void pluginHealthIndicator_isWired() {
    assertNotNull(pluginHealthIndicator, "PluginHealthIndicator should be in the context");
  }

  @Test
  void pluginMetrics_isWired() {
    assertNotNull(pluginMetrics, "PluginMetrics should be in the context");
  }

  @Test
  void meterRegistry_isWired() {
    assertNotNull(meterRegistry, "MeterRegistry should be in the context");
  }

  @Test
  void healthIndicator_returnsHealthWithAllDetails() {
    Health health = pluginHealthIndicator.health();
    assertNotNull(health);
    assertTrue(health.getDetails().containsKey("loaded"));
    assertTrue(health.getDetails().containsKey("failed"));
    assertTrue(health.getDetails().containsKey("jarDirAccessible"));
    // In a test context with no failed definitions and a valid jarDir, health should be UP
    long failed = (Long) health.getDetails().get("failed");
    boolean dirAccessible = (Boolean) health.getDetails().get("jarDirAccessible");
    if (failed == 0 && dirAccessible) {
      assertEquals("UP", health.getStatus().getCode());
    }
  }

  @Test
  void metrics_registerCountersAndGauge() {
    // Exercise metrics to ensure they are registered
    pluginMetrics.incrementUpload();
    pluginMetrics.incrementLoad();
    pluginMetrics.incrementUnload();

    // Verify counters exist in the registry
    assertNotNull(
        meterRegistry.find("plugins.uploaded.total").counter(),
        "plugins.uploaded.total counter should exist");
    assertNotNull(
        meterRegistry.find("plugins.loaded.total").counter(),
        "plugins.loaded.total counter should exist");
    assertNotNull(
        meterRegistry.find("plugins.unloaded.total").counter(),
        "plugins.unloaded.total counter should exist");

    // Verify gauge exists
    assertNotNull(
        meterRegistry.find("plugins.loaded.count").gauge(),
        "plugins.loaded.count gauge should exist");

    // Verify timers exist
    assertNotNull(
        meterRegistry.find("plugins.load.duration").timer(),
        "plugins.load.duration timer should exist");
    assertNotNull(
        meterRegistry.find("plugins.unload.duration").timer(),
        "plugins.unload.duration timer should exist");
  }

  @Test
  void executedCounter_withTag_isRegistered() {
    pluginMetrics.incrementExecuted("test-job");

    assertNotNull(
        meterRegistry.find("plugins.executed.total").tag("job_name", "test-job").counter(),
        "plugins.executed.total counter with tag should exist");
  }
}
