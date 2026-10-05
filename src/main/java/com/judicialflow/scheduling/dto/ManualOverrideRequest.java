package com.judicialflow.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for manual scheduling override.
 *
 * <p>Allows a registrar to force a specific judge/courtroom/time for a case,
 * bypassing the engine's proposal. The reason is mandatory for audit trail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualOverrideRequest {

    @NotNull(message = "Case ID is required")
    private UUID caseId;

    @NotNull(message = "Judge ID is required")
    private UUID judgeId;

    @NotNull(message = "Courtroom ID is required")
    private UUID courtroomId;

    @NotNull(message = "Scheduled time is required")
    private LocalDateTime scheduledTime;

    private int durationMinutes;

    @NotBlank(message = "Reason is required for audit trail")
    private String reason;

    @NotBlank(message = "Overridden by (registrar name) is required")
    private String overriddenBy;
}
