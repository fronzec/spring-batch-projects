package com.fronzec.myservice.web;

import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fronzec.myservice.batch.JobsManagerService;

/**
 * Este controlador expone endpoints para la gestion de los jobs
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

  private final JobsManagerService jobsManagerService;

  private final Logger logger = LoggerFactory.getLogger(JobController.class);

  public JobController(JobsManagerService jobsManagerService) {
    this.jobsManagerService = jobsManagerService;
  }

  @PostMapping("/start-all")
  public ResponseEntity<JobInfo> startAllAction(@Valid
  @RequestBody
          JobDataRequest dataRequest) {
    HashMap<String, String> stringStringHashMap = jobsManagerService.launchAllJobs(dataRequest.getDate(), dataRequest.getTryNumber());
    JobInfo jobInfo = new JobInfo();
    jobInfo.setInfo(stringStringHashMap);
    return ResponseEntity.ok(jobInfo);
  }

  @PostMapping(value = "/start-single")
  public ResponseEntity<Map<String, String>> postMethodName(
          @RequestHeader(value = "X-User", required = true)
                  String user, @Valid
  @RequestBody
          LaunchJobRequest request) {
    logger.info("user -> {}, reqBody -> {}", user, request);
    return ResponseEntity.ok(jobsManagerService.runJobWithParams(request));
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
