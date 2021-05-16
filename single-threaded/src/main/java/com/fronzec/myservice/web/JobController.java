package com.fronzec.myservice.web;

import com.fronzec.myservice.batch.JobsManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobsManagerService jobsManagerService;

    public JobController(JobsManagerService jobsManagerService) {
        this.jobsManagerService = jobsManagerService;
    }

    @PostMapping("/start-all")
    public ResponseEntity<JobInfo> startAllAction(
            @Valid @RequestBody JobDataRequest dataRequest
            ) {
        HashMap<String, String> stringStringHashMap = jobsManagerService.launchAllJobs(dataRequest.getDate(), dataRequest.getTryNumber());
        JobInfo jobInfo = new JobInfo();
        jobInfo.setInfo(stringStringHashMap);
        return ResponseEntity.ok(jobInfo);
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
