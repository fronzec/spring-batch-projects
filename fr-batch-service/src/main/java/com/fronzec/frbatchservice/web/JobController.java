/* 2024 */
package com.fronzec.frbatchservice.web;

import com.fronzec.frbatchservice.batchjobs.JobsManagerService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Este controlador expone endpoints para la gestion de los jobs */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobsManagerService jobsManagerService;

    private final Logger logger = LoggerFactory.getLogger(JobController.class);

    public JobController(JobsManagerService jobsManagerService) {
        this.jobsManagerService = jobsManagerService;
    }

    @PostMapping("/start-all/async")
    public ResponseEntity<JobInfo> startAllJobsAsync(
            @Valid @RequestBody AllJobsDataRequest dataRequest) {
        HashMap<String, String> stringStringHashMap =
                jobsManagerService.launchAllNonAutoDetectedJobsAsync(
                        dataRequest.getDate(), dataRequest.getTryNumber());
        JobInfo jobInfo = new JobInfo();
        jobInfo.setInfo(stringStringHashMap);
        return ResponseEntity.ok(jobInfo);
    }

    @PostMapping("/start-all/sync")
    public ResponseEntity<JobInfo> startAllJobsSync(
            @Valid @RequestBody AllJobsDataRequest dataRequest) {
        HashMap<String, String> stringStringHashMap =
                jobsManagerService.launchAllNonAutoDetectedJobsSync(
                        dataRequest.getLocalDate(), dataRequest.getTryNumber());
        JobInfo jobsResultInfo = new JobInfo();
        jobsResultInfo.setInfo(stringStringHashMap);
        return ResponseEntity.ok(jobsResultInfo);
    }

    @PostMapping(value = "/start-single/async")
    public ResponseEntity<Map<String, String>> runJobAsync(
            @RequestHeader(value = "X-User") String user,
            @Valid @RequestBody SingleJobDataRequest request) {
        logger.info("user -> {}, reqBody -> {}", user, request);
        return ResponseEntity.ok(jobsManagerService.asyncRunJobWithParams(request));
    }

    @PostMapping(value = "/start-single/sync")
    public ResponseEntity<Map<String, String>> runJobSync(
            @RequestHeader(value = "X-User") String user,
            @Valid @RequestBody SingleJobDataRequest request) {
        logger.info("user -> {}, reqBody -> {}", user, request);
        return ResponseEntity.ok(jobsManagerService.syncRunJobWithParams(request));
    }

    @PostMapping("/stop-all")
    public ResponseEntity<JobInfo> stopAllAction() {
        HashMap<String, String> stringStringHashMap = jobsManagerService.stopAllJobs();
        JobInfo jobInfo = new JobInfo();
        jobInfo.setInfo(stringStringHashMap);
        return ResponseEntity.ok(jobInfo);
    }

    @GetMapping("/running-all")
    public ResponseEntity<HashMap<String, Map<String, String>>> getAllAction() {
        HashMap<String, Map<String, String>> running = jobsManagerService.getRunning();
        return ResponseEntity.ok(running);
    }
}
