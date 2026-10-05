package com.judicialflow.priority.controller;

import com.judicialflow.ingestion.exceptions.ErrorResponse;
import com.judicialflow.priority.dto.PriorityScoreResult;
import com.judicialflow.priority.service.PriorityScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for the Phase-3 Priority / Aging Scoring Module.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/priority/cases/{caseId}/score}
 *       – Compute (and persist) a fresh score for a single case, returning the
 *         full breakdown.</li>
 *   <li>{@code GET  /api/priority/cases/{caseId}/score/latest}
 *       – Return the most recently persisted score (re-computes only if no
 *         stored score exists).</li>
 *   <li>{@code GET  /api/priority/cases/top?limit=N}
 *       – Compute fresh scores for all open cases and return the top-N ranked
 *         by urgency (highest score first).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/priority")
@RequiredArgsConstructor
@Tag(name = "Priority Scoring",
        description = "Explainable urgency scoring for open cases — "
                      + "supports per-case breakdown and system-wide top-N ranking.")
public class PriorityScoreController {

    private final PriorityScoreService priorityScoreService;

    // -------------------------------------------------------------------------
    // Single-case score (fresh computation + persist)
    // -------------------------------------------------------------------------

    @GetMapping("/cases/{caseId}/score")
    @Operation(
            summary = "Compute and persist a fresh priority score for a case",
            description = "Runs the scoring engine against current case data, persists the result "
                          + "to the priority_scores table, and returns the total score together "
                          + "with a per-factor breakdown that the registrar dashboard renders "
                          + "directly. Every call appends a new history row.")
    @ApiResponse(responseCode = "200",
            description = "Score computed and persisted successfully",
            content = @Content(schema = @Schema(implementation = PriorityScoreResult.class)))
    @ApiResponse(responseCode = "404",
            description = "Case not found or soft-deleted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PriorityScoreResult> computeScore(
            @Parameter(description = "UUID of the case to score")
            @PathVariable UUID caseId) {
        PriorityScoreResult result = priorityScoreService.computeAndPersist(caseId);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Single-case: latest stored score (or fresh if none exists)
    // -------------------------------------------------------------------------

    @GetMapping("/cases/{caseId}/score/latest")
    @Operation(
            summary = "Retrieve the latest stored priority score for a case",
            description = "Returns the most recently persisted score record with its breakdown. "
                          + "If no score has ever been computed for this case the engine runs "
                          + "immediately and the result is persisted before returning.")
    @ApiResponse(responseCode = "200",
            description = "Latest score returned",
            content = @Content(schema = @Schema(implementation = PriorityScoreResult.class)))
    @ApiResponse(responseCode = "404",
            description = "Case not found or soft-deleted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PriorityScoreResult> getLatestScore(
            @Parameter(description = "UUID of the case")
            @PathVariable UUID caseId) {
        PriorityScoreResult result = priorityScoreService.getOrComputeLatest(caseId);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // System-wide top-N ranking
    // -------------------------------------------------------------------------

    @GetMapping("/cases/top")
    @Operation(
            summary = "Get the top-N most urgent open cases system-wide",
            description = "Computes fresh scores for every open (non-disposed, non-deleted) case, "
                          + "persists each score, and returns the top-N ranked by urgency score "
                          + "descending. Each result includes the full factor breakdown. "
                          + "Useful for the registrar dashboard's priority queue view.")
    @ApiResponse(responseCode = "200",
            description = "Ranked list of top-N urgent cases",
            content = @Content(schema = @Schema(implementation = PriorityScoreResult.class)))
    @ApiResponse(responseCode = "400",
            description = "Invalid limit value",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<PriorityScoreResult>> getTopN(
            @Parameter(description = "Maximum number of cases to return (default 10, min 1)")
            @RequestParam(defaultValue = "10") int limit) {
        List<PriorityScoreResult> results = priorityScoreService.computeTopN(limit);
        return ResponseEntity.ok(results);
    }
}
