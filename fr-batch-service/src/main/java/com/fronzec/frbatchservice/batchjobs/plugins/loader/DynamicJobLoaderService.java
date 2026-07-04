/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import com.fronzec.frbatchservice.batchjobs.plugins.audit.AuditEvent;
import com.fronzec.frbatchservice.batchjobs.plugins.audit.AuditEventType;
import com.fronzec.frbatchservice.batchjobs.plugins.audit.AuditService;
import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import com.fronzec.frbatchservice.batchjobs.plugins.metrics.PluginMetrics;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import com.fronzec.frbatchservice.batchjobs.plugins.util.ChecksumUtil;
import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Orchestrates the full lifecycle of dynamically-loaded batch job plugins: load from JAR,
 * instantiate, configure, register, and unload.
 *
 * <p>Owns the {@link DynamicJobClassLoader} lifecycle via a {@link ConcurrentHashMap} keyed by
 * definition ID. Delegates registry operations to {@link PluginRegistryService}.
 */
@Service
public class DynamicJobLoaderService {

  private static final Logger log = LoggerFactory.getLogger(DynamicJobLoaderService.class);

  private final JobDefinitionRepository jobDefinitionRepository;
  private final PluginRegistryService pluginRegistryService;
  private final ApplicationContext applicationContext;
  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final AuditService auditService;
  private final PluginMetrics pluginMetrics;

  /** Tracks active classloaders by definition ID for cleanup on unload. */
  private final Map<Long, DynamicJobClassLoader> classLoaders = new ConcurrentHashMap<>();

  /**
   * Per-definition load locks. Serializes concurrent {@link #loadJob(Long)} calls for the same
   * definition ID so the already-loaded guard and the classloader/registry writes execute
   * atomically. Without this, two concurrent loads of the same definition both pass the guard, each
   * opens a JAR file-handle, and the loser's {@code classLoaders.put} silently overwrites the
   * winner's entry — leaking the winner's handle. {@link #loadJob(Long)} allocates an entry only
   * after confirming the definition exists, so unknown IDs cannot grow the map without bound.
   */
  private final Map<Long, ReentrantLock> loadLocks = new ConcurrentHashMap<>();

  public DynamicJobLoaderService(
      JobDefinitionRepository jobDefinitionRepository,
      PluginRegistryService pluginRegistryService,
      ApplicationContext applicationContext,
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      AuditService auditService,
      PluginMetrics pluginMetrics) {
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.pluginRegistryService = pluginRegistryService;
    this.applicationContext = applicationContext;
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.auditService = auditService;
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Loads a job definition from its JAR, instantiates the plugin, configures the Spring Batch
   * {@link org.springframework.batch.core.job.Job}, and registers it with the registry.
   *
   * @param definitionId the database ID of the job definition to load
   * @return a {@link LoadResult} describing the outcome
   * @throws NoSuchElementException if the definition does not exist
   * @throws IllegalStateException if the definition is disabled or already loaded
   * @throws JobLoadException if the load fails at any stage (bad JAR, class missing, configure
   *     failure, etc.)
   */
  public LoadResult loadJob(Long definitionId) {
    // Gate lock allocation on existence so unknown IDs (e.g. POST /definitions/{id}/load with a
    // bogus id) cannot grow loadLocks without bound. The authoritative existence check still runs
    // inside doLoadJob under the lock, guarding against a definition deleted between the two reads.
    if (jobDefinitionRepository.findById(definitionId).isEmpty()) {
      throw new NoSuchElementException("Job definition not found for id: " + definitionId);
    }
    ReentrantLock lock = loadLocks.computeIfAbsent(definitionId, k -> new ReentrantLock());
    lock.lock();
    try {
      return doLoadJob(definitionId);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Performs the actual load. Always invoked while holding the per-definition lock acquired in
   * {@link #loadJob(Long)}, so the already-loaded guard and the classloader/registry writes are
   * atomic with respect to other loads of the same definition.
   */
  private LoadResult doLoadJob(Long definitionId) {
    JobDefinitionEntity entity =
        jobDefinitionRepository
            .findById(definitionId)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Job definition not found for id: " + definitionId));

    // Pre-condition: must be enabled
    if (!Boolean.TRUE.equals(entity.getEnabled())) {
      throw new IllegalStateException(
          "Job definition \"" + entity.getJobName() + "\" (id=" + definitionId + ") is disabled");
    }

    // Approval guard: only APPROVED definitions can be loaded
    if (!"APPROVED".equals(entity.getApprovalStatus())) {
      throw new IllegalStateException(
          "Job definition \""
              + entity.getJobName()
              + "\" (id="
              + definitionId
              + ") must be approved before loading. Current status: "
              + entity.getApprovalStatus());
    }

    // Optimistic guard: already loaded in THIS JVM's registry. The persisted
    // loadStatus can be a stale LOADED after a restart (the registry is
    // in-memory and resets), so consult the live registry, not the DB column.
    if (pluginRegistryService.getRegisteredJobNames().contains(entity.getJobName())) {
      throw new IllegalStateException(
          "Job \"" + entity.getJobName() + "\" (id=" + definitionId + ") is already loaded");
    }

    // Mark LOADING so concurrent attempts see in-flight state
    entity.setLoadStatus("LOADING");
    entity.setLoadError(null);
    jobDefinitionRepository.save(entity);

    long loadStartNanos = System.nanoTime();
    DynamicJobClassLoader classLoader = null;
    try {
      // Validate JAR file exists
      File jarFile = new File(entity.getJarFilePath());
      if (!jarFile.exists()) {
        throw new JobLoadException(
            "JAR file not found: " + entity.getJarFilePath());
      }

      // Re-verify checksum: detect tampering that occurred after upload
      String storedChecksum = entity.getJarChecksum();
      String actualChecksum = ChecksumUtil.computeSha256(jarFile.toPath());
      if (!actualChecksum.equals(storedChecksum)) {
        entity.setLoadStatus(LoadResult.FAILED);
        entity.setLoadError(
            "Checksum mismatch: stored=" + storedChecksum + " actual=" + actualChecksum);
        jobDefinitionRepository.save(entity);
        throw new JobLoadException(
            "Checksum mismatch for job \""
                + entity.getJobName()
                + "\": stored="
                + storedChecksum
                + " actual="
                + actualChecksum);
      }

      // Create parent-last classloader
      URL[] jarUrls = new URL[] {jarFile.toURI().toURL()};
      classLoader =
          new DynamicJobClassLoader(jarUrls, getClass().getClassLoader(), entity.getJobName());

      // Load the plugin main class
      Class<?> pluginClass = classLoader.loadClass(entity.getMainClassName());

      // Validate it implements BatchJobPlugin
      if (!BatchJobPlugin.class.isAssignableFrom(pluginClass)) {
        throw new JobLoadException(
            "Main class "
                + entity.getMainClassName()
                + " does not implement BatchJobPlugin");
      }

      // Instantiate and verify
      BatchJobPlugin plugin = (BatchJobPlugin) pluginClass.getDeclaredConstructor().newInstance();

      if (!plugin.getJobName().equals(entity.getJobName())) {
        throw new JobLoadException(
            "Plugin declares jobName=\""
                + plugin.getJobName()
                + "\" but definition expects \""
                + entity.getJobName()
                + "\"");
      }

      // Register through existing hook — registerDynamicPlugin() internally calls
      // plugin.configureJob(...), validates the Job, and registers in JobRegistry
      pluginRegistryService.registerDynamicPlugin(plugin, classLoader);

      // Track classloader for future unload
      classLoaders.put(definitionId, classLoader);

      // Persist success
      entity.setLoadStatus(LoadResult.LOADED);
      entity.setLoadError(null);
      jobDefinitionRepository.save(entity);

      log.info(
          "Loaded plugin '{}' (id={}) from {}",
          entity.getJobName(),
          definitionId,
          entity.getJarFilePath());

      auditService.logEvent(
          new AuditEvent(
              AuditEventType.JOB_LOADED,
              entity.getJobName(),
              AuditService.currentUserId(),
              "Job loaded from " + entity.getJarFilePath(),
              AuditEvent.SUCCESS,
              LocalDateTime.now()));

      long durationMs =
          java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
              System.nanoTime() - loadStartNanos);
      pluginMetrics.incrementLoad();
      pluginMetrics.recordLoadDuration(durationMs);

      return new LoadResult(
          entity.getJobName(), LoadResult.LOADED, "Successfully loaded");

    } catch (NoSuchElementException | IllegalStateException e) {
      // Re-throw without wrapping — these are validation failures, not load errors
      // Reset status from LOADING back to previous
      entity.setLoadStatus(getPreviousLoadStatus(entity, definitionId));
      jobDefinitionRepository.save(entity);
      auditService.logEvent(
          new AuditEvent(
              AuditEventType.JOB_LOADED,
              entity.getJobName(),
              AuditService.currentUserId(),
              "Load rejected: " + e.getMessage(),
              AuditEvent.FAILURE,
              LocalDateTime.now()));
      throw e;
    } catch (Exception e) {
      // Mark FAILED and persist the error
      entity.setLoadStatus(LoadResult.FAILED);
      entity.setLoadError(e.getMessage());
      jobDefinitionRepository.save(entity);

      // Clean up any partial classloader
      if (classLoader != null) {
        try {
          classLoader.cleanup();
        } catch (Exception cleanupEx) {
          log.warn("Failed to clean up classloader for definition {}: {}", definitionId, cleanupEx.getMessage());
        }
      }
      classLoaders.remove(definitionId);

      log.error("Failed to load plugin for definition {}: {}", definitionId, e.getMessage(), e);

      auditService.logEvent(
          new AuditEvent(
              AuditEventType.JOB_LOADED,
              entity.getJobName(),
              AuditService.currentUserId(),
              "Load failed: " + e.getMessage(),
              AuditEvent.FAILURE,
              LocalDateTime.now()));

      if (e instanceof JobLoadException) {
        throw (JobLoadException) e;
      }
      throw new JobLoadException(
          "Failed to load job \"" + entity.getJobName() + "\": " + e.getMessage(), e);
    }
  }

  /**
   * Unloads a previously-loaded job: unregisters from the registry, closes the classloader, and
   * updates the database status.
   *
   * @param definitionId the database ID of the job definition to unload
   * @param force if {@code true}, skip the running-execution guard
   * @return a {@link LoadResult} describing the outcome
   * @throws NoSuchElementException if the definition does not exist
   * @throws JobUnloadConflictException if the job has running executions and {@code force} is
   *     {@code false}
   */
  public LoadResult unloadJob(Long definitionId, boolean force) {
    long unloadStartNanos = System.nanoTime();

    JobDefinitionEntity entity =
        jobDefinitionRepository
            .findById(definitionId)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Job definition not found for id: " + definitionId));

    // Guard: check for running executions unless forced
    if (!force) {
      // TODO: integrate JobExplorer for precise running-execution check.
      // Currently uses JobRepository.findRunningJobExecutions() which may not be exhaustive.
      java.util.Set<?> runningExecutions;
      try {
        runningExecutions =
            jobRepository.findRunningJobExecutions(entity.getJobName());
      } catch (EmptyResultDataAccessException e) {
        // Spring Batch 6.0.0 JdbcJobExecutionDao uses queryForObject which throws
        // when there are non-running executions but zero running ones. Treat as
        // "no running executions".
        log.debug(
            "findRunningJobExecutions returned empty for '{}': {}",
            entity.getJobName(),
            e.getMessage());
        runningExecutions = Collections.emptySet();
      }
      if (runningExecutions != null && !runningExecutions.isEmpty()) {
        throw new JobUnloadConflictException(
            "Job \""
                + entity.getJobName()
                + "\" has "
                + runningExecutions.size()
                + " running execution(s). Use force=true to override.");
      }
    }

    // Unregister from the plugin registry and JobRegistry
    pluginRegistryService.unregisterDynamicPlugin(entity.getJobName());

    // Close and remove classloader
    DynamicJobClassLoader classLoader = classLoaders.remove(definitionId);
    if (classLoader != null) {
      classLoader.cleanup();
    } else {
      log.warn(
          "No classloader found for definition {} — may not have been loaded through this service",
          definitionId);
    }

    // Persist unloaded state
    entity.setLoadStatus(LoadResult.UNLOADED);
    entity.setLoadError(null);
    jobDefinitionRepository.save(entity);

    log.info(
        "Unloaded plugin '{}' (id={})", entity.getJobName(), definitionId);

    auditService.logEvent(
        new AuditEvent(
            AuditEventType.JOB_UNLOADED,
            entity.getJobName(),
            AuditService.currentUserId(),
            "Job unloaded (force=" + force + ")",
            AuditEvent.SUCCESS,
            LocalDateTime.now()));

    long durationMs =
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - unloadStartNanos);
    pluginMetrics.incrementUnload();
    pluginMetrics.recordUnloadDuration(durationMs);

    return new LoadResult(
        entity.getJobName(), LoadResult.UNLOADED, "Successfully unloaded");
  }

  /**
   * Reloads a job by unloading (forced) then loading sequentially. The operation is sequential, so
   * the status never appears partially ready.
   *
   * @param definitionId the database ID of the job definition to reload
   * @return a {@link LoadResult} describing the outcome (always LOADED on success)
   * @throws NoSuchElementException if the definition does not exist
   * @throws JobLoadException if the load step fails
   */
  public LoadResult reloadJob(Long definitionId) {
    unloadJob(definitionId, true);
    return loadJob(definitionId);
  }

  /**
   * Loads all enabled job definitions whose {@code loadStatus} is not {@code LOADED}. Each
   * definition is loaded independently; one failure does not block the rest.
   *
   * @return a map from job name to the per-definition {@link LoadResult}
   */
  public Map<String, LoadResult> loadAllEnabled() {
    List<JobDefinitionEntity> enabledDefs = jobDefinitionRepository.findByEnabled(true);

    Map<String, LoadResult> results = new LinkedHashMap<>();

    for (JobDefinitionEntity def : enabledDefs) {
      // Skip definitions already loaded in THIS JVM's registry. After a restart
      // the persisted loadStatus may still say LOADED while the in-memory registry
      // is empty, so check the live registry — not the DB column — to avoid
      // skipping definitions that need re-instantiating.
      if (pluginRegistryService.getRegisteredJobNames().contains(def.getJobName())) {
        log.debug(
            "Skipping already-loaded definition '{}' (id={})",
            def.getJobName(),
            def.getId());
        results.put(
            def.getJobName(),
            new LoadResult(
                def.getJobName(), LoadResult.LOADED, "Already loaded"));
        continue;
      }

      try {
        LoadResult result = loadJob(def.getId());
        results.put(def.getJobName(), result);
      } catch (Exception e) {
        log.error(
            "Failed to auto-load definition '{}' (id={}): {}",
            def.getJobName(),
            def.getId(),
            e.getMessage());
        results.put(
            def.getJobName(),
            new LoadResult(
                def.getJobName(), LoadResult.FAILED, e.getMessage()));
      }
    }

    return results;
  }

  /**
   * Determines the previous load status for rollback when a pre-condition validation fails after
   * LOADING was already set.
   */
  private String getPreviousLoadStatus(JobDefinitionEntity entity, Long definitionId) {
    // If we already had a classloader, it means we were LOADED before this attempt
    if (classLoaders.containsKey(definitionId)) {
      return LoadResult.LOADED;
    }
    // Otherwise revert to UNLOADED (the most common prior state for a fresh load)
    return LoadResult.UNLOADED;
  }
}
