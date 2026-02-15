/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.BeanFactory;

@ExtendWith(MockitoExtension.class)
public class JobsManagerServiceTest {

    private JobsManagerService jobsManagerService;

    @Mock private JobOperator jobOperator;

    @Mock private BeanFactory beanFactory;

    @Mock private Job job;

    @BeforeEach
    void setUp() {
        List<Job> jobList = new ArrayList<>();
        jobList.add(job);
        jobsManagerService =
                new JobsManagerService(jobOperator, jobOperator, beanFactory, jobList, null);
    }

    @Test
    void testLaunchAllJobsPreloaded() throws Exception {
        LocalDate date = LocalDate.now();
        Integer runningNumber = 1;

        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
        jobParametersBuilder.addString("DATE", date.toString());
        jobParametersBuilder.addString("RUNNING_NUMBER", runningNumber.toString());

        when(job.getName()).thenReturn("TestJob");
        when(jobOperator.run(any(Job.class), any(JobParameters.class)))
                .thenReturn(
                        new JobExecution(
                                1L,
                                new JobInstance(1L, "TestJob"),
                                jobParametersBuilder.toJobParameters()));

        HashMap<String, String> result =
                jobsManagerService.launchAllJobsPreloaded(date, runningNumber);

        verify(jobOperator).run(any(Job.class), any(JobParameters.class));

        assertEquals(1, result.size());
        assertNotNull(result.get("TestJob"));
        // The result contains the job.toString() value which is set by the mock
        assertTrue(result.containsKey("TestJob"));
    }

    @Test
    void testLaunchAllJobsPreloadedWithException() throws Exception {
        LocalDate date = LocalDate.now();
        Integer runningNumber = 1;

        when(job.getName()).thenReturn("TestJob");
        when(jobOperator.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("Job is already running"));

        HashMap<String, String> result =
                jobsManagerService.launchAllJobsPreloaded(date, runningNumber);

        verify(jobOperator).run(any(Job.class), any(JobParameters.class));

        assertEquals(1, result.size());
        // When an exception occurs, the error message is stored in the result map
        assertEquals("Job is already running", result.get("TestJob"));
    }
}
