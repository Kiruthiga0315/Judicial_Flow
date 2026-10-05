package com.judicialflow.scheduling;

import com.judicialflow.scheduling.engine.SchedulingEngine;
import com.judicialflow.scheduling.engine.SchedulingInput;
import com.judicialflow.scheduling.engine.SchedulingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchedulingEngine}. No Spring context needed —
 * the engine is a pure-logic class.
 */
class SchedulingEngineTest {

    private SchedulingEngine engine;
    // 2026-09-14 is a Monday
    private final LocalDate mondayDate = LocalDate.of(2026, 9, 14);

    @BeforeEach
    void setUp() {
        engine = new SchedulingEngine();
    }

    private SchedulingInput.AvailabilityWindow mondayWindow() {
        return SchedulingInput.AvailabilityWindow.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0)).build();
    }

    private List<SchedulingInput.AvailabilityWindow> fullWeek() {
        List<SchedulingInput.AvailabilityWindow> windows = new ArrayList<>();
        for (DayOfWeek d : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            windows.add(SchedulingInput.AvailabilityWindow.builder()
                    .dayOfWeek(d).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build());
        }
        return windows;
    }

    // =========================================================================
    // Basic Tests
    // =========================================================================

    @Test
    void noCases_emptyResult() {
        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>())
                .judges(new ArrayList<>())
                .courtrooms(new ArrayList<>())
                .horizonStart(mondayDate)
                .horizonDays(1)
                .build();

        SchedulingResult result = engine.solve(input);

        assertTrue(result.getAssignments().isEmpty());
        assertTrue(result.getUnschedulableCases().isEmpty());
    }

    @Test
    void singleCase_singleJudge_singleCourtroom_assigned() {
        UUID judgeId = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(caseId).caseNumber("CASE-001")
                                .priorityScore(BigDecimal.valueOf(50))
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judgeId).judgeName("Judge A")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .horizonStart(mondayDate)
                .horizonDays(1)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        assertEquals(1, result.getTotalAssigned());
        assertEquals(caseId, result.getAssignments().get(0).getCaseId());
        assertEquals(judgeId, result.getAssignments().get(0).getJudgeId());
    }

    // =========================================================================
    // Hard Constraint: Adversarial Double-Booking Tests
    // =========================================================================

    @Test
    void hardConstraint_neverDoubleBooks_judge_adversarial() {
        UUID judgeId = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();

        // 20 cases competing for 1 judge on 1 day (Monday 09-17 = 8 hours = 8 slots of 60 min)
        List<SchedulingInput.CaseInfo> cases = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            cases.add(SchedulingInput.CaseInfo.builder()
                    .caseId(UUID.randomUUID()).caseNumber("CASE-" + i)
                    .priorityScore(BigDecimal.valueOf(50 - i))
                    .estimatedDurationMinutes(60).build());
        }

        SchedulingInput input = SchedulingInput.builder()
                .cases(cases)
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judgeId).judgeName("Only Judge")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Only Room")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .horizonStart(mondayDate)
                .horizonDays(1)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        // Some must be unschedulable since we can't fit 20 in 8 slots
        assertTrue(result.getTotalUnschedulable() > 0,
                "With 20 cases and only ~8 slots, some must be unschedulable");

        // Verify NO double-booking in assigned cases
        assertNoDoubleBookings(result);
    }

    @Test
    void hardConstraint_neverDoubleBooks_courtroom_adversarial() {
        UUID courtroomId = UUID.randomUUID();

        // 3 judges but only 1 courtroom
        List<SchedulingInput.JudgeInfo> judges = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            judges.add(SchedulingInput.JudgeInfo.builder()
                    .judgeId(UUID.randomUUID()).judgeName("Judge " + i)
                    .availabilityWindows(List.of(mondayWindow())).build());
        }

        List<SchedulingInput.CaseInfo> cases = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            cases.add(SchedulingInput.CaseInfo.builder()
                    .caseId(UUID.randomUUID()).caseNumber("CASE-" + i)
                    .priorityScore(BigDecimal.valueOf(50 - i))
                    .estimatedDurationMinutes(60).build());
        }

        SchedulingInput input = SchedulingInput.builder()
                .cases(cases)
                .judges(judges)
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Only Room")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .horizonStart(mondayDate)
                .horizonDays(1)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        // Verify no courtroom double-booking
        assertNoDoubleBookings(result);
    }

    // =========================================================================
    // Hard Constraint: Availability Enforcement
    // =========================================================================

    @Test
    void hardConstraint_availabilityEnforced() {
        UUID judgeId = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();

        // Judge available only 09:00–12:00
        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(UUID.randomUUID()).caseNumber("CASE-001")
                                .priorityScore(BigDecimal.TEN)
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judgeId).judgeName("Morning Judge")
                                .availabilityWindows(List.of(
                                        SchedulingInput.AvailabilityWindow.builder()
                                                .dayOfWeek(DayOfWeek.MONDAY)
                                                .startTime(LocalTime.of(9, 0))
                                                .endTime(LocalTime.of(12, 0)).build())).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .horizonStart(mondayDate)
                .horizonDays(1)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        assertEquals(1, result.getTotalAssigned());
        LocalDateTime assignedTime = result.getAssignments().get(0).getProposedTime();
        assertTrue(assignedTime.toLocalTime().isBefore(LocalTime.of(12, 0)),
                "Hearing must be within judge availability (before 12:00) but was: " + assignedTime);
    }

    @Test
    void hardConstraint_noAvailability_caseUnschedulable() {
        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(UUID.randomUUID()).caseNumber("CASE-001")
                                .priorityScore(BigDecimal.TEN)
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(UUID.randomUUID()).judgeName("No-Availability Judge")
                                .availabilityWindows(List.of()).build())))   // empty!
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(UUID.randomUUID()).courtroomName("Room 1")
                                .availabilityWindows(List.of(mondayWindow())).build())))
                .horizonStart(mondayDate)
                .horizonDays(1)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        assertEquals(0, result.getTotalAssigned());
        assertEquals(1, result.getTotalUnschedulable());
    }

    // =========================================================================
    // Linked Case Sequencing
    // =========================================================================

    @Test
    void linkedCase_sequencing() {
        UUID caseAId = UUID.randomUUID();
        UUID caseBId = UUID.randomUUID();
        UUID judgeId = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();

        // Case B links to Case A. B has higher priority (80 vs 50),
        // but A must be scheduled before B.
        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(caseBId).caseNumber("CASE-B")
                                .priorityScore(BigDecimal.valueOf(80))
                                .linkedCaseId(caseAId)
                                .estimatedDurationMinutes(60).build(),
                        SchedulingInput.CaseInfo.builder()
                                .caseId(caseAId).caseNumber("CASE-A")
                                .priorityScore(BigDecimal.valueOf(50))
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judgeId).judgeName("Judge A")
                                .availabilityWindows(fullWeek()).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(fullWeek()).build())))
                .horizonStart(mondayDate)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        assertEquals(2, result.getTotalAssigned(), "Both cases should be assigned");

        LocalDateTime timeA = result.getAssignments().stream()
                .filter(a -> a.getCaseId().equals(caseAId))
                .findFirst().orElseThrow().getProposedTime();
        LocalDateTime timeB = result.getAssignments().stream()
                .filter(a -> a.getCaseId().equals(caseBId))
                .findFirst().orElseThrow().getProposedTime();

        assertTrue(timeA.isBefore(timeB),
                "Case A must be scheduled before Case B (linked case sequencing). A=" + timeA + ", B=" + timeB);
    }

    // =========================================================================
    // Soft Constraint Tests
    // =========================================================================

    @Test
    void softConstraint_priorityOrdering() {
        UUID judgeId = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();
        UUID highPriorityCaseId = UUID.randomUUID();
        UUID lowPriorityCaseId = UUID.randomUUID();

        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(lowPriorityCaseId).caseNumber("LOW")
                                .priorityScore(BigDecimal.valueOf(20))
                                .estimatedDurationMinutes(60).build(),
                        SchedulingInput.CaseInfo.builder()
                                .caseId(highPriorityCaseId).caseNumber("HIGH")
                                .priorityScore(BigDecimal.valueOf(90))
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judgeId).judgeName("Judge A")
                                .availabilityWindows(fullWeek()).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(fullWeek()).build())))
                .horizonStart(mondayDate)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);
        assertEquals(2, result.getTotalAssigned());

        LocalDateTime highTime = result.getAssignments().stream()
                .filter(a -> a.getCaseId().equals(highPriorityCaseId))
                .findFirst().orElseThrow().getProposedTime();
        LocalDateTime lowTime = result.getAssignments().stream()
                .filter(a -> a.getCaseId().equals(lowPriorityCaseId))
                .findFirst().orElseThrow().getProposedTime();

        assertTrue(highTime.isBefore(lowTime) || highTime.isEqual(lowTime),
                "Higher priority case should get earlier or equal slot. High=" + highTime + ", Low=" + lowTime);
    }

    @Test
    void softConstraint_workloadBalance() {
        UUID judge1 = UUID.randomUUID();
        UUID judge2 = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();

        List<SchedulingInput.CaseInfo> cases = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cases.add(SchedulingInput.CaseInfo.builder()
                    .caseId(UUID.randomUUID()).caseNumber("CASE-" + i)
                    .priorityScore(BigDecimal.valueOf(50))
                    .estimatedDurationMinutes(60).build());
        }

        SchedulingInput input = SchedulingInput.builder()
                .cases(cases)
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judge1).judgeName("Judge 1")
                                .availabilityWindows(fullWeek()).build(),
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judge2).judgeName("Judge 2")
                                .availabilityWindows(fullWeek()).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(fullWeek()).build())))
                .horizonStart(mondayDate)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);
        assertEquals(4, result.getTotalAssigned());

        long judge1Count = result.getAssignments().stream()
                .filter(a -> a.getJudgeId().equals(judge1)).count();
        long judge2Count = result.getAssignments().stream()
                .filter(a -> a.getJudgeId().equals(judge2)).count();

        // With workload balance, expect roughly 2 each (allow ±1)
        assertTrue(Math.abs(judge1Count - judge2Count) <= 2,
                "Workload should be roughly balanced. Judge1=" + judge1Count + ", Judge2=" + judge2Count);
    }

    // =========================================================================
    // Explainability Tests
    // =========================================================================

    @Test
    void explainability_decisionRecordPopulated() {
        UUID judgeId = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();

        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(UUID.randomUUID()).caseNumber("CASE-001")
                                .priorityScore(BigDecimal.valueOf(50))
                                .estimatedDurationMinutes(60).build(),
                        SchedulingInput.CaseInfo.builder()
                                .caseId(UUID.randomUUID()).caseNumber("CASE-002")
                                .priorityScore(BigDecimal.valueOf(30))
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judgeId).judgeName("Judge A")
                                .availabilityWindows(fullWeek()).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(fullWeek()).build())))
                .horizonStart(mondayDate)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);

        for (SchedulingResult.ProposedAssignment a : result.getAssignments()) {
            assertNotNull(a.getDecision(), "Decision record must not be null for " + a.getCaseNumber());
            assertNotNull(a.getDecision().getExplanation(), "Explanation must not be null");
            assertNotNull(a.getDecision().getConstraintsSatisfied(), "Constraints list must not be null");
            assertFalse(a.getDecision().getConstraintsSatisfied().isEmpty(), "Constraints list must not be empty");
        }
    }

    @Test
    void explainability_runnerUpRecorded() {
        UUID judge1 = UUID.randomUUID();
        UUID judge2 = UUID.randomUUID();
        UUID courtroomId = UUID.randomUUID();

        // With 2 judges and 1 courtroom, each case has at least 2 candidate slots
        SchedulingInput input = SchedulingInput.builder()
                .cases(new ArrayList<>(List.of(
                        SchedulingInput.CaseInfo.builder()
                                .caseId(UUID.randomUUID()).caseNumber("CASE-001")
                                .priorityScore(BigDecimal.valueOf(50))
                                .estimatedDurationMinutes(60).build())))
                .judges(new ArrayList<>(List.of(
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judge1).judgeName("Judge 1")
                                .availabilityWindows(fullWeek()).build(),
                        SchedulingInput.JudgeInfo.builder()
                                .judgeId(judge2).judgeName("Judge 2")
                                .availabilityWindows(fullWeek()).build())))
                .courtrooms(new ArrayList<>(List.of(
                        SchedulingInput.CourtroomInfo.builder()
                                .courtroomId(courtroomId).courtroomName("Room 1")
                                .availabilityWindows(fullWeek()).build())))
                .horizonStart(mondayDate)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .build();

        SchedulingResult result = engine.solve(input);
        assertEquals(1, result.getTotalAssigned());

        SchedulingResult.DecisionRecord dec = result.getAssignments().get(0).getDecision();
        assertNotNull(dec.getRunnerUpJudgeName(),
                "Runner-up should be recorded when multiple candidates exist");
        assertNotNull(dec.getRunnerUpRejectionReason(),
                "Runner-up rejection reason should be populated");
    }

    // =========================================================================
    // Helper: verify no double bookings
    // =========================================================================

    private void assertNoDoubleBookings(SchedulingResult result) {
        List<SchedulingResult.ProposedAssignment> assignments = result.getAssignments();
        for (int i = 0; i < assignments.size(); i++) {
            for (int j = i + 1; j < assignments.size(); j++) {
                var a = assignments.get(i);
                var b = assignments.get(j);

                if (a.getJudgeId().equals(b.getJudgeId())) {
                    LocalDateTime aEnd = a.getProposedTime().plusMinutes(a.getDurationMinutes());
                    LocalDateTime bEnd = b.getProposedTime().plusMinutes(b.getDurationMinutes());
                    assertFalse(a.getProposedTime().isBefore(bEnd) && b.getProposedTime().isBefore(aEnd),
                            String.format("Judge double-booking! %s [%s-%s] and %s [%s-%s]",
                                    a.getCaseNumber(), a.getProposedTime(), aEnd,
                                    b.getCaseNumber(), b.getProposedTime(), bEnd));
                }

                if (a.getCourtroomId().equals(b.getCourtroomId())) {
                    LocalDateTime aEnd = a.getProposedTime().plusMinutes(a.getDurationMinutes());
                    LocalDateTime bEnd = b.getProposedTime().plusMinutes(b.getDurationMinutes());
                    assertFalse(a.getProposedTime().isBefore(bEnd) && b.getProposedTime().isBefore(aEnd),
                            String.format("Courtroom double-booking! %s [%s-%s] and %s [%s-%s]",
                                    a.getCaseNumber(), a.getProposedTime(), aEnd,
                                    b.getCaseNumber(), b.getProposedTime(), bEnd));
                }
            }
        }
    }
}
