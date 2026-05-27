/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistrationException;
import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class DynamicJobLoaderServiceTest {

  @Mock private JobDefinitionRepository jobDefinitionRepository;
  @Mock private PluginRegistryService pluginRegistryService;
  @Mock private ApplicationContext applicationContext;
  @Mock private JobRepository jobRepository;
  @Mock private PlatformTransactionManager transactionManager;

  @InjectMocks private DynamicJobLoaderService service;

  private Path tempDir;
  private String validJarPath;

  // ── Setup / teardown ──────────────────────────────────────────────────────

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("loader-service-test-");
    // Create a real (empty) file so JAR existence validation passes
    Path jarFile = tempDir.resolve("test-plugin.jar");
    Files.createFile(jarFile);
    validJarPath = jarFile.toString();
  }

  @AfterEach
  void tearDown() {
    if (tempDir != null) {
      try (var walk = Files.walk(tempDir)) {
        walk
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException ignored) {
                    // best-effort cleanup
                  }
                });
      } catch (IOException ignored) {
        // best-effort cleanup
      }
    }
  }

  // ── Helper: entity stub ───────────────────────────────────────────────────

  private JobDefinitionEntity createEntity(
      long id, String jobName, boolean enabled, String loadStatus) {
    JobDefinitionEntity entity = new JobDefinitionEntity();
    entity.setId(id);
    entity.setJobName(jobName);
    entity.setJarFilePath(validJarPath);
    entity.setMainClassName("com.test.TestPlugin");
    entity.setEnabled(enabled);
    entity.setLoadStatus(loadStatus);
    return entity;
  }

  // ── Successful load ───────────────────────────────────────────────────────

  @Test
  void loadJob_validPlugin_registersAndReturnsLoaded() throws Exception {
    JobDefinitionEntity entity = createEntity(1L, "test-job", true, null);

    when(jobDefinitionRepository.findById(1L)).thenReturn(Optional.of(entity));
    doNothing()
        .when(pluginRegistryService)
        .registerDynamicPlugin(any(BatchJobPlugin.class), any());

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenAnswer(inv -> TestBatchJobPlugin.class);
            })) {

      LoadResult result = service.loadJob(1L);

      assertEquals("test-job", result.jobName());
      assertEquals(LoadResult.LOADED, result.status());
      assertTrue(result.message().contains("Successfully loaded"));
      assertEquals(LoadResult.LOADED, entity.getLoadStatus());
      assertNull(entity.getLoadError());

      verify(pluginRegistryService)
          .registerDynamicPlugin(any(BatchJobPlugin.class), any(DynamicJobClassLoader.class));
    }
  }

  // ── Load failures ─────────────────────────────────────────────────────────

  @Test
  void loadJob_missingJarFile_setsFailed() {
    JobDefinitionEntity entity = createEntity(2L, "missing-jar-job", true, null);
    entity.setJarFilePath("/nonexistent/path.jar");

    when(jobDefinitionRepository.findById(2L)).thenReturn(Optional.of(entity));

    JobLoadException ex = assertThrows(JobLoadException.class, () -> service.loadJob(2L));
    assertTrue(ex.getMessage().contains("JAR file not found"));
    assertEquals(LoadResult.FAILED, entity.getLoadStatus());
    assertNotNull(entity.getLoadError());
  }

  @Test
  void loadJob_classNotImplementingBatchJobPlugin_setsFailed() throws Exception {
    JobDefinitionEntity entity = createEntity(3L, "bad-plugin-job", true, null);

    when(jobDefinitionRepository.findById(3L)).thenReturn(Optional.of(entity));

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin")).thenAnswer(inv -> String.class);
            })) {

      JobLoadException ex = assertThrows(JobLoadException.class, () -> service.loadJob(3L));
      assertTrue(
          ex.getMessage().contains("does not implement BatchJobPlugin"),
          "Expected 'does not implement' but got: " + ex.getMessage());
      assertEquals(LoadResult.FAILED, entity.getLoadStatus());
      assertNotNull(entity.getLoadError());
    }
  }

  @Test
  void loadJob_nameMismatch_setsFailed() throws Exception {
    JobDefinitionEntity entity = createEntity(4L, "other-name", true, null);

    when(jobDefinitionRepository.findById(4L)).thenReturn(Optional.of(entity));

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenAnswer(inv -> TestBatchJobPlugin.class);
            })) {

      JobLoadException ex = assertThrows(JobLoadException.class, () -> service.loadJob(4L));
      assertTrue(
          ex.getMessage().contains("declares jobName="),
          "Expected 'declares jobName=' but got: " + ex.getMessage());
      assertEquals(LoadResult.FAILED, entity.getLoadStatus());
    }
  }

  @Test
  void loadJob_classNotFound_setsFailed() throws Exception {
    JobDefinitionEntity entity = createEntity(5L, "bad-class-job", true, null);

    when(jobDefinitionRepository.findById(5L)).thenReturn(Optional.of(entity));

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenThrow(new ClassNotFoundException("com.test.TestPlugin"));
            })) {

      JobLoadException ex = assertThrows(JobLoadException.class, () -> service.loadJob(5L));
      assertTrue(
          ex.getMessage().contains("Failed to load job"),
          "Expected 'Failed to load job' but got: " + ex.getMessage());
      assertEquals(LoadResult.FAILED, entity.getLoadStatus());
      assertNotNull(entity.getLoadError());
    }
  }

  @Test
  void loadJob_configureJobFails_setsFailed() throws Exception {
    JobDefinitionEntity entity = createEntity(17L, "test-job", true, null);

    when(jobDefinitionRepository.findById(17L)).thenReturn(Optional.of(entity));
    doThrow(new PluginRegistrationException("configureJob failed"))
        .when(pluginRegistryService)
        .registerDynamicPlugin(any(BatchJobPlugin.class), any());

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenAnswer(inv -> TestBatchJobPlugin.class);
            })) {

      JobLoadException ex = assertThrows(JobLoadException.class, () -> service.loadJob(17L));
      assertTrue(
          ex.getMessage().contains("configureJob failed"),
          "Expected 'configureJob failed' but got: " + ex.getMessage());
      assertEquals(LoadResult.FAILED, entity.getLoadStatus());
      assertNotNull(entity.getLoadError());
    }
  }

  // ── Pre-condition validations ─────────────────────────────────────────────

  @Test
  void loadJob_definitionNotFound_throwsNoSuchElementException() {
    when(jobDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(NoSuchElementException.class, () -> service.loadJob(999L));
  }

  @Test
  void loadJob_disabledDefinition_throwsIllegalStateException() {
    JobDefinitionEntity entity = createEntity(6L, "disabled-job", false, null);

    when(jobDefinitionRepository.findById(6L)).thenReturn(Optional.of(entity));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.loadJob(6L));
    assertTrue(ex.getMessage().contains("disabled"));
  }

  @Test
  void loadJob_alreadyLoaded_throwsIllegalStateException() {
    JobDefinitionEntity entity = createEntity(7L, "already-loaded", true, LoadResult.LOADED);

    when(jobDefinitionRepository.findById(7L)).thenReturn(Optional.of(entity));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.loadJob(7L));
    assertTrue(ex.getMessage().contains("already loaded"));
  }

  // ── Unload ────────────────────────────────────────────────────────────────

  @Test
  void unloadJob_success_updatesStatus() {
    JobDefinitionEntity entity =
        createEntity(8L, "unloadable-job", true, LoadResult.LOADED);

    when(jobDefinitionRepository.findById(8L)).thenReturn(Optional.of(entity));
    when(jobRepository.findRunningJobExecutions("unloadable-job"))
        .thenReturn(Collections.emptySet());
    doNothing().when(pluginRegistryService).unregisterDynamicPlugin("unloadable-job");

    LoadResult result = service.unloadJob(8L, false);

    assertEquals("unloadable-job", result.jobName());
    assertEquals(LoadResult.UNLOADED, result.status());
    assertEquals(LoadResult.UNLOADED, entity.getLoadStatus());
    verify(pluginRegistryService).unregisterDynamicPlugin("unloadable-job");
  }

  @Test
  void unloadJob_runningExecutions_notForced_throwsConflict() {
    JobDefinitionEntity entity =
        createEntity(9L, "running-job", true, LoadResult.LOADED);

    when(jobDefinitionRepository.findById(9L)).thenReturn(Optional.of(entity));
    when(jobRepository.findRunningJobExecutions("running-job"))
        .thenReturn(Set.of(mock(JobExecution.class)));

    JobUnloadConflictException ex =
        assertThrows(JobUnloadConflictException.class, () -> service.unloadJob(9L, false));
    assertTrue(ex.getMessage().contains("running execution"));
    verify(pluginRegistryService, never()).unregisterDynamicPlugin(any());
  }

  @Test
  void unloadJob_runningExecutions_forced_succeeds() {
    JobDefinitionEntity entity =
        createEntity(10L, "force-unload-job", true, LoadResult.LOADED);

    when(jobDefinitionRepository.findById(10L)).thenReturn(Optional.of(entity));
    doNothing().when(pluginRegistryService).unregisterDynamicPlugin("force-unload-job");

    LoadResult result = service.unloadJob(10L, true);

    assertEquals(LoadResult.UNLOADED, result.status());
    verify(pluginRegistryService).unregisterDynamicPlugin("force-unload-job");
  }

  @Test
  void unloadJob_definitionNotFound_throwsNoSuchElementException() {
    when(jobDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(NoSuchElementException.class, () -> service.unloadJob(999L, false));
  }

  // ── Reload ────────────────────────────────────────────────────────────────

  @Test
  void reloadJob_unloadsThenLoads_atomic() throws Exception {
    JobDefinitionEntity entity =
        createEntity(11L, "test-job", true, LoadResult.LOADED);

    when(jobDefinitionRepository.findById(11L)).thenReturn(Optional.of(entity));
    doNothing().when(pluginRegistryService).unregisterDynamicPlugin("test-job");
    doNothing()
        .when(pluginRegistryService)
        .registerDynamicPlugin(any(BatchJobPlugin.class), any());

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenAnswer(inv -> TestBatchJobPlugin.class);
            })) {

      LoadResult result = service.reloadJob(11L);

      assertEquals(LoadResult.LOADED, result.status());
      verify(pluginRegistryService).unregisterDynamicPlugin("test-job");
      verify(pluginRegistryService)
          .registerDynamicPlugin(any(BatchJobPlugin.class), any(DynamicJobClassLoader.class));
    }
  }

  // ── loadAllEnabled ────────────────────────────────────────────────────────

  @Test
  void loadAllEnabled_skipsAlreadyLoaded_loadsOthers() throws Exception {
    JobDefinitionEntity loaded =
        createEntity(12L, "already-loaded", true, LoadResult.LOADED);
    JobDefinitionEntity pending = createEntity(13L, "test-job", true, null);

    when(jobDefinitionRepository.findByEnabled(true)).thenReturn(List.of(loaded, pending));
    when(jobDefinitionRepository.findById(13L)).thenReturn(Optional.of(pending));
    doNothing()
        .when(pluginRegistryService)
        .registerDynamicPlugin(any(BatchJobPlugin.class), any());

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenAnswer(inv -> TestBatchJobPlugin.class);
            })) {

      Map<String, LoadResult> results = service.loadAllEnabled();

      assertEquals(2, results.size());
      assertTrue(results.containsKey("already-loaded"));
      assertEquals(LoadResult.LOADED, results.get("already-loaded").status());
      assertTrue(results.get("already-loaded").message().contains("Already loaded"));

      assertTrue(results.containsKey("test-job"));
      assertEquals(LoadResult.LOADED, results.get("test-job").status());
    }
  }

  @Test
  void loadAllEnabled_oneFails_othersStillLoaded() throws Exception {
    JobDefinitionEntity good = createEntity(14L, "test-job", true, null);
    JobDefinitionEntity bad = createEntity(15L, "bad-job", true, null);
    bad.setJarFilePath("/nonexistent/bad.jar");

    when(jobDefinitionRepository.findByEnabled(true)).thenReturn(List.of(good, bad));
    when(jobDefinitionRepository.findById(14L)).thenReturn(Optional.of(good));
    when(jobDefinitionRepository.findById(15L)).thenReturn(Optional.of(bad));
    doNothing()
        .when(pluginRegistryService)
        .registerDynamicPlugin(any(BatchJobPlugin.class), any());

    try (MockedConstruction<DynamicJobClassLoader> mocked =
        mockConstruction(
            DynamicJobClassLoader.class,
            (mock, ctx) -> {
              when(mock.loadClass("com.test.TestPlugin"))
                  .thenAnswer(inv -> TestBatchJobPlugin.class);
            })) {

      Map<String, LoadResult> results = service.loadAllEnabled();

      assertEquals(2, results.size());
      assertEquals(LoadResult.LOADED, results.get("test-job").status());
      assertEquals(LoadResult.FAILED, results.get("bad-job").status());
      assertTrue(
          results.get("bad-job").message().contains("JAR file not found"),
          "Expected 'JAR file not found' but got: " + results.get("bad-job").message());
    }
  }

  // ── Inner test plugin ─────────────────────────────────────────────────────

  /**
   * Minimal {@link BatchJobPlugin} implementation used in {@code mockConstruction} tests where the
   * mocked classloader returns this class.
   *
   * <p>Must be {@code public} so it can be instantiated via reflection from a mocked classloader
   * context.
   */
  public static class TestBatchJobPlugin implements BatchJobPlugin {

    @Override
    public String getJobName() {
      return "test-job";
    }

    @Override
    public String getVersion() {
      return "1.0-test";
    }

    @Override
    public Job configureJob(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ApplicationContext parentContext) {
      return new JobBuilder("test-job", jobRepository)
          .start(
              new StepBuilder("test-step", jobRepository)
                  .tasklet((c, cc) -> RepeatStatus.FINISHED)
                  .build())
          .build();
    }

    @Override
    public Map<String, String> getDefaultParameters() {
      return Collections.emptyMap();
    }

    @Override
    public List<String> getRequiredDependencies() {
      return Collections.emptyList();
    }

    @Override
    public JobMetadata getMetadata() {
      return new JobMetadata() {
        @Override
        public String getDisplayName() {
          return "Test Plugin";
        }

        @Override
        public String getDescription() {
          return "Test BatchJobPlugin implementation";
        }

        @Override
        public String getAuthor() {
          return "test";
        }

        @Override
        public List<String> getTags() {
          return Collections.emptyList();
        }

        @Override
        public Duration getEstimatedRuntime() {
          return Duration.ZERO;
        }
      };
    }
  }
}
