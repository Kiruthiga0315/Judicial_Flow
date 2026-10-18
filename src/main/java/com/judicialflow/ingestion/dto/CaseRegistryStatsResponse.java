package com.judicialflow.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated docket and urgency KPI statistics across the legal registry")
public class CaseRegistryStatsResponse {

    @Schema(description = "Total registered matters matching the current filter scope", example = "2101")
    private long totalCases;

    @Schema(description = "Count of active open cases with critical urgency priority score >= 70", example = "315")
    private long criticalUrgencyCount;

    @Schema(description = "Count of active open cases with statutory urgency priority score between 45 and 69", example = "840")
    private long highStatutoryCount;

    @Schema(description = "Count of matters currently scheduled or heard in active hearing slots", example = "185")
    private long allocatedSlotsCount;

    @Schema(description = "Count of active open cases pending for more than 180 days", example = "150")
    private long extremePendencyCount;

    @Schema(description = "Count of active open cases pending between 90 and 180 days", example = "300")
    private long elevatedAgingCount;

    @Schema(description = "Count of active open cases with statutory deadline within 15 days", example = "42")
    private long imminentStatutoryCount;
}
