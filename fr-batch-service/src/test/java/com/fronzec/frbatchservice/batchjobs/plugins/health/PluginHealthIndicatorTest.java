package com.fronzec.frbatchservice.batchjobs.plugins.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class PluginHealthIndicatorTest {

  @Mock private PluginRegistryService pluginRegistryService;
  @Mock private JobDefinitionRepository jobDefinitionRepository;

  @TempDir java.nio.file.Path tempDir;

  private String validJarDir;

  @BeforeEach
  void setUp() {
    validJarDir = tempDir.toString();
  }

  @Test
  void health_isUp_whenNoFailuresAndDirAccessible() {
    when(jobDefinitionRepository.countByLoadStatus("FAILED")).thenReturn(0L);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of());

    PluginHealthIndicator indicator =
        new PluginHealthIndicator(pluginRegistryService, jobDefinitionRepository, validJarDir);

    Health health = indicator.health();
    assertEquals(Status.UP, health.getStatus());
    assertEquals(0, health.getDetails().get("loaded"));
    assertEquals(0L, health.getDetails().get("failed"));
    assertEquals(true, health.getDetails().get("jarDirAccessible"));
  }

  @Test
  void health_isUp_whenPluginsLoadedAndNoFailures() {
    BatchJobPlugin mockPlugin = org.mockito.Mockito.mock(BatchJobPlugin.class);
    when(jobDefinitionRepository.countByLoadStatus("FAILED")).thenReturn(0L);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of(mockPlugin));

    PluginHealthIndicator indicator =
        new PluginHealthIndicator(pluginRegistryService, jobDefinitionRepository, validJarDir);

    Health health = indicator.health();
    assertEquals(Status.UP, health.getStatus());
    assertEquals(1, health.getDetails().get("loaded"));
    assertEquals(0L, health.getDetails().get("failed"));
  }

  @Test
  void health_isDown_whenFailedDefinitionsExist() {
    when(jobDefinitionRepository.countByLoadStatus("FAILED")).thenReturn(3L);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of());

    PluginHealthIndicator indicator =
        new PluginHealthIndicator(pluginRegistryService, jobDefinitionRepository, validJarDir);

    Health health = indicator.health();
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(3L, health.getDetails().get("failed"));
  }

  @Test
  void health_isDown_whenJarDirNotAccessible() {
    when(jobDefinitionRepository.countByLoadStatus("FAILED")).thenReturn(0L);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of());

    PluginHealthIndicator indicator =
        new PluginHealthIndicator(
            pluginRegistryService,
            jobDefinitionRepository,
            "/nonexistent/path/should/not/exist/for/test");

    Health health = indicator.health();
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(false, health.getDetails().get("jarDirAccessible"));
  }

  @Test
  void health_isDown_whenBothFailuresAndDirInaccessible() {
    when(jobDefinitionRepository.countByLoadStatus("FAILED")).thenReturn(1L);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of());

    PluginHealthIndicator indicator =
        new PluginHealthIndicator(
            pluginRegistryService,
            jobDefinitionRepository,
            "/nonexistent/path/should/not/exist/for/test");

    Health health = indicator.health();
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(1L, health.getDetails().get("failed"));
    assertEquals(false, health.getDetails().get("jarDirAccessible"));
  }

  @Test
  void health_detailsContainAllKeys() {
    when(jobDefinitionRepository.countByLoadStatus("FAILED")).thenReturn(0L);
    when(pluginRegistryService.getPlugins()).thenReturn(List.of());

    PluginHealthIndicator indicator =
        new PluginHealthIndicator(pluginRegistryService, jobDefinitionRepository, validJarDir);

    Health health = indicator.health();
    assertNotNull(health);
    assertEquals(3, health.getDetails().size());
    assertEquals(true, health.getDetails().containsKey("loaded"));
    assertEquals(true, health.getDetails().containsKey("failed"));
    assertEquals(true, health.getDetails().containsKey("jarDirAccessible"));
  }
}
