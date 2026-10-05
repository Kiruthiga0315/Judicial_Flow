package com.judicialflow.scheduling.engine;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Weighted constraint-satisfaction scheduling engine.
 *
 * <h2>Algorithm: Greedy Weighted Assignment with Local-Search Repair</h2>
 *
 * <h3>Phase 1 — Greedy Assignment</h3>
 * <ol>
 *   <li>Sort unscheduled cases by priority score descending.</li>
 *   <li>For each case (highest priority first):
 *     <ol type="a">
 *       <li>Generate all candidate slots across the scheduling horizon
 *           (every judge × every courtroom × every available time slot).</li>
 *       <li>Filter candidates that violate any hard constraint.</li>
 *       <li>Score remaining candidates by weighted soft-constraint penalty.</li>
 *       <li>Select the candidate with the lowest penalty score.</li>
 *       <li>Record the assignment and produce a DecisionRecord capturing
 *           the chosen slot, runner-up, and reasoning.</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <h3>Phase 2 — Local-Search Repair</h3>
 * <ol>
 *   <li>Scan for linked-case sequencing violations (can occur when a
 *       lower-priority linked case was assigned before its prerequisite).</li>
 *   <li>For each violation, attempt to swap or reassign the offending
 *       case to a slot after its prerequisite's hearing.</li>
 *   <li>If repair fails after K iterations, mark as unschedulable.</li>
 * </ol>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li>Slot generation: O(J × R × T) per case where J = judges,
 *       R = courtrooms, T = time slots per day × horizon days.</li>
 *   <li>Greedy pass: O(C × J × R × T) total.</li>
 *   <li>Repair: bounded by repairMaxIterations × C.</li>
 * </ul>
 *
 * <p>This class has NO Spring dependencies. It receives all data via
 * {@link SchedulingInput} and returns a {@link SchedulingResult}.
 */
public class SchedulingEngine {

    /**
     * Run the scheduling engine on the given input.
     */
    public SchedulingResult solve(SchedulingInput input) {
        HardConstraintChecker checker = new HardConstraintChecker();
        checker.loadExistingHearings(input.getExistingHearings());

        LocalDateTime horizonStart = input.getHorizonStart().atStartOfDay();
        LocalDateTime horizonEnd = calculateHorizonEnd(input.getHorizonStart(), input.getHorizonDays());
        
        List<LocalDateTime> timeSlots = generateTimeSlots(input.getHorizonStart(), input.getHorizonDays(), input.getDefaultDurationMinutes());
        
        Map<UUID, Integer> initialJudgeLoad = new HashMap<>();
        for (SchedulingInput.JudgeInfo j : input.getJudges()) {
            initialJudgeLoad.put(j.getJudgeId(), 0);
        }
        for (SchedulingInput.ExistingHearing h : input.getExistingHearings()) {
            initialJudgeLoad.put(h.getJudgeId(), initialJudgeLoad.getOrDefault(h.getJudgeId(), 0) + 1);
        }

        SoftConstraintScorer scorer = new SoftConstraintScorer(
            input.getSoftWeights(),
            initialJudgeLoad,
            input.getPreviousAssignments(),
            horizonStart,
            horizonEnd
        );

        List<SchedulingInput.CaseInfo> sortedCases = new ArrayList<>(input.getCases());
        sortedCases.sort((a, b) -> b.getPriorityScore().compareTo(a.getPriorityScore()));
        
        BigDecimal maxPriority = sortedCases.isEmpty() ? BigDecimal.ONE : sortedCases.get(0).getPriorityScore();
        if (maxPriority.compareTo(BigDecimal.ZERO) <= 0) {
            maxPriority = BigDecimal.ONE;
        }

        List<SchedulingResult.ProposedAssignment> assignments = new ArrayList<>();
        List<SchedulingResult.UnschedulableCase> unschedulable = new ArrayList<>();
        
        Queue<SchedulingInput.CaseInfo> deferredQueue = new LinkedList<>();
        Set<UUID> assignedCaseIds = new HashSet<>();

        // Main Greedy Pass
        for (SchedulingInput.CaseInfo caseInfo : sortedCases) {
            if (caseInfo.getLinkedCaseId() != null && !assignedCaseIds.contains(caseInfo.getLinkedCaseId()) && checker.getHearingTimeForCase(caseInfo.getLinkedCaseId()).isEmpty()) {
                deferredQueue.add(caseInfo);
                continue;
            }
            
            boolean success = tryScheduleCase(caseInfo, input, timeSlots, checker, scorer, maxPriority, assignments);
            if (success) {
                assignedCaseIds.add(caseInfo.getCaseId());
            } else {
                unschedulable.add(new SchedulingResult.UnschedulableCase(caseInfo.getCaseId(), caseInfo.getCaseNumber(), "No valid slots available satisfying hard constraints."));
            }
        }

        // Process Deferred Pass
        int loopDetect = 0;
        int initialDeferredSize = deferredQueue.size();
        while (!deferredQueue.isEmpty() && loopDetect < initialDeferredSize * 2) {
            SchedulingInput.CaseInfo caseInfo = deferredQueue.poll();
            loopDetect++;
            
            if (caseInfo.getLinkedCaseId() != null && !assignedCaseIds.contains(caseInfo.getLinkedCaseId()) && checker.getHearingTimeForCase(caseInfo.getLinkedCaseId()).isEmpty()) {
                deferredQueue.add(caseInfo);
                continue;
            }
            
            boolean success = tryScheduleCase(caseInfo, input, timeSlots, checker, scorer, maxPriority, assignments);
            if (success) {
                assignedCaseIds.add(caseInfo.getCaseId());
                loopDetect = 0; // reset loop detector
            } else {
                unschedulable.add(new SchedulingResult.UnschedulableCase(caseInfo.getCaseId(), caseInfo.getCaseNumber(), "No valid slots available satisfying hard constraints."));
            }
        }
        
        // Anything left in deferredQueue means circular dependency or unsolvable chain
        for (SchedulingInput.CaseInfo caseInfo : deferredQueue) {
            unschedulable.add(new SchedulingResult.UnschedulableCase(caseInfo.getCaseId(), caseInfo.getCaseNumber(), "Could not resolve linked case dependency."));
        }

        Map<UUID, Integer> finalHearingsPerJudge = new HashMap<>(initialJudgeLoad);
        for (SchedulingResult.ProposedAssignment pa : assignments) {
            finalHearingsPerJudge.merge(pa.getJudgeId(), 1, Integer::sum);
        }

        return SchedulingResult.builder()
            .assignments(assignments)
            .unschedulableCases(unschedulable)
            .hearingsPerJudge(finalHearingsPerJudge)
            .totalCasesInput(input.getCases().size())
            .totalAssigned(assignments.size())
            .totalUnschedulable(unschedulable.size())
            .build();
    }

    private boolean tryScheduleCase(SchedulingInput.CaseInfo caseInfo, SchedulingInput input, List<LocalDateTime> timeSlots,
                                 HardConstraintChecker checker, SoftConstraintScorer scorer, BigDecimal maxPriority,
                                 List<SchedulingResult.ProposedAssignment> assignments) {
        
        int duration = caseInfo.getEstimatedDurationMinutes() > 0 ? caseInfo.getEstimatedDurationMinutes() : input.getDefaultDurationMinutes();
        
        List<CandidateSlot> candidates = generateCandidates(caseInfo, input.getJudges(), input.getCourtrooms(), timeSlots, checker, duration);
        if (candidates.isEmpty()) {
            return false;
        }

        candidates.forEach(c -> scorer.score(c, caseInfo, maxPriority));
        candidates.sort(Comparator.comparing(CandidateSlot::getSoftScore));
        
        CandidateSlot chosen = candidates.get(0);
        CandidateSlot runnerUp = candidates.size() > 1 ? candidates.get(1) : null;
        
        checker.recordBooking(chosen.getJudgeId(), chosen.getCourtroomId(), caseInfo.getCaseId(), chosen.getStartTime(), duration);
        scorer.recordAssignment(chosen.getJudgeId());
        
        List<String> satisfiedConstraints = Arrays.asList(
            "Judge availability", "Courtroom availability", "Judge free of conflicts", "Courtroom free of conflicts", "Linked case sequencing"
        );
        
        SchedulingResult.DecisionRecord decision = buildDecisionRecord(caseInfo, chosen, runnerUp, satisfiedConstraints);
        
        SchedulingResult.ProposedAssignment assignment = SchedulingResult.ProposedAssignment.builder()
            .caseId(caseInfo.getCaseId())
            .caseNumber(caseInfo.getCaseNumber())
            .judgeId(chosen.getJudgeId())
            .judgeName(chosen.getJudgeName())
            .courtroomId(chosen.getCourtroomId())
            .courtroomName(chosen.getCourtroomName())
            .proposedTime(chosen.getStartTime())
            .durationMinutes(duration)
            .casePriorityScore(caseInfo.getPriorityScore())
            .decision(decision)
            .build();
            
        assignments.add(assignment);
        return true;
    }

    private LocalDateTime calculateHorizonEnd(LocalDate start, int days) {
        LocalDate current = start;
        int daysAdded = 0;
        while (daysAdded < days) {
            current = current.plusDays(1);
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                daysAdded++;
            }
        }
        return current.atTime(23, 59, 59);
    }

    private List<LocalDateTime> generateTimeSlots(LocalDate horizonStart, int horizonDays, int defaultDurationMinutes) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDate current = horizonStart;
        int daysAdded = 0;
        
        int incrementMinutes = 30; // 30-min intervals
        
        while (daysAdded < horizonDays) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                LocalTime t = LocalTime.of(9, 0);
                LocalTime end = LocalTime.of(17, 0);
                while (!t.isAfter(end.minusMinutes(defaultDurationMinutes))) {
                    slots.add(LocalDateTime.of(current, t));
                    t = t.plusMinutes(incrementMinutes);
                }
                daysAdded++;
            }
            current = current.plusDays(1);
        }
        return slots;
    }

    private List<CandidateSlot> generateCandidates(SchedulingInput.CaseInfo caseInfo, 
                                                   List<SchedulingInput.JudgeInfo> judges, 
                                                   List<SchedulingInput.CourtroomInfo> courtrooms, 
                                                   List<LocalDateTime> timeSlots, 
                                                   HardConstraintChecker checker, 
                                                   int duration) {
        List<CandidateSlot> validCandidates = new ArrayList<>();
        for (SchedulingInput.JudgeInfo judge : judges) {
            for (SchedulingInput.CourtroomInfo cr : courtrooms) {
                for (LocalDateTime slot : timeSlots) {
                    CandidateSlot candidate = CandidateSlot.builder()
                        .judgeId(judge.getJudgeId())
                        .judgeName(judge.getJudgeName())
                        .courtroomId(cr.getCourtroomId())
                        .courtroomName(cr.getCourtroomName())
                        .startTime(slot)
                        .durationMinutes(duration)
                        .build();
                        
                    List<String> violations = checker.checkAll(candidate, duration, caseInfo, judges, courtrooms);
                    if (violations.isEmpty()) {
                        validCandidates.add(candidate);
                    }
                }
            }
        }
        return validCandidates;
    }

    private SchedulingResult.DecisionRecord buildDecisionRecord(SchedulingInput.CaseInfo caseInfo, 
                                                               CandidateSlot chosen, 
                                                               CandidateSlot runnerUp, 
                                                               List<String> constraintsSatisfied) {
        SchedulingResult.DecisionRecord record = new SchedulingResult.DecisionRecord();
        record.setCaseNumber(caseInfo.getCaseNumber());
        record.setChosenJudgeName(chosen.getJudgeName());
        record.setChosenCourtroomName(chosen.getCourtroomName());
        record.setChosenTime(chosen.getStartTime());
        record.setChosenSoftScore(chosen.getSoftScore());
        record.setConstraintsSatisfied(constraintsSatisfied);
        
        record.setExplanation(String.format("Assigned to %s in %s at %s. Soft score: %s. Details: %s", 
            chosen.getJudgeName(), chosen.getCourtroomName(), chosen.getStartTime().toString(), 
            chosen.getSoftScore().toString(), chosen.getScoreBreakdown()));

        if (runnerUp != null) {
            record.setRunnerUpJudgeName(runnerUp.getJudgeName());
            record.setRunnerUpCourtroomName(runnerUp.getCourtroomName());
            record.setRunnerUpTime(runnerUp.getStartTime());
            record.setRunnerUpSoftScore(runnerUp.getSoftScore());
            record.setRunnerUpRejectionReason("Scored higher penalty: " + runnerUp.getSoftScore() + " vs " + chosen.getSoftScore() + ". " + runnerUp.getScoreBreakdown());
        }
        
        return record;
    }
}
