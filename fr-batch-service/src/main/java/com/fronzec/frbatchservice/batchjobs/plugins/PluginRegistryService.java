package com.fronzec.frbatchservice.batchjobs.plugins;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Collects all {@link BatchJobPlugin} beans at startup, invokes their {@code configureJob(...)}
 * method, and registers the produced {@link Job} instances into the shared {@link JobRegistry}.
 *
 * <p>Registration runs in {@link PostConstruct}, which Spring guarantees completes before any
 * consumer bean that constructor-injects this service is made available to the HTTP layer.
 */
@Service
public class PluginRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryService.class);

    private final List<BatchJobPlugin> plugins;
    private final JobRegistry jobRegistry;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationContext applicationContext;
    private final JobDefinitionRepository jobDefinitionRepository;

    /** Built during {@link PostConstruct} and dynamic registration; mutable. */
    private final Map<String, BatchJobPlugin> pluginsByJobName = new LinkedHashMap<>();

    /**
     * Tracks classloader references for dynamically-registered plugins. Stored for
     * Phase 4 cleanup (classloader close/reload). Key: job name, Value: classloader
     * reference.
     */
    private final Map<String, Object> classloaderRefs = new HashMap<>();

    public PluginRegistryService(
            List<BatchJobPlugin> plugins,
            JobRegistry jobRegistry,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext applicationContext,
            JobDefinitionRepository jobDefinitionRepository) {
        this.plugins = plugins;
        this.jobRegistry = jobRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.applicationContext = applicationContext;
        this.jobDefinitionRepository = jobDefinitionRepository;
    }

    @PostConstruct
    public void registerAll() {
        for (BatchJobPlugin plugin : plugins) {
            String name = plugin.getJobName();

            // 1. Duplicate-name check — fail-fast (REQ-3)
            if (pluginsByJobName.containsKey(name)) {
                BatchJobPlugin existing = pluginsByJobName.get(name);
                throw new PluginRegistrationException(
                        "Duplicate plugin job name: \""
                                + name
                                + "\" declared by "
                                + plugin.getClass().getName()
                                + " and "
                                + existing.getClass().getName());
            }

            // 2. Build the Job — fail-fast on any exception (REQ-4)
            Job job;
            try {
                job = plugin.configureJob(jobRepository, transactionManager, applicationContext);
                Objects.requireNonNull(
                        job, "configureJob() returned null for plugin: " + plugin.getClass().getName());
                if (!name.equals(job.getName())) {
                    throw new PluginRegistrationException(
                            "Plugin "
                                    + plugin.getClass().getName()
                                    + " declares jobName=\""
                                    + name
                                    + "\" but configureJob() produced a Job named \""
                                    + job.getName()
                                    + "\"");
                }
            } catch (PluginRegistrationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new PluginRegistrationException(
                        "Plugin " + plugin.getClass().getName() + " failed configureJob(): " + e.getMessage(),
                        e);
            }

            // 3. Register into the shared JobRegistry
            try {
                jobRegistry.register(job);
            } catch (DuplicateJobException e) {
                throw new PluginRegistrationException(
                        "Failed to register job \""
                                + name
                                + "\" from plugin "
                                + plugin.getClass().getName()
                                + ": "
                                + e.getMessage(),
                        e);
            }

            pluginsByJobName.put(name, plugin);
            log.info(
                    "Registered plugin job '{}' v{} from {}",
                    name,
                    plugin.getVersion(),
                    plugin.getClass().getSimpleName());
        }

        log.info(
                "Plugin registration complete: {} plugin(s) registered: {}",
                pluginsByJobName.size(),
                pluginsByJobName.keySet());
    }

    /** Returns all registered plugins in registration order. */
    public Collection<BatchJobPlugin> getPlugins() {
        return Collections.unmodifiableCollection(pluginsByJobName.values());
    }

    /** Returns the plugin registered under the given job name, or empty if not found. */
    public Optional<BatchJobPlugin> getPlugin(String jobName) {
        return Optional.ofNullable(pluginsByJobName.get(jobName));
    }

    /** Returns the set of all registered job names. */
    public Set<String> getRegisteredJobNames() {
        return Collections.unmodifiableSet(pluginsByJobName.keySet());
    }

    /**
     * Dynamically registers a {@link BatchJobPlugin} at runtime.
     *
     * <p>Thread-safe: acquires the {@code pluginsByJobName} monitor before
     * mutation so register-into-map + register-into-JobRegistry are atomic.
     *
     * @param plugin          the plugin instance to register (must have a unique
     *                        job name)
     * @param classLoaderRef  a reference to the classloader that loaded the
     *                        plugin (stored for Phase 4 cleanup)
     * @throws PluginRegistrationException if a plugin with the same job name
     *                                     is already registered, or if
     *                                     {@code configureJob()} fails
     */
    public void registerDynamicPlugin(BatchJobPlugin plugin, Object classLoaderRef) {
        String name = plugin.getJobName();

        synchronized (pluginsByJobName) {
            if (pluginsByJobName.containsKey(name)) {
                throw new PluginRegistrationException(
                        "Job \"" + name + "\" is already registered by plugin "
                                + pluginsByJobName.get(name).getClass().getName());
            }

            Job job;
            try {
                job = plugin.configureJob(jobRepository, transactionManager, applicationContext);
                Objects.requireNonNull(
                        job, "configureJob() returned null for plugin: " + plugin.getClass().getName());
                if (!name.equals(job.getName())) {
                    throw new PluginRegistrationException(
                            "Plugin " + plugin.getClass().getName()
                                    + " declares jobName=\"" + name
                                    + "\" but configureJob() produced a Job named \""
                                    + job.getName() + "\"");
                }
            } catch (PluginRegistrationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new PluginRegistrationException(
                        "Plugin " + plugin.getClass().getName() + " failed configureJob(): " + e.getMessage(),
                        e);
            }

            try {
                jobRegistry.register(job);
            } catch (DuplicateJobException e) {
                throw new PluginRegistrationException(
                        "Failed to register job \"" + name
                                + "\" from plugin " + plugin.getClass().getName() + ": "
                                + e.getMessage(), e);
            }

            pluginsByJobName.put(name, plugin);
            classloaderRefs.put(name, classLoaderRef);

            log.info(
                    "Dynamically registered plugin job '{}' from {}",
                    name,
                    plugin.getClass().getSimpleName());
        }
    }

    /**
     * Unregisters a dynamically-registered plugin from the internal map and the
     * shared {@link JobRegistry}.
     *
     * <p>Thread-safe: acquires the {@code pluginsByJobName} monitor. The
     * classloader reference is <em>retained</em> in {@code classloaderRefs} for
     * Phase 4 cleanup (graceful drain / close).
     *
     * <p>If the job is currently running, a warning is logged but execution is
     * not interrupted — Phase 4 handles graceful drain.
     *
     * @param jobName the job name to unregister
     */
    public void unregisterDynamicPlugin(String jobName) {
        synchronized (pluginsByJobName) {
            if (!pluginsByJobName.containsKey(jobName)) {
                log.warn("Attempted to unregister unknown plugin: \"{}\"", jobName);
                return;
            }

            pluginsByJobName.remove(jobName);

            try {
                jobRegistry.unregister(jobName);
            } catch (Exception e) {
                log.warn(
                        "Failed to unregister job \"{}\" from JobRegistry: {}",
                        jobName,
                        e.getMessage());
            }

            classloaderRefs.remove(jobName);

            log.info(
                    "Unregistered plugin '{}', classloader reference removed",
                    jobName);
        }
    }

    /**
     * Returns all enabled job definitions from the database.
     *
     * <p>Used by the merged plugin-listing endpoint to include uploaded
     * (but not yet classloader-loaded) definitions.
     */
    public List<JobDefinitionEntity> getRegisteredJobDefinitions() {
        return jobDefinitionRepository.findByEnabled(true);
    }

    /**
     * Determines the origin of a job definition by name.
     *
     * <p>Checks classpath-registered plugins first (they take precedence), then
     * falls back to the database.
     *
     * @param jobName the job name to look up
     * @return {@code "CLASSPATH"} if found in the plugin registry,
     *         {@code "UPLOADED"} if found as an enabled DB definition,
     *         or {@code null} if not found in either source
     */
    public String getPluginBySource(String jobName) {
        if (pluginsByJobName.containsKey(jobName)) {
            return "CLASSPATH";
        }
        Optional<JobDefinitionEntity> def = jobDefinitionRepository.findByJobName(jobName);
        if (def.isPresent() && Boolean.TRUE.equals(def.get().getEnabled())) {
            return "UPLOADED";
        }
        return null;
    }
}
