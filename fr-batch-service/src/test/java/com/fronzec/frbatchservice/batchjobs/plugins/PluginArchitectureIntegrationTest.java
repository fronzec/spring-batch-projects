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
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchStatus;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntityRepository;
import com.fronzec.frbatchservice.personv2.PersonV2Repository;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import com.fronzec.frbatchservice.restclients.ApiClient;
import com.fronzec.frbatchservice.restclients.DataCalculatedResponse;
import com.fronzec.frbatchservice.web.SingleJobDataRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Integration test proving the plugin architecture wiring end-to-end against an in-memory H2
 * database.
 *
 * <p>Uses {@code @ActiveProfiles("test")} to avoid the production datasource bean. Job step
 * execution may fail due to missing CSV fixture (sample-persons-1k.csv is not on the test
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
  @Autowired private DispatchedGroupEntityRepository dispatchedGroupEntityRepository;
  @Autowired private PersonV2Repository personV2Repository;

  @MockitoBean private ApiClient apiClient;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  /** SC-01, SC-02: Both plugins are discovered. */
  @Test
  void contextLoads_job1AndJob2PluginsDiscovered() {
    assertEquals(2, plugins.size(), "exactly two BatchJobPlugins should be discovered");

    List<String> names = plugins.stream().map(BatchJobPlugin::getJobName).sorted().toList();
    assertEquals(List.of("job1", "job2"), names);
  }

  /** Both job names are registered in MapJobRegistry after context refresh. */
  @Test
  void jobRegistry_containsJob1AndJob2_afterStartup() {
    assertTrue(
        jobRegistry.getJobNames().contains("job1"),
        "MapJobRegistry must contain 'job1' after plugin registration");
    assertTrue(
        jobRegistry.getJobNames().contains("job2"),
        "MapJobRegistry must contain 'job2' after plugin registration");

    Job job1 = jobRegistry.getJob("job1");
    assertNotNull(job1, "jobRegistry.getJob('job1') must return a non-null Job");
    assertEquals("job1", job1.getName());

    Job job2 = jobRegistry.getJob("job2");
    assertNotNull(job2, "jobRegistry.getJob('job2') must return a non-null Job");
    assertEquals("job2", job2.getName());
  }

  /**
   * SC-10: No @Bean Job named 'job1' or 'job2' exists in the Spring context. The Jobs are produced
   * by plugins — not registered as beans.
   */
  @Test
  void noJobBeanNamedJob1orJob2_inSpringContext() {
    assertFalse(
        applicationContext.containsBean("job1"),
        "There must be no Spring bean named 'job1' — the job is owned by PluginRegistryService");
    assertFalse(
        applicationContext.containsBean("job2"),
        "There must be no Spring bean named 'job2' — the job is owned by PluginRegistryService");
  }

  /**
   * SC-06: GET /jobs/plugins returns 200 with plugin metadata for both job1 and job2. Note:
   * context-path /api/batch-service is set in main application.properties. MockMvc bypasses the
   * servlet container, so the context-path is NOT prepended here.
   */
  @Test
  void getPlugins_returns200_withJob1AndJob2Metadata() throws Exception {
    mockMvc
        .perform(get("/jobs/plugins"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].job_name").isNotEmpty())
        .andExpect(jsonPath("$[0].version").isNotEmpty())
        .andExpect(jsonPath("$[0].display_name").isNotEmpty())
        .andExpect(jsonPath("$[0].author").isNotEmpty())
        .andExpect(jsonPath("$[1].job_name").isNotEmpty())
        .andExpect(jsonPath("$[1].version").isNotEmpty())
        .andExpect(jsonPath("$[1].display_name").isNotEmpty())
        .andExpect(jsonPath("$[1].author").isNotEmpty());
  }

  /**
   * SC-03, SC-09: syncRunJobWithParams resolves job1 via registry, launches it, and all three
   * steps complete successfully with ExitStatus.COMPLETED.
   *
   * <p>The ApiClient is mocked so step3's HTTP sendBatch does not leave the JVM. The assertion on
   * "result" key proves the job ran to completion end-to-end.
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

  /**
   * SC-04 (Recovery): seeds an ERROR-flagged {@link DispatchedGroupEntity} with two linked {@link
   * PersonsV2Entity} rows, mocks {@link ApiClient#sendBatch} to return {@code true}, runs {@code
   * job2} synchronously, and asserts the exit status is {@code COMPLETED} and the group's status is
   * {@code SENT}.
   */
  @Test
  void syncRunJobWithParams_job2_recoversErrorGroupToSent() {
    // Seed: an ERROR group with two persons
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setUuidV4(UUID.randomUUID().toString());
    group.setDispatchStatus(DispatchStatus.ERROR);
    dispatchedGroupEntityRepository.save(group);

    PersonsV2Entity p1 = new PersonsV2Entity();
    p1.setUuidV4("uuid-recover-1");
    p1.setFirstName("Recover");
    p1.setLastName("One");
    p1.setEmail("r1@example.com");
    p1.setProfession("Dev");
    p1.setSalary(BigDecimal.valueOf(80000));
    p1.setSnapshotDate(LocalDate.of(2026, 1, 1));
    p1.setFkDispatchedGroupId(group.getId());
    personV2Repository.save(p1);

    PersonsV2Entity p2 = new PersonsV2Entity();
    p2.setUuidV4("uuid-recover-2");
    p2.setFirstName("Recover");
    p2.setLastName("Two");
    p2.setEmail("r2@example.com");
    p2.setProfession("QA");
    p2.setSalary(BigDecimal.valueOf(70000));
    p2.setSnapshotDate(LocalDate.of(2026, 1, 1));
    p2.setFkDispatchedGroupId(group.getId());
    personV2Repository.save(p2);

    when(apiClient.sendBatch(any())).thenReturn(true);

    SingleJobDataRequest request = new SingleJobDataRequest();
    request.setJobBeanName("job2");
    request.setParam("date", LocalDate.now().toString());
    request.setParam("execution_attempt_number", "1");
    request.setParam("description", "recovery-integration-test");

    Map<String, String> result = jobsManagerService.syncRunJobWithParams(request);

    assertEquals(ExitStatus.COMPLETED.toString(), result.get("result"));

    // Verify the group was updated to SENT
    DispatchedGroupEntity updated =
        dispatchedGroupEntityRepository.findById(group.getId()).orElseThrow();
    assertEquals(DispatchStatus.SENT, updated.getDispatchStatus());
    assertEquals(2, updated.getRecordsIncluded());
  }

  /**
   * SC-04 (Idempotent failure): seeds an ERROR-flagged group, mocks {@link ApiClient#sendBatch} to
   * return {@code false}, runs {@code job2}, and asserts the group's status remains {@code ERROR}.
   */
  @Test
  void syncRunJobWithParams_job2_failureLeavesStatusError() {
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setUuidV4(UUID.randomUUID().toString());
    group.setDispatchStatus(DispatchStatus.ERROR);
    dispatchedGroupEntityRepository.save(group);

    PersonsV2Entity p1 = new PersonsV2Entity();
    p1.setUuidV4("uuid-fail-1");
    p1.setFirstName("Fail");
    p1.setLastName("Case");
    p1.setEmail("fail@example.com");
    p1.setProfession("Dev");
    p1.setSalary(BigDecimal.valueOf(60000));
    p1.setSnapshotDate(LocalDate.of(2026, 1, 1));
    p1.setFkDispatchedGroupId(group.getId());
    personV2Repository.save(p1);

    when(apiClient.sendBatch(any())).thenReturn(false);

    SingleJobDataRequest request = new SingleJobDataRequest();
    request.setJobBeanName("job2");
    request.setParam("date", LocalDate.now().toString());
    request.setParam("execution_attempt_number", "2");
    request.setParam("description", "failure-integration-test");

    Map<String, String> result = jobsManagerService.syncRunJobWithParams(request);

    assertEquals(ExitStatus.FAILED.toString(), result.get("result"));

    // Verify the group status is still ERROR
    DispatchedGroupEntity updated =
        dispatchedGroupEntityRepository.findById(group.getId()).orElseThrow();
    assertEquals(DispatchStatus.ERROR, updated.getDispatchStatus());
  }
}
