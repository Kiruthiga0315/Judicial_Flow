package com.judicialflow.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST response for a complete scheduling run.
 *
 * <p>Contains run metadata, the full list of proposed assignments (each with
 * its decision log), unschedulable cases, and workload distribution summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingRunResponse {

    private UUID runId;
    private String status;
    private LocalDateTime triggeredAt;
    private LocalDateTime completedAt;

    // Summary statistics
    private int totalCasesInput;
    private int totalAssigned;
    private int totalUnschedulable;
    private int horizonDays;
    private int defaultDurationMinutes;

    /** All proposed assignments, ordered by proposed time. */
    @Builder.Default
    private List<ProposalResponse> proposals = new ArrayList<>();

    /** Cases that could not be scheduled, with reasons. */
    @Builder.Default
    private List<UnschedulableCaseResponse> unschedulableCases = new ArrayList<>();

    /** Hearings-per-judge workload distribution. */
    private Map<String, Integer> workloadDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnschedulableCaseResponse {
        private UUID caseId;
        private String caseNumber;
        private String reason;
    }
}
