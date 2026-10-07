package com.judicialflow.scheduling.controller;

import com.judicialflow.scheduling.dto.ManualOverrideRequest;
import com.judicialflow.scheduling.dto.ProposalResponse;
import com.judicialflow.scheduling.dto.SchedulingConfigDto;
import com.judicialflow.scheduling.dto.SchedulingRunResponse;
import com.judicialflow.scheduling.service.SchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the Phase 4 scheduling engine.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/scheduling/run} — Trigger a scheduling run. Returns the
 *       proposed schedule + decision log. Does NOT auto-commit the schedule.</li>
 *   <li>{@code GET /api/scheduling/runs/{runId}} — Retrieve a past scheduling run's
 *       proposals and decisions.</li>
 *   <li>{@code POST /api/scheduling/override} — Manual override: force a specific
 *       judge/courtroom/time for a case, with reason recorded in audit log.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/scheduling")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scheduling Engine", description = "Phase 4: Weighted constraint-satisfaction scheduling engine")
public class SchedulingController {

    private final SchedulingService schedulingService;

    /**
     * Trigger a scheduling run. Returns the proposed schedule plus its decision log.
     * Does NOT auto-commit — the schedule is returned as a proposal the registrar can accept.
     *
     * @param configDto optional configuration overrides (horizon days, duration)
     * @return the complete scheduling run response with proposals and decision logs
     */
    @PostMapping("/run")
    @Operation(summary = "Trigger a scheduling run",
            description = "Runs the constraint-satisfaction engine on all unscheduled FILED cases. "
                    + "Returns a proposed schedule with explainable decision logs for each assignment. "
                    + "The schedule is NOT auto-committed — it is returned as a proposal.")
    public ResponseEntity<SchedulingRunResponse> triggerSchedulingRun(
            @RequestBody(required = false) SchedulingConfigDto configDto) {
        log.info("POST /api/scheduling/run triggered");
        SchedulingRunResponse response = schedulingService.triggerSchedulingRun(configDto);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a past scheduling run by its ID.
     *
     * @param runId the UUID of the scheduling run
     * @return the scheduling run response with proposals and decision logs
     */
    @GetMapping("/runs/{runId}")
    @Operation(summary = "Retrieve a scheduling run",
            description = "Returns the proposals and decision logs from a past scheduling run.")
    public ResponseEntity<SchedulingRunResponse> getSchedulingRun(@PathVariable UUID runId) {
        log.info("GET /api/scheduling/runs/{}", runId);
        SchedulingRunResponse response = schedulingService.getSchedulingRun(runId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/runs/latest")
    @Operation(summary = "Retrieve the latest completed scheduling run")
    public ResponseEntity<SchedulingRunResponse> getLatestRun() {
        log.info("GET /api/v1/scheduling/runs/latest");
        SchedulingRunResponse response = schedulingService.getLatestRun();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/proposals/latest")
    @Operation(summary = "Retrieve proposals from the latest completed scheduling run")
    public ResponseEntity<java.util.List<ProposalResponse>> getLatestProposals() {
        log.info("GET /api/v1/scheduling/proposals/latest");
        return ResponseEntity.ok(schedulingService.getLatestProposals());
    }

    /**
     * Manual override: force a specific judge/courtroom/time for a case.
     * Records the override in the audit log with a reason.
     *
     * @param request the override request with case, judge, courtroom, time, and reason
     * @return the resulting proposal response
     */
    @PostMapping("/override")
    @Operation(summary = "Apply a manual scheduling override",
            description = "Forces a specific judge/courtroom/time for a case, bypassing the engine. "
                    + "The reason is recorded in the audit log for accountability.")
    public ResponseEntity<ProposalResponse> applyManualOverride(
            @Valid @RequestBody ManualOverrideRequest request) {
        log.info("POST /api/v1/scheduling/override for case {}", request.getCaseId());
        ProposalResponse response = schedulingService.applyManualOverride(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve a proposal: creates a committed Hearing row, sets createdByEngine = true,
     * updates the case to SCHEDULED, and audits the change.
     */
    @PostMapping("/proposals/{proposalId}/approve")
    @Operation(summary = "Approve a scheduling proposal",
            description = "Creates a committed Hearing row from the proposal, sets createdByEngine = true, "
                    + "updates the case status to SCHEDULED, records before/after state in audit log, and publishes domain event.")
    public ResponseEntity<ProposalResponse> approveProposal(@PathVariable UUID proposalId) {
        log.info("POST /api/v1/scheduling/proposals/{}/approve", proposalId);
        ProposalResponse response = schedulingService.approveProposal(proposalId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reject a proposal: marks REJECTED with a reason and audits the action.
     */
    @PostMapping("/proposals/{proposalId}/reject")
    @Operation(summary = "Reject a scheduling proposal",
            description = "Marks the proposal REJECTED with a reason and records an audit log entry.")
    public ResponseEntity<ProposalResponse> rejectProposal(
            @PathVariable UUID proposalId,
            @RequestBody(required = false) com.judicialflow.scheduling.dto.RejectProposalRequest request) {
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Rejected by registrar";
        log.info("POST /api/v1/scheduling/proposals/{}/reject (reason={})", proposalId, reason);
        ProposalResponse response = schedulingService.rejectProposal(proposalId, reason);
        return ResponseEntity.ok(response);
    }
}
