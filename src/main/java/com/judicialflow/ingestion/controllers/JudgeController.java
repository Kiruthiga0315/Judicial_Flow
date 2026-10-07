package com.judicialflow.ingestion.controllers;

import com.judicialflow.ingestion.dto.*;
import com.judicialflow.ingestion.exceptions.ErrorResponse;
import com.judicialflow.ingestion.services.JudgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/judges")
@RequiredArgsConstructor
@Tag(name = "Judges", description = "Endpoints for managing judges and their availability windows")
public class JudgeController {

    private final JudgeService judgeService;

    @PostMapping
    @Operation(summary = "Create a new judge", description = "Registers a new judge with optional specialization and initial availability windows.")
    @ApiResponse(responseCode = "201", description = "Judge created successfully", content = @Content(schema = @Schema(implementation = JudgeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<JudgeResponse> createJudge(@Valid @RequestBody CreateJudgeRequest request) {
        JudgeResponse response = judgeService.createJudge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update judge details", description = "Updates the name and specialization of an existing judge.")
    @ApiResponse(responseCode = "200", description = "Judge updated successfully", content = @Content(schema = @Schema(implementation = JudgeResponse.class)))
    @ApiResponse(responseCode = "404", description = "Judge not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<JudgeResponse> updateJudge(
            @Parameter(description = "Judge UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateJudgeRequest request) {
        JudgeResponse response = judgeService.updateJudge(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get judge by ID", description = "Retrieves details of a judge including their availability windows.")
    @ApiResponse(responseCode = "200", description = "Judge retrieved successfully", content = @Content(schema = @Schema(implementation = JudgeResponse.class)))
    @ApiResponse(responseCode = "404", description = "Judge not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<JudgeResponse> getJudgeById(@Parameter(description = "Judge UUID") @PathVariable UUID id) {
        JudgeResponse response = judgeService.getJudgeById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List all judges", description = "Retrieves a paginated list of judges with sorting.")
    @ApiResponse(responseCode = "200", description = "Page of judges retrieved successfully")
    public ResponseEntity<PageResponse<JudgeResponse>> listJudges(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<JudgeResponse> response = judgeService.listJudges(pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/availability")
    @Operation(summary = "Set judge availability windows", description = "Configures weekly hearing availability windows for a judge.")
    @ApiResponse(responseCode = "200", description = "Availability windows updated successfully", content = @Content(schema = @Schema(implementation = JudgeResponse.class)))
    @ApiResponse(responseCode = "404", description = "Judge not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure in availability windows", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<JudgeResponse> setAvailabilityWindows(
            @Parameter(description = "Judge UUID") @PathVariable UUID id,
            @Valid @RequestBody SetJudgeAvailabilityRequest request) {
        JudgeResponse response = judgeService.setAvailabilityWindows(id, request);
        return ResponseEntity.ok(response);
    }
}
