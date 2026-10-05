package com.judicialflow.scheduling.engine;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The output of a scheduling engine run: proposed assignments, decision logs,
 * and unschedulable cases with reasons.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingResult {

    @Builder.Default
    private List<ProposedAssignment> assignments = new ArrayList<>();

    @Builder.Default
    private List<UnschedulableCase> unschedulableCases = new ArrayList<>();

    @Builder.Default
    private Map<UUID, Integer> hearingsPerJudge = new HashMap<>();

    private int totalCasesInput;
    private int totalAssigned;
    private int totalUnschedulable;

    /** Total weighted soft cost of all assignments. Lower is better. */
    private BigDecimal totalWeightedSoftCost;

    /** Breakdown of the total cost by soft constraint. */
    @Builder.Default
    private Map<String, BigDecimal> costBreakdown = new HashMap<>();

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProposedAssignment {
        private UUID caseId;
        private String caseNumber;
        private UUID judgeId;
        private String judgeName;
        private UUID courtroomId;
        private String courtroomName;
        private LocalDateTime proposedTime;
        private int durationMinutes;
        private BigDecimal casePriorityScore;

        /** The decision record explaining why this slot was chosen. */
        private DecisionRecord decision;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DecisionRecord {
        private String caseNumber;

        // Chosen slot details
        private String chosenJudgeName;
        private String chosenCourtroomName;
        private LocalDateTime chosenTime;
        private BigDecimal chosenSoftScore;

        // Runner-up details (null if only one candidate existed)
        private String runnerUpJudgeName;
        private String runnerUpCourtroomName;
        private LocalDateTime runnerUpTime;
        private BigDecimal runnerUpSoftScore;
        private String runnerUpRejectionReason;

        /** Which hard constraints were verified as satisfied. */
        private List<String> constraintsSatisfied;

        /** Full human-readable explanation of the decision. */
        private String explanation;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UnschedulableCase {
        private UUID caseId;
        private String caseNumber;
        private String reason;
    }
}
