package com.judicialflow.ingestion.dto;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter criteria for listing cases")
public class CaseFilterCriteria {

    @Schema(description = "Filter by case status", example = "FILED")
    private CaseStatus status;

    @Schema(description = "Filter by case type", example = "CIVIL")
    private CaseType caseType;

    @Schema(description = "Filter by assigned judge or hearing judge UUID")
    private UUID judgeId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Filter by filing date on or after this date (inclusive)", example = "2024-01-01")
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Filter by filing date on or before this date (inclusive)", example = "2024-12-31")
    private LocalDate endDate;

    @Schema(description = "Include soft-deleted cases in results (default false)", example = "false")
    @Builder.Default
    private boolean includeDeleted = false;
}
