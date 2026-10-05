package com.judicialflow.scheduling.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

/**
 * Evaluates soft constraints for candidate slots and produces a weighted penalty score.
 *
 * <h2>Soft constraints</h2>
 * <ol>
 *   <li><b>Priority ordering (weight: configurable, default 0.5)</b>:
 *       Higher-priority cases should get earlier time slots. The penalty is proportional
 *       to how "late" the slot is relative to the horizon start.
 *       Penalty = (1 - normalizedPriority) × slotLateness</li>
 *   <li><b>Workload balance (weight: configurable, default 0.3)</b>:
 *       Hearings should be distributed evenly across judges. The penalty for assigning
 *       to a judge is proportional to how many hearings that judge already has compared
 *       to the average.
 *       Penalty = max(0, judgeHearingCount - averageCount)</li>
 *   <li><b>Schedule churn (weight: configurable, default 0.2)</b>:
 *       If a previous run assigned this case to a different slot, there is a penalty
 *       for changing the assignment.
 *       Penalty = 1.0 if the slot differs from the previous assignment, 0.0 otherwise</li>
 * </ol>
 */
public class SoftConstraintScorer {

    private final SchedulingInput.SoftWeights weights;
    private final Map<UUID, Integer> currentJudgeLoad; // judgeId -> hearing count
    private final Map<UUID, SchedulingInput.PreviousAssignment> previousAssignments;
    private final LocalDateTime horizonStart;
    private final LocalDateTime horizonEnd;
    private final long totalHorizonMinutes;

    public SoftConstraintScorer(SchedulingInput.SoftWeights weights,
                                 Map<UUID, Integer> initialJudgeLoad,
                                 Map<UUID, SchedulingInput.PreviousAssignment> previousAssignments,
                                 LocalDateTime horizonStart, LocalDateTime horizonEnd) {
        this.weights = weights;
        this.currentJudgeLoad = new HashMap<>(initialJudgeLoad);
        this.previousAssignments = previousAssignments != null ? previousAssignments : Map.of();
        this.horizonStart = horizonStart;
        this.horizonEnd = horizonEnd;
        this.totalHorizonMinutes = Duration.between(horizonStart, horizonEnd).toMinutes();
    }

    /**
     * Score a candidate slot for a given case. Lower score = better assignment.
     * Returns the combined weighted penalty and sets the scoreBreakdown on the candidate.
     */
    public BigDecimal score(CandidateSlot candidate, SchedulingInput.CaseInfo caseInfo,
                           BigDecimal maxPriorityScore) {
        
        // 1. Priority Ordering Penalty
        double priorityPenalty = calculatePriorityPenalty(candidate.getStartTime(), caseInfo.getPriorityScore(), maxPriorityScore);
        
        // 2. Workload Balance Penalty
        double workloadPenalty = calculateWorkloadPenalty(candidate.getJudgeId());
        
        // 3. Schedule Churn Penalty
        double churnPenalty = calculateChurnPenalty(candidate, caseInfo.getCaseId());

        // Combine
        double combinedScore = 
            (priorityPenalty * weights.getPriorityOrdering()) +
            (workloadPenalty * weights.getWorkloadBalance()) +
            (churnPenalty * weights.getScheduleChurn());

        BigDecimal finalScore = BigDecimal.valueOf(combinedScore).setScale(4, RoundingMode.HALF_UP);
        candidate.setSoftScore(finalScore);
        
        candidate.setPriorityPenaltyVal(BigDecimal.valueOf(priorityPenalty * weights.getPriorityOrdering()).setScale(4, RoundingMode.HALF_UP));
        candidate.setWorkloadPenaltyVal(BigDecimal.valueOf(workloadPenalty * weights.getWorkloadBalance()).setScale(4, RoundingMode.HALF_UP));
        candidate.setChurnPenaltyVal(BigDecimal.valueOf(churnPenalty * weights.getScheduleChurn()).setScale(4, RoundingMode.HALF_UP));
        
        String breakdown = String.format(
            "Priority Penalty: %.2f (wt: %.2f), Workload Penalty: %.2f (wt: %.2f), Churn Penalty: %.2f (wt: %.2f)",
            priorityPenalty, weights.getPriorityOrdering(),
            workloadPenalty, weights.getWorkloadBalance(),
            churnPenalty, weights.getScheduleChurn()
        );
        candidate.setScoreBreakdown(breakdown);

        return finalScore;
    }

    /** Record that a judge has been assigned one more hearing (call after each assignment). */
    public void recordAssignment(UUID judgeId) {
        currentJudgeLoad.merge(judgeId, 1, Integer::sum);
    }

    public void removeAssignment(UUID judgeId) {
        currentJudgeLoad.computeIfPresent(judgeId, (k, v) -> v > 0 ? v - 1 : 0);
    }

    private double calculatePriorityPenalty(LocalDateTime start, BigDecimal priorityScore, BigDecimal maxPriorityScore) {
        if (totalHorizonMinutes <= 0) return 0.0;
        
        long minutesIntoHorizon = Duration.between(horizonStart, start).toMinutes();
        double slotLateness = Math.max(0.0, Math.min(1.0, (double) minutesIntoHorizon / totalHorizonMinutes));
        
        double normalizedPriority = 0.0;
        if (maxPriorityScore != null && maxPriorityScore.compareTo(BigDecimal.ZERO) > 0) {
            normalizedPriority = priorityScore.doubleValue() / maxPriorityScore.doubleValue();
        }
        
        return Math.max(0.0, (1.0 - normalizedPriority) * slotLateness);
    }

    private double calculateWorkloadPenalty(UUID judgeId) {
        if (currentJudgeLoad.isEmpty()) return 0.0;
        
        double totalLoad = currentJudgeLoad.values().stream().mapToInt(Integer::intValue).sum();
        double avgLoad = totalLoad / currentJudgeLoad.size();
        
        int judgeLoad = currentJudgeLoad.getOrDefault(judgeId, 0);
        return Math.max(0.0, judgeLoad - avgLoad);
    }

    private double calculateChurnPenalty(CandidateSlot candidate, UUID caseId) {
        SchedulingInput.PreviousAssignment prev = previousAssignments.get(caseId);
        if (prev == null) {
            return 0.0; // no previous assignment, no churn
        }
        
        boolean isSameJudge = candidate.getJudgeId().equals(prev.getJudgeId());
        boolean isSameCourtroom = candidate.getCourtroomId().equals(prev.getCourtroomId());
        boolean isSameTime = candidate.getStartTime().equals(prev.getStartTime());
        
        if (isSameJudge && isSameCourtroom && isSameTime) {
            return 0.0;
        }
        return 1.0;
    }
}
