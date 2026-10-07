package com.judicialflow.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to register a leave period for a judge")
public class JudgeLeaveRequest {

    @NotNull(message = "Start date is required")
    @Schema(description = "Leave start date (YYYY-MM-DD)", example = "2026-10-15")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Schema(description = "Leave end date (YYYY-MM-DD)", example = "2026-10-17")
    private LocalDate endDate;

    @Schema(description = "Reason or remarks for the leave", example = "Judicial training conference")
    private String reason;
}
