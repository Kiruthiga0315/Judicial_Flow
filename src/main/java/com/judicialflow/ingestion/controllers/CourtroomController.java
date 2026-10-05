package com.judicialflow.ingestion.controllers;

import com.judicialflow.ingestion.dto.*;
import com.judicialflow.ingestion.exceptions.ErrorResponse;
import com.judicialflow.ingestion.services.CourtroomService;
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
@RequestMapping("/api/courtrooms")
@RequiredArgsConstructor
@Tag(name = "Courtrooms", description = "Endpoints for managing courtrooms, capacities, and scheduling availability")
public class CourtroomController {

    private final CourtroomService courtroomService;

    @PostMapping
    @Operation(summary = "Create a new courtroom", description = "Registers a new courtroom with seating capacity and initial availability.")
    @ApiResponse(responseCode = "201", description = "Courtroom created successfully", content = @Content(schema = @Schema(implementation = CourtroomResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CourtroomResponse> createCourtroom(@Valid @RequestBody CreateCourtroomRequest request) {
        CourtroomResponse response = courtroomService.createCourtroom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update courtroom details", description = "Updates name and capacity of an existing courtroom.")
    @ApiResponse(responseCode = "200", description = "Courtroom updated successfully", content = @Content(schema = @Schema(implementation = CourtroomResponse.class)))
    @ApiResponse(responseCode = "404", description = "Courtroom not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CourtroomResponse> updateCourtroom(
            @Parameter(description = "Courtroom UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateCourtroomRequest request) {
        CourtroomResponse response = courtroomService.updateCourtroom(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get courtroom by ID", description = "Retrieves details of a courtroom including capacity and availability.")
    @ApiResponse(responseCode = "200", description = "Courtroom retrieved successfully", content = @Content(schema = @Schema(implementation = CourtroomResponse.class)))
    @ApiResponse(responseCode = "404", description = "Courtroom not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CourtroomResponse> getCourtroomById(@Parameter(description = "Courtroom UUID") @PathVariable UUID id) {
        CourtroomResponse response = courtroomService.getCourtroomById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List all courtrooms", description = "Retrieves a paginated list of courtrooms with sorting.")
    @ApiResponse(responseCode = "200", description = "Page of courtrooms retrieved successfully")
    public ResponseEntity<PageResponse<CourtroomResponse>> listCourtrooms(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CourtroomResponse> response = courtroomService.listCourtrooms(pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/availability")
    @Operation(summary = "Set courtroom capacity/availability", description = "Updates courtroom capacity and/or availability windows.")
    @ApiResponse(responseCode = "200", description = "Capacity/availability updated successfully", content = @Content(schema = @Schema(implementation = CourtroomResponse.class)))
    @ApiResponse(responseCode = "404", description = "Courtroom not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CourtroomResponse> setCapacityAndAvailability(
            @Parameter(description = "Courtroom UUID") @PathVariable UUID id,
            @Valid @RequestBody SetCourtroomAvailabilityRequest request) {
        CourtroomResponse response = courtroomService.setCapacityAndAvailability(id, request);
        return ResponseEntity.ok(response);
    }
}
