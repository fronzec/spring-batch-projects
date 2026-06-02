package com.fronzec.frbatchservice.batchjobs.plugins.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PluginMetricsTest {

  @Mock private PluginRegistryService pluginRegistryService;

  private MeterRegistry meterRegistry;
  private PluginMetrics pluginMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    pluginMetrics = new PluginMetrics(meterRegistry, pluginRegistryService);
    pluginMetrics.init();
  }

  @Test
  void incrementUpload_increasesCounter() {
    pluginMetrics.incrementUpload();

    Counter counter = meterRegistry.find("plugins.uploaded.total").counter();
    assertEquals(1.0, counter.count(), 0.01);
  }

  @Test
  void incrementLoad_increasesCounter() {
    pluginMetrics.incrementLoad();

    Counter counter = meterRegistry.find("plugins.loaded.total").counter();
    assertEquals(1.0, counter.count(), 0.01);
  }

  @Test
  void incrementUnload_increasesCounter() {
    pluginMetrics.incrementUnload();

    Counter counter = meterRegistry.find("plugins.unloaded.total").counter();
    assertEquals(1.0, counter.count(), 0.01);
  }

  @Test
  void multipleIncrements_accumulateCorrectly() {
    pluginMetrics.incrementUpload();
    pluginMetrics.incrementUpload();
    pluginMetrics.incrementUpload();

    Counter counter = meterRegistry.find("plugins.uploaded.total").counter();
    assertEquals(3.0, counter.count(), 0.01);
  }

  @Test
  void gaugeReadsCurrentLoadedPlugins() {
    BatchJobPlugin mockPlugin = org.mockito.Mockito.mock(BatchJobPlugin.class);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of(mockPlugin));

    Gauge gauge = meterRegistry.find("plugins.loaded.count").gauge();
    assertEquals(1.0, gauge.value(), 0.01);
  }

  @Test
  void gaugeReadsZeroWhenNoPlugins() {
    when(pluginRegistryService.getPlugins()).thenReturn(List.of());

    Gauge gauge = meterRegistry.find("plugins.loaded.count").gauge();
    assertEquals(0.0, gauge.value(), 0.01);
  }

  @Test
  void recordLoadDuration_recordsInTimer() {
    pluginMetrics.recordLoadDuration(250);

    Timer timer = meterRegistry.find("plugins.load.duration").timer();
    assertEquals(1, timer.count());
    assertEquals(250, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1.0);
  }

  @Test
  void recordUnloadDuration_recordsInTimer() {
    pluginMetrics.recordUnloadDuration(100);

    Timer timer = meterRegistry.find("plugins.unload.duration").timer();
    assertEquals(1, timer.count());
    assertEquals(100, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1.0);
  }

  @Test
  void incrementExecuted_createsTaggedCounter() {
    pluginMetrics.incrementExecuted("payment-job");
    pluginMetrics.incrementExecuted("payment-job");
    pluginMetrics.incrementExecuted("report-job");

    Counter paymentCounter =
        meterRegistry.find("plugins.executed.total").tag("job_name", "payment-job").counter();
    Counter reportCounter =
        meterRegistry.find("plugins.executed.total").tag("job_name", "report-job").counter();
    assertEquals(2.0, paymentCounter.count(), 0.01);
    assertEquals(1.0, reportCounter.count(), 0.01);
  }
}
