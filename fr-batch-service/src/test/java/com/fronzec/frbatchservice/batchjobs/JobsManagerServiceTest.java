package com.fronzec.frbatchservice.batchjobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobsManagerServiceTest {

  private JobsManagerService jobsManagerService;


  @Mock
  private JobLauncher jobLauncher;

  @Mock
  private BeanFactory beanFactory;

  @Mock
  private Job job;

  @BeforeEach
  void setUp() {
    List<Job> jobList = new ArrayList<>();
    jobList.add(job);
    jobsManagerService = new JobsManagerService(jobLauncher, jobLauncher, null, null, beanFactory, jobList, null);
  }

  @Test
  void testLaunchAllJobsPreloaded() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {

    Date date = new Date();
    Integer runningNumber = 1;

    JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
    jobParametersBuilder.addString("DATE", String.format("%s-%s-%s", date.getDay(), date.getMonth(), date.getYear()));
    jobParametersBuilder.addString("RUNNING_NUMBER", runningNumber.toString());

    when(job.getName()).thenReturn("TestJob");
    when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenReturn(new JobExecution(1L));

    HashMap<String, String> result = jobsManagerService.launchAllJobsPreloaded(date, runningNumber);

    verify(jobLauncher).run(job, jobParametersBuilder.toJobParameters());

    assertEquals(result.size(), 1);
    assertEquals(result.get("TestJob"), job.toString());
  }

  @Test
  void testLaunchAllJobsPreloadedWithException() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {

    Date date = new Date();
    Integer runningNumber = 1;

    JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
    jobParametersBuilder.addString("DATE", String.format("%s-%s-%s", date.getDay(), date.getMonth(), date.getYear()));
    jobParametersBuilder.addString("RUNNING_NUMBER", runningNumber.toString());

    when(job.getName()).thenReturn("TestJob");
    when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenThrow(JobExecutionAlreadyRunningException.class);

    HashMap<String, String> result = jobsManagerService.launchAllJobsPreloaded(date, runningNumber);

    verify(jobLauncher).run(job, jobParametersBuilder.toJobParameters());

    assertEquals(result.size(), 1);
    assertEquals(result.get("TestJob"), "A job execution for this job is already running: JobInstance: id=0, version=null, Job=[null]");
  }
}
