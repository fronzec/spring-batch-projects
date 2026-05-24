/* 2024-2025 */
package com.fronzec.frbatchservice.web;

import com.fronzec.frbatchservice.batchjobs.plugins.service.DuplicateJobDefinitionException;
import com.fronzec.frbatchservice.batchjobs.plugins.service.InvalidJarException;
import com.fronzec.frbatchservice.batchjobs.plugins.service.JarUploadService;
import com.fronzec.frbatchservice.web.dto.ErrorResponse;
import com.fronzec.frbatchservice.web.dto.JarUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for managing uploaded job definitions.
 *
 * <p>Effective URL prefix (with context-path): {@code /api/batch-service/jobs}
 *
 * <p>Validation errors are caught inline (PR 2). When {@code GlobalExceptionHandler}
 * is introduced in PR 5, the try/catch blocks will be removed in favor of
 * {@code @ExceptionHandler} methods.
 */
@RestController
@RequestMapping("/jobs")
public class JobManagementController {

    private static final Logger log = LoggerFactory.getLogger(JobManagementController.class);

    private final JarUploadService jarUploadService;

    public JobManagementController(JarUploadService jarUploadService) {
        this.jarUploadService = jarUploadService;
    }

    /**
     * Uploads a JAR file and registers a new job definition.
     *
     * <p>On success, returns {@code 201 Created} with the persisted metadata.
     * Validation failures return {@code 400 Bad Request}. Duplicate job names
     * return {@code 409 Conflict}.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadJar(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobName") String jobName,
            @RequestParam("version") String version,
            @RequestParam("mainClassName") String mainClassName,
            HttpServletRequest request) {

        try {
            JarUploadResponse response =
                    jarUploadService.uploadJar(file, jobName, version, mainClassName);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (InvalidJarException e) {
            log.warn("JAR upload validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(
                            400,
                            "Bad Request",
                            e.getMessage(),
                            LocalDateTime.now(),
                            request.getRequestURI()));

        } catch (DuplicateJobDefinitionException e) {
            log.warn("Duplicate job definition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            409,
                            "Conflict",
                            e.getMessage(),
                            LocalDateTime.now(),
                            request.getRequestURI()));
        }
    }
}
