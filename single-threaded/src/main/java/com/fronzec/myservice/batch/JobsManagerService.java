package com.fronzec.myservice.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Esta clas se encarga de proveer y gestionar la vida de los jobs
 */
@Service
public class JobsManagerService {

    // Saves jobBeanNames and jobsParams
    // TODO esto se debe mover a la BD
    private HashMap<String, Map<String, String>> jobs = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(JobsManagerService.class);

    private JobLauncher asyncJobLauncher;
    /**
     * JobExplorer is a readonly implementation of the JobRepository, it can be configured with custom props
     */
    private JobExplorer jobExplorer;

    /**
     * Allow keep track of which jobs are available in the context
     */
    private JobRegistry jobRegistry;

    private BeanFactory beanFactory;

    /**
     * As previously discussed, the JobRepository provides CRUD operations on the meta-data, and the JobExplorer provides
     * read-only operations on the meta-data. However, those operations are most useful when used together to perform common
     * monitoring tasks such as stopping, restarting, or summarizing a Job, as is commonly done by batch operators.
     * Spring Batch provides these types of operations via the JobOperator interface:
     */
    private JobOperator jobOperator;

    public JobsManagerService(JobLauncher asyncJobLauncher, JobExplorer jobExplorer, JobRegistry jobRegistry, BeanFactory beanFactory, JobOperator jobOperator) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.jobExplorer = jobExplorer;
        this.jobRegistry = jobRegistry;
        this.beanFactory = beanFactory;
        this.jobOperator = jobOperator;
    }

    @PostConstruct
    public void init() {
        // NOTE: 16/05/21 Jobs configurations associated
        Map<String, String> value = new HashMap<>();
        value.put("paramname", "paramvalue");
        jobs.put("myImportUserJob", value);

    }

    public HashMap<String, String> launchAllJobs(Date date, final Integer runningNumber) {
        Objects.requireNonNull(date);
        // TODO: 15/05/21 get all jobs
        // TODO: 16/05/21 instance a job parameter and job names
        HashMap<String, String> info = new HashMap<>(jobs.size());
        jobs.forEach((key, params) -> {
            var theJob = (Job) beanFactory.getBean(key);
            Objects.requireNonNull(theJob);
            var jobParametersBuilder = new JobParametersBuilder();
            // Adding the date to our param we set and allow run a single job only for one day
            int day = date.getDay();
            int month = date.getMonth();
            int year = date.getYear();
            params.put("DATE",String.format("%s-%s-%s", day,month,year));
            params.put("RUNNING_NUMBER", runningNumber.toString());
            params.forEach(jobParametersBuilder::addString);
            try {
                logger.info("Running job: {} with params -> {}", theJob, params);
                asyncJobLauncher.run(theJob, jobParametersBuilder.toJobParameters());
                info.put(key, theJob.toString());
            } catch (JobExecutionAlreadyRunningException e) {
                logger.error("already", e);
                info.put(key, e.getMessage());
            } catch (JobRestartException e) {
                logger.error("restart", e);
                info.put(key, e.getMessage());
            } catch (JobInstanceAlreadyCompleteException e) {
                logger.error("completed", e);
                info.put(key, e.getMessage());
            } catch (JobParametersInvalidException e) {
                logger.info("parameters", e);
                info.put(key, e.getMessage());
            }
        });

        return info;
    }

    public HashMap<String, String> stopAllJobs() {
        HashMap<String, String> singaledStopped = new HashMap<>();

        jobs.keySet().forEach(name -> {
            try {
                Set<Long> runningExecutions = jobOperator.getRunningExecutions(name);
                logger.info("Stopping job name -> {} :: running -> {} ", name, runningExecutions);
                runningExecutions.forEach(exId -> {
                    try {
                        jobOperator.stop(exId);
                    } catch (NoSuchJobExecutionException e) {
                        logger.error("nosuch", e);
                    } catch (JobExecutionNotRunningException e) {
                        logger.error("no running",e);
                    }
                });
                singaledStopped.put(name, runningExecutions.toString());
            } catch (NoSuchJobException e) {
                singaledStopped.put(name, "NOSUCHJOB");
                logger.error("no such job", e);
            }
        });

        return singaledStopped;
    }

    public HashMap<String, Map<String, String>> getRunning() {
        HashMap<String, Map<String, String>> running = new HashMap<>();
        jobs.keySet().forEach(key -> {
            HashMap<String, String> tmp = new HashMap<>();
            try {
                Set<Long> runningExecutions = jobOperator.getRunningExecutions(key);
                runningExecutions.forEach(r -> {
                    try {
                        tmp.put(String.valueOf(r), jobOperator.getSummary(r));
                    } catch (NoSuchJobExecutionException e) {
                        tmp.put(String.valueOf(r), "NOSUCHEXECUTION");
                    }
                });
                running.put(key, tmp);
            } catch (NoSuchJobException e) {
                logger.error("no such job", e);
                running.put(key, Collections.singletonMap("error","nosuch job"));
            }
        });
        return running;
    }
}
