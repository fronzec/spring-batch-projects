/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fronzec.api.BatchJobPlugin;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PluginRegistryServiceTest {

    @Mock private JobRegistry jobRegistry;
    @Mock private JobRepository jobRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ApplicationContext applicationContext;

    private BatchJobPlugin stubPlugin(String jobName, Job producedJob) {
        BatchJobPlugin plugin = mock(BatchJobPlugin.class);
        when(plugin.getJobName()).thenReturn(jobName);
        when(plugin.configureJob(eq(jobRepository), eq(transactionManager), eq(applicationContext)))
                .thenReturn(producedJob);
        return plugin;
    }

    private Job stubJob(String name) {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn(name);
        return job;
    }

    @Test
    void happyPath_singlePlugin_isDiscoveredAndRegistered() throws Exception {
        Job job = stubJob("job1");
        BatchJobPlugin plugin = stubPlugin("job1", job);

        PluginRegistryService service = new PluginRegistryService(
                List.of(plugin), jobRegistry, jobRepository, transactionManager, applicationContext);
        service.registerAll();

        assertEquals(1, service.getPlugins().size());
        assertTrue(service.getRegisteredJobNames().contains("job1"));
        assertTrue(service.getPlugin("job1").isPresent());
    }

    @Test
    void duplicateJobName_throwsPluginRegistrationException_withBothClassNames() {
        Job job1 = stubJob("duplicateJob");

        BatchJobPlugin pluginA = mock(BatchJobPlugin.class);
        when(pluginA.getJobName()).thenReturn("duplicateJob");
        when(pluginA.configureJob(any(), any(), any())).thenReturn(job1);

        BatchJobPlugin pluginB = mock(BatchJobPlugin.class);
        when(pluginB.getJobName()).thenReturn("duplicateJob");
        // pluginB.configureJob() is NOT called — duplicate check fires first

        PluginRegistryService service = new PluginRegistryService(
                List.of(pluginA, pluginB), jobRegistry, jobRepository, transactionManager, applicationContext);

        PluginRegistrationException ex = assertThrows(
                PluginRegistrationException.class, service::registerAll);

        String message = ex.getMessage();
        assertTrue(message.contains("duplicateJob"), "message should contain the conflicting job name");
        assertTrue(
                message.contains(pluginA.getClass().getName())
                        || message.contains(pluginB.getClass().getName()),
                "message should reference at least one plugin class");
    }

    @Test
    void configureJobThrows_wrapsInPluginRegistrationException_withPluginClassAndCause() {
        RuntimeException cause = new RuntimeException("simulated failure");

        BatchJobPlugin plugin = mock(BatchJobPlugin.class);
        when(plugin.getJobName()).thenReturn("failingJob");
        when(plugin.configureJob(any(), any(), any())).thenThrow(cause);

        PluginRegistryService service = new PluginRegistryService(
                List.of(plugin), jobRegistry, jobRepository, transactionManager, applicationContext);

        PluginRegistrationException ex = assertThrows(
                PluginRegistrationException.class, service::registerAll);

        String message = ex.getMessage();
        assertTrue(
                message.contains(plugin.getClass().getName()),
                "message should contain the plugin class name");
        assertEquals(cause, ex.getCause(), "cause should be the original exception");
    }
}
