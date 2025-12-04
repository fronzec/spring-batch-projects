/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.BeanFactory;

public class JobsManagerServiceTest {

    private JobsManagerService jobsManagerService;

    @Mock private JobLauncher jobLauncher;

    @Mock private BeanFactory beanFactory;

    @Mock private Job job;

    @BeforeEach
    void setUp() {
        List<Job> jobList = new ArrayList<>();
        jobList.add(job);
        jobsManagerService =
                new JobsManagerService(jobLauncher, jobLauncher, null, beanFactory, jobList, null);
    }

    @Test
    @Disabled
    void testLaunchAllJobsPreloaded()
            throws JobExecutionAlreadyRunningException,
                    JobRestartException,
                    JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {

        Date date = new Date();
        Integer runningNumber = 1;

        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
        jobParametersBuilder.addString(
                "DATE", String.format("%s-%s-%s", date.getDay(), date.getMonth(), date.getYear()));
        jobParametersBuilder.addString("RUNNING_NUMBER", runningNumber.toString());

        when(job.getName()).thenReturn("TestJob");
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenReturn(
                        new JobExecution(
                                1L,
                                new JobInstance(1L, "TestJob"),
                                jobParametersBuilder.toJobParameters()));

        HashMap<String, String> result =
                jobsManagerService.launchAllJobsPreloaded(date, runningNumber);

        verify(jobLauncher).run(job, jobParametersBuilder.toJobParameters());

        assertEquals(result.size(), 1);
        assertEquals(result.get("TestJob"), job.toString());
    }

    @Test
    @Disabled
    void testLaunchAllJobsPreloadedWithException()
            throws JobExecutionAlreadyRunningException,
                    JobRestartException,
                    JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {

        Date date = new Date();
        Integer runningNumber = 1;

        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
        jobParametersBuilder.addString(
                "DATE", String.format("%s-%s-%s", date.getDay(), date.getMonth(), date.getYear()));
        jobParametersBuilder.addString("RUNNING_NUMBER", runningNumber.toString());

        when(job.getName()).thenReturn("TestJob");
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(JobExecutionAlreadyRunningException.class);

        HashMap<String, String> result =
                jobsManagerService.launchAllJobsPreloaded(date, runningNumber);

        verify(jobLauncher).run(job, jobParametersBuilder.toJobParameters());

        assertEquals(result.size(), 1);
        assertEquals(
                result.get("TestJob"),
                "A job execution for this job is already running: JobInstance: id=0, version=null,"
                        + " Job=[null]");
    }
}
