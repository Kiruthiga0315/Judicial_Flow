package com.judicialflow.simulation.validation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin controller for triggering simulation and fetching validation reports.
 * Protected by ADMIN role only.
 */
@RestController
@RequestMapping("/api/v1/admin/simulation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Simulation Validation", description = "Admin endpoints for triggering and retrieving simulation validation reports")
public class SimulationAdminController {

    private final SimulationOrchestratorService orchestratorService;

    @PostMapping("/run")
    @Operation(summary = "Trigger asynchronous simulation run (ADMIN only)")
    public ResponseEntity<Map<String, Object>> triggerSimulation(
            @RequestParam(defaultValue = "false") boolean quick,
            Authentication auth
    ) {
        String username = auth != null ? auth.getName() : "ADMIN";
        String role = "ADMIN";
        String runId = UUID.randomUUID().toString();

        orchestratorService.registerPendingRun(runId);

        // Run asynchronously
        CompletableFuture.runAsync(() -> {
            orchestratorService.runSimulationWithRunId(runId, quick, username, role);
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "runId", runId,
                "status", "ACCEPTED",
                "message", "Simulation run triggered asynchronously.",
                "quickMode", quick
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "Get status of the latest simulation run (ADMIN only)")
    public ResponseEntity<?> getLatestSimulationStatus() {
        return orchestratorService.getLatestStatus()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get status of a simulation run by ID (ADMIN only)")
    public ResponseEntity<?> getSimulationStatus(@PathVariable String id) {
        return orchestratorService.getStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/report")
    @Operation(summary = "Get the latest simulation JSON report (ADMIN only)")
    public ResponseEntity<Resource> getLatestReportJson() {
        File file = new File("reports/simulation-report.json");
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"simulation-report.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    @GetMapping("/{id}/report")
    @Operation(summary = "Get simulation JSON report by run ID (ADMIN only)")
    public ResponseEntity<Resource> getReportJsonByRunId(@PathVariable String id) {
        var statusOpt = orchestratorService.getStatus(id);
        if (statusOpt.isEmpty() || statusOpt.get().reportJsonPath() == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(statusOpt.get().reportJsonPath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"simulation-report-" + id + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }
}
