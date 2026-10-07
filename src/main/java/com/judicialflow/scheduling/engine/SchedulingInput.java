package com.judicialflow.scheduling.engine;

import lombok.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Immutable input bundle for the scheduling engine.
 * Contains all data needed to produce a conflict-free schedule proposal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingInput {

    /** Cases to schedule, each with their priority score. */
    @Builder.Default
    private List<CaseInfo> cases = new ArrayList<>();

    /** All judges with their availability windows. */
    @Builder.Default
    private List<JudgeInfo> judges = new ArrayList<>();

    /** All courtrooms with their availability windows. */
    @Builder.Default
    private List<CourtroomInfo> courtrooms = new ArrayList<>();

    /** Existing committed hearings (for conflict detection). */
    @Builder.Default
    private List<ExistingHearing> existingHearings = new ArrayList<>();

    /** Proposals from the previous run (for churn calculation). */
    @Builder.Default
    private Map<UUID, PreviousAssignment> previousAssignments = new HashMap<>();

    /** Scheduling horizon start date (inclusive). */
    private LocalDate horizonStart;

    /** Number of business days to schedule into. */
    @Builder.Default
    private int horizonDays = 5;

    /** Default hearing duration in minutes. */
    @Builder.Default
    private int defaultDurationMinutes = 60;

    /** Soft constraint weights. */
    @Builder.Default
    private SoftWeights softWeights = new SoftWeights();

    /** Max iterations for local-search repair phase. */
    @Builder.Default
    private int repairMaxIterations = 100;

    // ---- Inner DTOs ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CaseInfo {
        private UUID caseId;
        private String caseNumber;
        private BigDecimal priorityScore;
        private UUID linkedCaseId; // null if not linked
        private int estimatedDurationMinutes; // 0 = use default
        private LocalDate filingDate;
        private LocalDate effectiveFilingDate;
        private com.judicialflow.common.enums.CaseType caseType;
        private Boolean statutoryPriority;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class JudgeInfo {
        private UUID judgeId;
        private String judgeName;
        private List<AvailabilityWindow> availabilityWindows;
        @Builder.Default
        private List<DateRange> leaves = new ArrayList<>();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DateRange {
        private LocalDate startDate;
        private LocalDate endDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CourtroomInfo {
        private UUID courtroomId;
        private String courtroomName;
        private List<AvailabilityWindow> availabilityWindows;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AvailabilityWindow {
        private DayOfWeek dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExistingHearing {
        private UUID hearingId;
        private UUID caseId;
        private UUID judgeId;
        private UUID courtroomId;
        private LocalDateTime startTime;
        private int durationMinutes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PreviousAssignment {
        private UUID caseId;
        private UUID judgeId;
        private UUID courtroomId;
        private LocalDateTime startTime;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SoftWeights {
        private double priorityOrdering = 0.5;
        private double workloadBalance = 0.3;
        private double scheduleChurn = 0.2;
    }
}
