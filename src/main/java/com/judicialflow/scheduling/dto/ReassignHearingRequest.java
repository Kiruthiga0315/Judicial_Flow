package com.judicialflow.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignHearingRequest {

    @NotNull(message = "New judge ID is required")
    private UUID judgeId;

    @NotNull(message = "New courtroom ID is required")
    private UUID courtroomId;

    @NotNull(message = "New scheduled time is required")
    private LocalDateTime scheduledTime;

    private Integer durationMinutes;

    @NotBlank(message = "Reason for reassignment is required")
    private String reason;

    private String litigantMessage;
}
