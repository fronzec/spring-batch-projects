/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.frbatchservice.batchjobs.JobsManagerService;
import com.fronzec.frbatchservice.restclients.ApiClient;
import com.fronzec.frbatchservice.restclients.DataCalculatedResponse;
import com.fronzec.frbatchservice.web.SingleJobDataRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration test proving the plugin architecture wiring end-to-end against an in-memory H2 database.
 *
 * <p>Uses {@code @ActiveProfiles("test")} to avoid the production datasource bean.
 * Job step execution may fail due to missing CSV fixture (sample-persons-1k.csv is not on the test
 * classpath); the launch assertions are therefore scoped to the absence of an "error" key in the
 * response map, not to COMPLETED exit status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PluginArchitectureIntegrationTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private List<BatchJobPlugin> plugins;
    @Autowired private JobRegistry jobRegistry;
    @Autowired private JobsManagerService jobsManagerService;

    @MockitoBean
    private ApiClient apiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /** SC-01, SC-02: Job1Plugin is discovered and MapJobRegistry is populated at startup. */
    @Test
    void contextLoads_job1PluginDiscovered_andRegistered() {
        assertEquals(1, plugins.size(), "exactly one BatchJobPlugin should be discovered");
        BatchJobPlugin plugin = plugins.get(0);
        assertEquals("job1", plugin.getJobName());
    }

    /** SC-02, SC-09: MapJobRegistry.getJobNames() contains 'job1' after context refresh. */
    @Test
    void jobRegistry_containsJob1_afterStartup() {
        assertTrue(
                jobRegistry.getJobNames().contains("job1"),
                "MapJobRegistry must contain 'job1' after plugin registration");

        Job job = jobRegistry.getJob("job1");
        assertNotNull(job, "jobRegistry.getJob('job1') must return a non-null Job");
        assertEquals("job1", job.getName());
    }

    /**
     * SC-10: No @Bean Job named 'job1' exists in the Spring context.
     * The Job is produced by Job1Plugin.configureJob() — not registered as a bean.
     */
    @Test
    void noJobBeanNamedJob1_inSpringContext() {
        assertFalse(
                applicationContext.containsBean("job1"),
                "There must be no Spring bean named 'job1' — the job is owned by PluginRegistryService");
    }

    /**
     * SC-06: GET /jobs/plugins returns 200 with plugin metadata for job1.
     * Note: context-path /api/batch-service is set in main application.properties.
     * MockMvc bypasses the servlet container, so the context-path is NOT prepended here.
     */
    @Test
    void getPlugins_returns200_withJob1Metadata() throws Exception {
        mockMvc.perform(get("/jobs/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].job_name").value("job1"))
                .andExpect(jsonPath("$[0].version").value("1.0.0"))
                .andExpect(jsonPath("$[0].display_name").isNotEmpty())
                .andExpect(jsonPath("$[0].author").isNotEmpty());
    }

    /**
     * SC-03, SC-09: syncRunJobWithParams resolves job1 via registry, launches it,
     * and all three steps complete successfully with ExitStatus.COMPLETED.
     *
     * <p>The ApiClient is mocked so step3's HTTP sendBatch does not leave the JVM.
     * The assertion on "result" key proves the job ran to completion end-to-end.
     */
    @Test
    void syncRunJobWithParams_job1_completesWithExitStatusCompleted() {
        SingleJobDataRequest request = new SingleJobDataRequest();
        request.setJobBeanName("job1");
        request.setParam("date", LocalDate.now().toString());
        request.setParam("execution_attempt_number", "1");
        request.setParam("description", "integration-test");

        DataCalculatedResponse salaryResponse = new DataCalculatedResponse();
        salaryResponse.setValue(BigDecimal.valueOf(50000));
        when(apiClient.getRandomValue(any())).thenReturn(salaryResponse);
        when(apiClient.sendBatch(any())).thenReturn(true);

        Map<String, String> result = jobsManagerService.syncRunJobWithParams(request);

        assertEquals(ExitStatus.COMPLETED.toString(), result.get("result"));
    }
}
