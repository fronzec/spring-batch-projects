/* 2024-2025 */
package com.fronzec.frbatchservice.web;

import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import com.fronzec.frbatchservice.web.dto.MergedPluginInfoResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes plugin metadata over HTTP.
 *
 * <p>Effective URL (with context-path {@code /api/batch-service}):
 * {@code GET /api/batch-service/jobs/plugins}
 */
@RestController
@RequestMapping("/jobs")
public class PluginController {

    private final PluginRegistryService pluginRegistryService;
    private final JobDefinitionRepository jobDefinitionRepository;

    public PluginController(
            PluginRegistryService pluginRegistryService,
            JobDefinitionRepository jobDefinitionRepository) {
        this.pluginRegistryService = pluginRegistryService;
        this.jobDefinitionRepository = jobDefinitionRepository;
    }

    /**
     * Returns merged plugin metadata from both classpath-registered plugins and
     * database-registered (uploaded) job definitions.
     *
     * <p>Classpath entries take precedence on job-name collision — a
     * database definition with the same job name as a classpath plugin is
     * excluded from the response.
     */
    @GetMapping("/plugins")
    public ResponseEntity<List<MergedPluginInfoResponse>> listPlugins() {
        // 1. Classpath plugins (take precedence)
        List<MergedPluginInfoResponse> classpath =
                pluginRegistryService.getPlugins().stream()
                        .map(MergedPluginInfoResponse::fromClasspath)
                        .toList();

        Set<String> classpathNames =
                classpath.stream()
                        .map(MergedPluginInfoResponse::jobName)
                        .collect(Collectors.toSet());

        // 2. Database definitions (enabled only, exclude name collisions)
        List<MergedPluginInfoResponse> uploaded =
                jobDefinitionRepository.findByEnabled(true).stream()
                        .filter(e -> !classpathNames.contains(e.getJobName()))
                        .map(MergedPluginInfoResponse::fromEntity)
                        .toList();

        // 3. Merge
        List<MergedPluginInfoResponse> merged = new ArrayList<>(classpath);
        merged.addAll(uploaded);

        return ResponseEntity.ok(merged);
    }
}
