/* 2024-2025 */
package com.fronzec.frbatchservice.web;

import com.fronzec.frbatchservice.batchjobs.plugins.PluginRegistryService;
import java.util.List;
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

    public PluginController(PluginRegistryService pluginRegistryService) {
        this.pluginRegistryService = pluginRegistryService;
    }

    /** Returns metadata for every registered plugin job. */
    @GetMapping("/plugins")
    public ResponseEntity<List<PluginInfoResponse>> listPlugins() {
        List<PluginInfoResponse> body =
                pluginRegistryService.getPlugins().stream()
                        .map(PluginInfoResponse::from)
                        .toList();
        return ResponseEntity.ok(body);
    }
}
