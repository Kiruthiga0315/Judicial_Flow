package com.judicialflow.ingestion.controllers;

import com.judicialflow.ingestion.dto.*;
import com.judicialflow.ingestion.exceptions.ErrorResponse;
import com.judicialflow.ingestion.services.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Tag(name = "Cases", description = "Endpoints for managing legal cases, metadata, linked cases, and lifecycle statuses")
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    @Operation(summary = "Create a new case", description = "Ingests a new legal case into the system with filing date, case type, and optional linked cases/judge.")
    @ApiResponse(responseCode = "201", description = "Case successfully created", content = @Content(schema = @Schema(implementation = CaseResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure or invalid case type", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Case number already exists", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        CaseResponse response = caseService.createCase(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a case", description = "Updates an existing case details, status, prior adjournments, linked case, or assigned judge.")
    @ApiResponse(responseCode = "200", description = "Case successfully updated", content = @Content(schema = @Schema(implementation = CaseResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure or circular linked-case reference detected", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Case or linked entity not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Case number conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CaseResponse> updateCase(
            @Parameter(description = "Case UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseRequest request) {
        CaseResponse response = caseService.updateCase(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get case by ID", description = "Retrieves case details by UUID. Soft-deleted cases return 404 by default unless includeDeleted=true is passed.")
    @ApiResponse(responseCode = "200", description = "Case found", content = @Content(schema = @Schema(implementation = CaseResponse.class)))
    @ApiResponse(responseCode = "404", description = "Case not found or soft-deleted", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CaseResponse> getCaseById(
            @Parameter(description = "Case UUID") @PathVariable UUID id,
            @Parameter(description = "Whether to retrieve even if soft-deleted") @RequestParam(defaultValue = "false") boolean includeDeleted) {
        CaseResponse response = caseService.getCaseById(id, includeDeleted);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List cases with filters, pagination, and sorting",
            description = "Filters cases by status, case type, assigned/hearing judge, and filing date range with pagination.")
    @ApiResponse(responseCode = "200", description = "Page of cases retrieved successfully")
    public ResponseEntity<PageResponse<CaseResponse>> listCases(
            @ParameterObject CaseFilterCriteria criteria,
            @PageableDefault(size = 20, sort = "filingDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CaseResponse> response = caseService.listCases(criteria, pageable);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a case", description = "Marks a case as deleted without hard removal from the database.")
    @ApiResponse(responseCode = "204", description = "Case successfully soft-deleted")
    @ApiResponse(responseCode = "404", description = "Case not found or already deleted", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> deleteCase(@Parameter(description = "Case UUID") @PathVariable UUID id) {
        caseService.softDeleteCase(id);
        return ResponseEntity.noContent().build();
    }
}
