package com.fronzec.frbatchservice.batchjobs.plugins.metrics;

import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Centralizes Micrometer metric registrations for the plugin subsystem.
 *
 * <p>Counters track cumulative events since process start; the gauge reports the
 * current number of loaded plugins; timers measure load and unload duration.
 * Service classes call the {@code increment*} and {@code record*Duration} methods
 * rather than injecting {@link MeterRegistry} directly.
 */
@Component
public class PluginMetrics {

  private final MeterRegistry meterRegistry;
  private final PluginRegistryService pluginRegistryService;

  private Counter uploadedCounter;
  private Counter loadedCounter;
  private Counter unloadedCounter;
  private Timer loadDurationTimer;
  private Timer unloadDurationTimer;

  public PluginMetrics(
      MeterRegistry meterRegistry, PluginRegistryService pluginRegistryService) {
    this.meterRegistry = meterRegistry;
    this.pluginRegistryService = pluginRegistryService;
  }

  @PostConstruct
  public void init() {
    uploadedCounter =
        Counter.builder("plugins.uploaded.total")
            .description("Total uploaded JAR plugins")
            .register(meterRegistry);

    loadedCounter =
        Counter.builder("plugins.loaded.total")
            .description("Total loaded plugins")
            .register(meterRegistry);

    unloadedCounter =
        Counter.builder("plugins.unloaded.total")
            .description("Total unloaded plugins")
            .register(meterRegistry);

    Gauge.builder("plugins.loaded.count", pluginRegistryService, r -> r.getPlugins().size())
        .description("Currently loaded plugins count")
        .register(meterRegistry);

    loadDurationTimer =
        Timer.builder("plugins.load.duration")
            .description("Plugin load duration")
            .register(meterRegistry);

    unloadDurationTimer =
        Timer.builder("plugins.unload.duration")
            .description("Plugin unload duration")
            .register(meterRegistry);
  }

  /** Increments the upload counter — called after a JAR is successfully persisted. */
  public void incrementUpload() {
    uploadedCounter.increment();
  }

  /** Increments the load counter — called after a plugin is successfully loaded. */
  public void incrementLoad() {
    loadedCounter.increment();
  }

  /** Increments the unload counter — called after a plugin is successfully unloaded. */
  public void incrementUnload() {
    unloadedCounter.increment();
  }

  /** Increments the per-job-name execution counter. */
  public void incrementExecuted(String jobName) {
    Counter.builder("plugins.executed.total")
        .description("Total plugin job executions")
        .tag("job_name", jobName)
        .register(meterRegistry)
        .increment();
  }

  /** Records the measured load duration in the load-duration timer. */
  public void recordLoadDuration(long millis) {
    loadDurationTimer.record(millis, TimeUnit.MILLISECONDS);
  }

  /** Records the measured unload duration in the unload-duration timer. */
  public void recordUnloadDuration(long millis) {
    unloadDurationTimer.record(millis, TimeUnit.MILLISECONDS);
  }
}
