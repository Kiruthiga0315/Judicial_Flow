package com.judicialflow.simulation.validation;

import com.judicialflow.scheduling.engine.CandidateSlot;
import com.judicialflow.scheduling.engine.HardConstraintChecker;
import com.judicialflow.scheduling.engine.SchedulingInput;
import com.judicialflow.scheduling.engine.SchedulingResult;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Baseline Scheduler: First-Come, First-Served (FCFS).
 * Evaluates candidate slots in chronological order, assigning each case to the earliest
 * available slot that satisfies the EXACT SAME HardConstraintChecker rules as the engine.
 *
 * Implements three judge-selection variants:
 * 1. ROUND_ROBIN (Headline Baseline): Rotates judge selection cyclically across assignments
 * 2. LEAST_LOADED: Chooses the judge with lowest current cumulative workload at earliest feasible slot
 * 3. NAIVE (Strawman): Scans judges starting from index 0 every time (the previous behavior)
 *
 * All three ignore case type and priority score entirely, sorting cases strictly by filing date ASC.
 */
@Component
public class FcfsScheduler {

    public enum FcfsVariant {
        ROUND_ROBIN,
        LEAST_LOADED,
        NAIVE,
        TIERED
    }

    public SchedulingResult schedule(SchedulingInput input) {
        return schedule(input, FcfsVariant.ROUND_ROBIN);
    }

    public SchedulingResult schedule(SchedulingInput input, FcfsVariant variant) {
        HardConstraintChecker checker = new HardConstraintChecker();
        checker.loadExistingHearings(input.getExistingHearings());

        List<SchedulingInput.CaseInfo> sortedCases = new ArrayList<>(input.getCases());
        if (variant == FcfsVariant.TIERED) {
            sortedCases.sort((a, b) -> {
                boolean aStat = a.getStatutoryPriority() != null ? a.getStatutoryPriority() : isStatutoryType(a.getCaseType());
                boolean bStat = b.getStatutoryPriority() != null ? b.getStatutoryPriority() : isStatutoryType(b.getCaseType());
                if (aStat != bStat) {
                    return aStat ? -1 : 1; // Tier 1 (statutory) first, Tier 2 (non-priority) second
                }
                LocalDate dateA = a.getEffectiveFilingDate() != null ? a.getEffectiveFilingDate() : a.getFilingDate();
                LocalDate dateB = b.getEffectiveFilingDate() != null ? b.getEffectiveFilingDate() : b.getFilingDate();
                if (dateA != null && dateB != null) {
                    int cmp = dateA.compareTo(dateB);
                    if (cmp != 0) return cmp;
                }
                return a.getCaseNumber().compareTo(b.getCaseNumber());
            });
        } else {
            // Sort eligible cases strictly by filing date ascending (or effective filing date), tie-breaker: case number ascending
            // Priority score and case type are ignored.
            sortedCases.sort((a, b) -> {
                LocalDate dateA = a.getEffectiveFilingDate() != null ? a.getEffectiveFilingDate() : a.getFilingDate();
                LocalDate dateB = b.getEffectiveFilingDate() != null ? b.getEffectiveFilingDate() : b.getFilingDate();
                if (dateA != null && dateB != null) {
                    int cmp = dateA.compareTo(dateB);
                    if (cmp != 0) return cmp;
                }
                return a.getCaseNumber().compareTo(b.getCaseNumber());
            });
        }

        List<LocalDateTime> timeSlots = generateTimeSlots(
                input.getHorizonStart(),
                input.getHorizonDays(),
                input.getDefaultDurationMinutes(),
                input.getCourtrooms()
        );

        List<SchedulingResult.ProposedAssignment> assignments = new ArrayList<>();
        List<SchedulingResult.UnschedulableCase> unschedulable = new ArrayList<>();
        Map<UUID, Integer> judgeLoad = new HashMap<>();
        input.getJudges().forEach(j -> judgeLoad.put(j.getJudgeId(), 0));

        int roundRobinJudgeIndex = 0;

        for (SchedulingInput.CaseInfo caseInfo : sortedCases) {
            int duration = caseInfo.getEstimatedDurationMinutes() > 0 ?
                    caseInfo.getEstimatedDurationMinutes() : input.getDefaultDurationMinutes();

            ChosenSlotResult slotResult = findEarliestValidSlot(
                    caseInfo, input, timeSlots, checker, duration, variant, roundRobinJudgeIndex, judgeLoad
            );

            if (slotResult != null && slotResult.slot != null) {
                CandidateSlot chosenSlot = slotResult.slot;
                roundRobinJudgeIndex = slotResult.nextRoundRobinIndex;

                checker.recordBooking(
                        chosenSlot.getJudgeId(),
                        chosenSlot.getCourtroomId(),
                        caseInfo.getCaseId(),
                        chosenSlot.getStartTime(),
                        duration
                );
                judgeLoad.merge(chosenSlot.getJudgeId(), 1, Integer::sum);

                SchedulingResult.DecisionRecord decision = new SchedulingResult.DecisionRecord();
                decision.setCaseNumber(caseInfo.getCaseNumber());
                decision.setChosenJudgeName(chosenSlot.getJudgeName());
                decision.setChosenCourtroomName(chosenSlot.getCourtroomName());
                decision.setChosenTime(chosenSlot.getStartTime());
                decision.setExplanation("FCFS (" + variant + ") assignment: earliest feasible slot without constraint violation.");

                SchedulingResult.ProposedAssignment assignment = SchedulingResult.ProposedAssignment.builder()
                        .caseId(caseInfo.getCaseId())
                        .caseNumber(caseInfo.getCaseNumber())
                        .judgeId(chosenSlot.getJudgeId())
                        .judgeName(chosenSlot.getJudgeName())
                        .courtroomId(chosenSlot.getCourtroomId())
                        .courtroomName(chosenSlot.getCourtroomName())
                        .proposedTime(chosenSlot.getStartTime())
                        .durationMinutes(duration)
                        .casePriorityScore(caseInfo.getPriorityScore())
                        .decision(decision)
                        .build();

                assignments.add(assignment);
            } else {
                unschedulable.add(new SchedulingResult.UnschedulableCase(
                        caseInfo.getCaseId(),
                        caseInfo.getCaseNumber(),
                        "FCFS (" + variant + "): No available slot satisfying hard constraints in current horizon."
                ));
            }
        }

        return SchedulingResult.builder()
                .assignments(assignments)
                .unschedulableCases(unschedulable)
                .hearingsPerJudge(judgeLoad)
                .totalCasesInput(input.getCases().size())
                .totalAssigned(assignments.size())
                .totalUnschedulable(unschedulable.size())
                .build();
    }

    private record ChosenSlotResult(CandidateSlot slot, int nextRoundRobinIndex) {}

    private ChosenSlotResult findEarliestValidSlot(
            SchedulingInput.CaseInfo caseInfo,
            SchedulingInput input,
            List<LocalDateTime> timeSlots,
            HardConstraintChecker checker,
            int duration,
            FcfsVariant variant,
            int currentRoundRobinIdx,
            Map<UUID, Integer> judgeLoad
    ) {
        List<SchedulingInput.JudgeInfo> judges = input.getJudges();
        int numJudges = judges.size();

        // Evaluate chronological slots
        for (LocalDateTime slot : timeSlots) {
            // Find all courtroom and judge combinations valid at this slot
            List<CandidateSlot> validPairsAtSlot = new ArrayList<>();

            for (SchedulingInput.CourtroomInfo cr : input.getCourtrooms()) {
                if (!checker.isCourtroomFree(cr.getCourtroomId(), slot, duration)) {
                    continue;
                }
                if (!checker.isWithinCourtroomAvailability(cr.getCourtroomId(), slot, duration, input.getCourtrooms())) {
                    continue;
                }

                for (int jIdx = 0; jIdx < numJudges; jIdx++) {
                    SchedulingInput.JudgeInfo judge = judges.get(jIdx);
                    if (!checker.isJudgeFree(judge.getJudgeId(), slot, duration)) {
                        continue;
                    }
                    if (!checker.isWithinJudgeAvailability(judge.getJudgeId(), slot, duration, input.getJudges())) {
                        continue;
                    }
                    if (!checker.isLinkedCaseSequencingValid(caseInfo, slot)) {
                        continue;
                    }

                    validPairsAtSlot.add(CandidateSlot.builder()
                            .judgeId(judge.getJudgeId())
                            .judgeName(judge.getJudgeName())
                            .courtroomId(cr.getCourtroomId())
                            .courtroomName(cr.getCourtroomName())
                            .startTime(slot)
                            .durationMinutes(duration)
                            .build());
                }
            }

            if (!validPairsAtSlot.isEmpty()) {
                // Earliest feasible slot reached! Choose judge based on variant
                switch (variant) {
                    case NAIVE: {
                        // Always pick the first judge according to static input order
                        CandidateSlot chosen = validPairsAtSlot.get(0);
                        return new ChosenSlotResult(chosen, currentRoundRobinIdx);
                    }
                    case TIERED:
                    case ROUND_ROBIN: {
                        // Cycle judges starting from currentRoundRobinIdx
                        for (int offset = 0; offset < numJudges; offset++) {
                            int targetJudgeIdx = (currentRoundRobinIdx + offset) % numJudges;
                            UUID targetJudgeId = judges.get(targetJudgeIdx).getJudgeId();
                            for (CandidateSlot candidate : validPairsAtSlot) {
                                if (candidate.getJudgeId().equals(targetJudgeId)) {
                                    int nextIdx = (targetJudgeIdx + 1) % numJudges;
                                    return new ChosenSlotResult(candidate, nextIdx);
                                }
                            }
                        }
                        // Fallback
                        return new ChosenSlotResult(validPairsAtSlot.get(0), currentRoundRobinIdx);
                    }
                    case LEAST_LOADED: {
                        // Pick candidate with minimum current judgeLoad
                        CandidateSlot best = null;
                        int minLoad = Integer.MAX_VALUE;
                        for (CandidateSlot candidate : validPairsAtSlot) {
                            int load = judgeLoad.getOrDefault(candidate.getJudgeId(), 0);
                            if (load < minLoad) {
                                minLoad = load;
                                best = candidate;
                            }
                        }
                        return new ChosenSlotResult(best != null ? best : validPairsAtSlot.get(0), currentRoundRobinIdx);
                    }
                }
            }
        }
        return null;
    }

    private boolean isStatutoryType(com.judicialflow.common.enums.CaseType type) {
        if (type == null) return false;
        return type == com.judicialflow.common.enums.CaseType.BAIL
                || type == com.judicialflow.common.enums.CaseType.POCSO
                || type == com.judicialflow.common.enums.CaseType.MATRIMONIAL;
    }

    private List<LocalDateTime> generateTimeSlots(
            LocalDate horizonStart,
            int horizonDays,
            int defaultDurationMinutes,
            List<SchedulingInput.CourtroomInfo> courtrooms
    ) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDate current = horizonStart;
        int daysAdded = 0;
        int incrementMinutes = 60; // 60 min slots

        while (daysAdded < horizonDays) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                DayOfWeek dow = current.getDayOfWeek();
                List<SchedulingInput.AvailabilityWindow> dayWindows = courtrooms != null ? courtrooms.stream()
                        .filter(c -> c.getAvailabilityWindows() != null)
                        .flatMap(c -> c.getAvailabilityWindows().stream())
                        .filter(w -> w.getDayOfWeek() == dow)
                        .distinct()
                        .toList() : List.of();

                if (!dayWindows.isEmpty()) {
                    for (SchedulingInput.AvailabilityWindow w : dayWindows) {
                        LocalTime t = w.getStartTime();
                        LocalTime end = w.getEndTime();
                        while (!t.isAfter(end.minusMinutes(defaultDurationMinutes))) {
                            LocalDateTime slotDt = LocalDateTime.of(current, t);
                            if (!slots.contains(slotDt)) {
                                slots.add(slotDt);
                            }
                            t = t.plusMinutes(incrementMinutes);
                        }
                    }
                } else {
                    LocalTime t = LocalTime.of(9, 0);
                    LocalTime end = LocalTime.of(17, 0);
                    while (!t.isAfter(end.minusMinutes(defaultDurationMinutes))) {
                        slots.add(LocalDateTime.of(current, t));
                        t = t.plusMinutes(incrementMinutes);
                    }
                }
                daysAdded++;
            }
            current = current.plusDays(1);
        }
        slots.sort(Comparator.naturalOrder());
        return slots;
    }
}
