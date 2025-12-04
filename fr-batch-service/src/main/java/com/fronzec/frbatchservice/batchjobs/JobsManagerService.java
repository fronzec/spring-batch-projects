/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs;

import com.fronzec.frbatchservice.utils.JsonUtils;
import com.fronzec.frbatchservice.web.SingleJobDataRequest;
import io.vavr.control.Try;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.*;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Service;

/** Esta clas se encarga de proveer y gestionar la vida de los jobs */
@Service
public class JobsManagerService {

    // TODO This must be moved to the DB to an entity relationship model

    /** Saves jobBeanNames and jobsParams */
    private Map<String, Map<String, String>> manualDefinedJobs = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(JobsManagerService.class);

    private JobLauncher asyncJobLauncher;

    private JobLauncher syncJobLauncher;

    /**
     * JobExplorer is a readonly implementation of the JobRepository, it can be configured with custom
     * props
     */
    private JobExplorer jobExplorer;

    private BeanFactory beanFactory;

    private List<Job> jobsList;

    /**
     * The JobRepository provides CRUD operations on the meta-data, and the JobExplorer provides
     * read-only operations on the meta-data. However, those operations are most useful when used
     * together to perform common monitoring tasks such as stopping, restarting, or summarizing a Job,
     * as is commonly done by batch operators. Spring Batch provides these types of operations via the
     * JobOperator interface:
     */
    private JobOperator jobOperator;

    public JobsManagerService(
            JobLauncher asyncJobLauncher,
            JobLauncher syncJobLauncher,
            JobExplorer jobExplorer,
            BeanFactory beanFactory,
            List<Job> jobsList,
            JobOperator jobOperator) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.syncJobLauncher = syncJobLauncher;
        this.jobExplorer = jobExplorer;
        this.beanFactory = beanFactory;
        this.jobsList = jobsList;
        this.jobOperator = jobOperator;
    }

    @PostConstruct
    public void init() {
        // NOTE: 16/05/21 Jobs configurations associated manually
        // TODO: 19/02/2022 refactor default params to add boolean that allow indicate if param is
        // used
        // as identifying a job instance
        Map<String, String> params = new HashMap<>();
        params.put("paramname", "paramvalue");

        manualDefinedJobs.put("job1", params);
        manualDefinedJobs.put("job2", params);
        manualDefinedJobs.put("job3", params);
        // TODO: 16/05/21 trying to run non existing job
        manualDefinedJobs.put("nonExistingJob", params);

        // NOTE: 16/05/21 Jobs loaded in our service using spring, see injection on constructor
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("jobs found by spring -> < count= %s >", jobsList.size()));
        jobsList.forEach(job -> sb.append(String.format(" | [job -> %s]", job)));
        logger.info("-> {}", sb);
    }

    public HashMap<String, String> launchAllNonAutoDetectedJobsAsync(
            LocalDate date, final int runningNumber) {
        return launchAllNonAutoDetectedJobs(date, runningNumber, false);
    }

    public HashMap<String, String> launchAllNonAutoDetectedJobsSync(
            LocalDate execDate, final int runningNumber) {
        return launchAllNonAutoDetectedJobs(execDate, runningNumber, true);
    }

    /**
     * Launches all manually registered (non-auto-detected) jobs using the provided execution date and attempt number.
     *
     * Each job receives job parameters where "DATE" is set from {@code execDate} and "ATTEMPT_NUMBER" is set from
     * {@code runningNumber}; a non-identifying "DESCRIPTION" parameter is also added. Jobs are executed synchronously
     * when {@code useSyncExecutor} is true, otherwise they are executed asynchronously.
     *
     * @param execDate        the execution date to add as the "DATE" job parameter (must not be null)
     * @param runningNumber   the attempt number to add as the "ATTEMPT_NUMBER" job parameter
     * @param useSyncExecutor when true, use the synchronous JobLauncher; when false, use the asynchronous JobLauncher
     * @return a map from job name to either the job's identifier string on successful launch or an error message if the job
     *         could not be obtained or failed to start
     */
    private HashMap<String, String> launchAllNonAutoDetectedJobs(
            LocalDate execDate, final int runningNumber, boolean useSyncExecutor) {
        Objects.requireNonNull(execDate);
        HashMap<String, String> resultInfoHolder = new HashMap<>(manualDefinedJobs.size());
        manualDefinedJobs.forEach(
                (key, defaultJobParams) -> {
                    Try<Job> theJob = Try.of(() -> (Job) beanFactory.getBean(key));
                    if (theJob.isFailure()) {
                        Throwable e = theJob.getCause();
                        logger.info("nobeanjob found {}", e.toString());
                        resultInfoHolder.put(key, e.getMessage());
                        logger.warn("failure building the bean for the job, continuing with next");
                        return;
                    }

                    var jobParametersBuilder = new JobParametersBuilder();
                    // note: Params used as part of identifying a job instance
                    defaultJobParams.put("DATE", execDate.toString());
                    defaultJobParams.put("ATTEMPT_NUMBER", String.valueOf(runningNumber));
                    defaultJobParams.forEach(jobParametersBuilder::addString);
                    // note: Params not used to identify a job instance
                    jobParametersBuilder.addString("DESCRIPTION", "some useful description", false);
                    try {
                        Job jobToRun = theJob.get();
                        logger.info(
                                "Running job: {} with params -> {}", jobToRun, defaultJobParams);
                        if (useSyncExecutor) {
                            syncJobLauncher.run(jobToRun, jobParametersBuilder.toJobParameters());
                        } else {
                            asyncJobLauncher.run(jobToRun, jobParametersBuilder.toJobParameters());
                        }
                        resultInfoHolder.put(key, jobToRun.toString());
                    } catch (JobExecutionAlreadyRunningException e) {
                        logger.error("already", e);
                        resultInfoHolder.put(key, e.getMessage());
                    } catch (JobRestartException e) {
                        logger.error("restart", e);
                        resultInfoHolder.put(key, e.getMessage());
                    } catch (JobInstanceAlreadyCompleteException e) {
                        logger.error("completed", e);
                        resultInfoHolder.put(key, e.getMessage());
                    } catch (InvalidJobParametersException e) {
                        logger.info("parameters", e);
                        resultInfoHolder.put(key, e.getMessage());
                    }
                });
        return resultInfoHolder;
    }

    /**
     * Launches every job discovered by Spring (preloaded) using the async launcher with the provided execution date and running number.
     *
     * @param date the execution date used to populate the `DATE` job parameter; must not be null
     * @param runningNumber an integer placed into the `RUNNING_NUMBER` job parameter
     * @return a map from job name to either the job's identifying string on successful launch or an error message when launch failed
     */
    public HashMap<String, String> launchAllJobsPreloaded(Date date, final Integer runningNumber) {
        Objects.requireNonNull(date);
        var info = new HashMap<String, String>(jobsList.size());
        jobsList.forEach(
                job -> {
                    var key = job.getName();
                    var params = new HashMap<String, String>();
                    var jobParametersBuilder = new JobParametersBuilder();
                    // Adding the date to our param we set and allow run a single job only for one
                    // day
                    var day = date.getDay();
                    var month = date.getMonth();
                    var year = date.getYear();
                    params.put("DATE", String.format("%s-%s-%s", day, month, year));
                    params.put("RUNNING_NUMBER", runningNumber.toString());
                    params.forEach(jobParametersBuilder::addString);
                    try {
                        logger.info("Running job: {} with params -> {}", job, params);
                        asyncJobLauncher.run(job, jobParametersBuilder.toJobParameters());
                        info.put(key, job.toString());
                    } catch (JobExecutionAlreadyRunningException e) {
                        logger.error("already", e);
                        info.put(key, e.getMessage());
                    } catch (JobRestartException e) {
                        logger.error("restart", e);
                        info.put(key, e.getMessage());
                    } catch (JobInstanceAlreadyCompleteException e) {
                        logger.error("completed", e);
                        info.put(key, e.getMessage());
                    } catch (InvalidJobParametersException e) {
                        logger.info("parameters", e);
                        info.put(key, e.getMessage());
                    }
                });

        return info;
    }

    /**
     * Launches a manually registered job asynchronously using parameters from the request.
     *
     * Builds job parameters from the request (expects a "date" and "execution_attempt_number" entry, and optionally "description"),
     * attempts to run the job via the asynchronous JobLauncher, and returns a small metadata map describing the outcome.
     *
     * @param request container holding the target job bean name and a map of string parameters; must include "date" and "execution_attempt_number"
     * @return a map containing outcome details:
     *         - "jobName": the requested job bean name when found,
     *         - "result": a JSON-serialized representation of the started job when launch succeeds,
     *         - "error": an error message when the job cannot be started or the job is not found
     */
    public Map<String, String> asyncRunJobWithParams(SingleJobDataRequest request) {
        Map<String, String> launchedJobMetadata = new HashMap<>();
        Map<String, String> jobExecParams = new HashMap<>();
        if (manualDefinedJobs.containsKey(request.getJobBeanName())) {
            try {
                var theJob = (Job) beanFactory.getBean(request.getJobBeanName());
                Objects.requireNonNull(theJob);
                launchedJobMetadata.put("jobName", request.getJobBeanName());
                var jobParametersBuilder = new JobParametersBuilder();
                // Adding the date to our param we track the execution of the job for a day
                // That param allow for example filter data to be processed for that day
                LocalDate execDate = LocalDate.parse(request.getParams().get("date"));
                jobExecParams.put("DATE", execDate.toString());
                jobExecParams.put(
                        "ATTEMPT_NUMBER", request.getParams().get("execution_attempt_number"));

                // add param to the job builder
                jobExecParams.forEach(jobParametersBuilder::addString);
                jobParametersBuilder.addString(
                        "DESCRIPTION", request.getParams().get("description"), false);
                logger.info(
                        "Attempt to run job -> {} with params -> {}",
                        theJob,
                        JsonUtils.parseObject2Json(jobExecParams));
                asyncJobLauncher.run(theJob, jobParametersBuilder.toJobParameters());
                launchedJobMetadata.put("result", JsonUtils.parseObject2Json(theJob.toString()));
            } catch (JobExecutionAlreadyRunningException e) {
                logger.error("already running job with the same params", e);
                launchedJobMetadata.put("error", e.getMessage());
            } catch (JobRestartException e) {
                logger.error("restart", e);
                launchedJobMetadata.put("error", e.getMessage());
            } catch (JobInstanceAlreadyCompleteException e) {
                logger.error("completed", e);
                launchedJobMetadata.put("error", e.getMessage());
            } catch (InvalidJobParametersException e) {
                logger.info("parameters", e);
                launchedJobMetadata.put("error", e.getMessage());
            }
        } else {
            launchedJobMetadata.put(
                    "error", String.format("Job <%s> not found", request.getJobBeanName()));
        }
        return launchedJobMetadata;
    }

    /**
     * Runs the specified manually defined job synchronously using parameters supplied in the request.
     *
     * The request must reference a job known to the manual registry. The method builds job parameters
     * from request.params (see below), executes the job with the synchronous JobLauncher, and returns
     * a map containing execution metadata or an error message.
     *
     * @param request a SingleJobDataRequest whose params map is expected to contain:
     *                - "date": ISO-8601 date string used as the job's `DATE` parameter
     *                - "execution_attempt_number": a string used as the job's `ATTEMPT_NUMBER` parameter
     *                - "description": a non-identifying description added as `DESCRIPTION`
     * @return a map of result keys:
     *         - "jobName": the invoked job bean name (on success)
     *         - "parameters": JSON string of the JobParameters used (on success)
     *         - "result": job exit status as string (on success)
     *         - "error": error message when execution fails or job is not found
     */
    public Map<String, String> syncRunJobWithParams(SingleJobDataRequest request) {
        Map<String, String> launchedJobMetadata = new HashMap<>();
        Map<String, String> jobExecParams = new HashMap<>();
        if (manualDefinedJobs.containsKey(request.getJobBeanName())) {
            try {
                var theJob = (Job) beanFactory.getBean(request.getJobBeanName());
                Objects.requireNonNull(theJob);
                launchedJobMetadata.put("jobName", request.getJobBeanName());
                var jobParametersBuilder = new JobParametersBuilder();
                // Adding the date to our param we track the execution of the job for a day
                // That param allow for example filter data to be processed for that day
                LocalDate execDate = LocalDate.parse(request.getParams().get("date"));
                jobExecParams.put("DATE", execDate.toString());
                jobExecParams.put(
                        "ATTEMPT_NUMBER", request.getParams().get("execution_attempt_number"));

                // add param to the job builder
                jobExecParams.forEach(jobParametersBuilder::addString);
                jobParametersBuilder.addString(
                        "DESCRIPTION", request.getParams().get("description"), false);
                logger.info(
                        "Attempt to run job -> {} with identifying params -> {}",
                        theJob,
                        JsonUtils.parseObject2Json(jobExecParams));
                JobExecution run =
                        syncJobLauncher.run(theJob, jobParametersBuilder.toJobParameters());
                launchedJobMetadata.put(
                        "parameters",
                        JsonUtils.parseObject2Json(jobParametersBuilder.toJobParameters()));
                launchedJobMetadata.put("result", run.getExitStatus().toString());
            } catch (JobExecutionAlreadyRunningException e) {
                logger.error("already running job with the same params", e);
                launchedJobMetadata.put("error", e.getMessage());
            } catch (JobRestartException e) {
                logger.error("restart", e);
                launchedJobMetadata.put("error", e.getMessage());
            } catch (JobInstanceAlreadyCompleteException e) {
                logger.error("completed", e);
                launchedJobMetadata.put("error", e.getMessage());
            } catch (InvalidJobParametersException e) {
                logger.info("parameters", e);
                launchedJobMetadata.put("error", e.getMessage());
            }
        } else {
            launchedJobMetadata.put(
                    "error", String.format("Job <%s> not found", request.getJobBeanName()));
        }
        return launchedJobMetadata;
    }

    public HashMap<String, String> stopAllJobs() {
        HashMap<String, String> singaledStopped = new HashMap<>();
        manualDefinedJobs
                .keySet()
                .forEach(
                        name -> {
                            try {
                                Set<Long> runningExecutions =
                                        jobOperator.getRunningExecutions(name);
                                logger.info(
                                        "Stopping job name -> {} :: running -> {} ",
                                        name,
                                        runningExecutions);
                                runningExecutions.forEach(
                                        exId -> {
                                            try {
                                                jobOperator.stop(exId);
                                            } catch (NoSuchJobExecutionException e) {
                                                logger.error("nosuch", e);
                                            } catch (JobExecutionNotRunningException e) {
                                                logger.error("no running", e);
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
        manualDefinedJobs
                .keySet()
                .forEach(
                        key -> {
                            HashMap<String, String> tmp = new HashMap<>();
                            try {
                                Set<Long> runningExecutions = jobOperator.getRunningExecutions(key);
                                runningExecutions.forEach(
                                        r -> {
                                            try {
                                                tmp.put(
                                                        String.valueOf(r),
                                                        jobOperator.getSummary(r));
                                            } catch (NoSuchJobExecutionException e) {
                                                tmp.put(String.valueOf(r), "NOSUCHEXECUTION");
                                            }
                                        });
                                running.put(key, tmp);
                            } catch (NoSuchJobException e) {
                                logger.error("no such job", e);
                                running.put(key, Collections.singletonMap("error", "nosuch job"));
                            }
                        });
        return running;
    }
}
