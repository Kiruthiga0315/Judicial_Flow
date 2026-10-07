package com.judicialflow.scheduling.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDiffSummary {
    private UUID currentRunId;
    private UUID previousRunId;
    private int totalCurrentProposals;
    private int totalPreviousProposals;
    private int newAssignmentsCount;
    private int modifiedAssignmentsCount;
    private int unchangedAssignmentsCount;
    private int reprioritizedCasesCount;
    private java.util.List<AssignmentDiff> assignmentDiffs;
    private java.util.List<ReprioritizationDiff> reprioritizations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentDiff {
        private UUID caseId;
        private String caseNumber;
        private DiffType diffType; // NEW, MODIFIED, UNCHANGED, REMOVED
        private String previousJudge;
        private String currentJudge;
        private String previousCourtroom;
        private String currentCourtroom;
        private LocalDateTime previousProposedTime;
        private LocalDateTime currentProposedTime;
        private String details;
    }

    public enum DiffType {
        NEW,
        MODIFIED,
        UNCHANGED,
        REMOVED,
        COMMITTED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReprioritizationDiff {
        private UUID caseId;
        private String caseNumber;
        private BigDecimal previousScore;
        private BigDecimal currentScore;
        private BigDecimal scoreDelta;
        private String details;
    }
}
