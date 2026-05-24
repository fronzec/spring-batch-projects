package com.fronzec.frbatchservice.batchjobs.plugins;

import com.fronzec.api.BatchJobPlugin;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
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

    /** Built during {@link PostConstruct}, then read-only. */
    private final Map<String, BatchJobPlugin> pluginsByJobName = new LinkedHashMap<>();

    public PluginRegistryService(
            List<BatchJobPlugin> plugins,
            JobRegistry jobRegistry,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ApplicationContext applicationContext) {
        this.plugins = plugins;
        this.jobRegistry = jobRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.applicationContext = applicationContext;
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
}
