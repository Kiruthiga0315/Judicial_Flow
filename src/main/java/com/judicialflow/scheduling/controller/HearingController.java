package com.judicialflow.scheduling.controller;

import com.judicialflow.scheduling.dto.HearingResponse;
import com.judicialflow.scheduling.dto.ReassignHearingRequest;
import com.judicialflow.scheduling.service.HearingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hearings")
@RequiredArgsConstructor
@Tag(name = "Hearings", description = "Endpoints for managing court hearings and manual reassignments")
public class HearingController {

    private final HearingService hearingService;

    @GetMapping
    @Operation(summary = "List hearings with optional filters", description = "Filter hearings by date range, judge, courtroom. Scoping handled fail-closed in HearingService.")
    public ResponseEntity<List<HearingResponse>> listHearings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) UUID judgeId,
            @RequestParam(required = false) UUID courtroomId) {
        return ResponseEntity.ok(hearingService.listHearings(from, to, judgeId, courtroomId));
    }

    @GetMapping("/cases/{caseId}")
    @Operation(summary = "Get current/latest hearing for a case", description = "Returns latest hearing for a case. Scoping handled fail-closed in HearingService.")
    public ResponseEntity<HearingResponse> getHearingForCase(@PathVariable UUID caseId) {
        HearingResponse response = hearingService.getCurrentHearingForCase(caseId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reassign")
    @Operation(summary = "Manually reassign a hearing", description = "Allows REGISTRAR or ADMIN to reassign a hearing to a new judge, courtroom, and time slot with conflict checks.")
    public ResponseEntity<HearingResponse> reassignHearing(
            @PathVariable UUID id,
            @Valid @RequestBody ReassignHearingRequest request) {
        HearingResponse response = hearingService.reassignHearing(id, request);
        return ResponseEntity.ok(response);
    }
}
