package com.judicialflow.scheduling;

import com.judicialflow.scheduling.engine.CandidateSlot;
import com.judicialflow.scheduling.engine.HardConstraintChecker;
import com.judicialflow.scheduling.engine.SchedulingInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HardConstraintChecker} in isolation.
 * No Spring context, no Testcontainers — tests pure constraint logic.
 */
class HardConstraintCheckerTest {

    private HardConstraintChecker checker;

    // 2026-09-14 is a Monday
    private final LocalDate monday = LocalDate.of(2026, 9, 14);
    private final UUID judgeA = UUID.randomUUID();
    private final UUID judgeB = UUID.randomUUID();
    private final UUID courtroomA = UUID.randomUUID();
    private final UUID courtroomB = UUID.randomUUID();

    private List<SchedulingInput.JudgeInfo> judges;
    private List<SchedulingInput.CourtroomInfo> courtrooms;

    @BeforeEach
    void setUp() {
        checker = new HardConstraintChecker();

        judges = List.of(
                SchedulingInput.JudgeInfo.builder()
                        .judgeId(judgeA).judgeName("Judge A")
                        .availabilityWindows(List.of(
                                SchedulingInput.AvailabilityWindow.builder()
                                        .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build()))
                        .build(),
                SchedulingInput.JudgeInfo.builder()
                        .judgeId(judgeB).judgeName("Judge B")
                        .availabilityWindows(List.of(
                                SchedulingInput.AvailabilityWindow.builder()
                                        .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).build()))
                        .build()
        );

        courtrooms = List.of(
                SchedulingInput.CourtroomInfo.builder()
                        .courtroomId(courtroomA).courtroomName("Room A")
                        .availabilityWindows(List.of(
                                SchedulingInput.AvailabilityWindow.builder()
                                        .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build()))
                        .build(),
                SchedulingInput.CourtroomInfo.builder()
                        .courtroomId(courtroomB).courtroomName("Room B")
                        .availabilityWindows(List.of(
                                SchedulingInput.AvailabilityWindow.builder()
                                        .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0)).build()))
                        .build()
        );
    }

    @Test
    void judgeDoubleBooking_detected() {
        // Book Judge A at 10:00–11:00
        checker.recordBooking(judgeA, courtroomA, UUID.randomUUID(),
                LocalDateTime.of(monday, LocalTime.of(10, 0)), 60);

        // Check 10:30 — overlaps
        assertFalse(checker.isJudgeFree(judgeA, LocalDateTime.of(monday, LocalTime.of(10, 30)), 60));
    }

    @Test
    void courtroomDoubleBooking_detected() {
        checker.recordBooking(judgeA, courtroomA, UUID.randomUUID(),
                LocalDateTime.of(monday, LocalTime.of(10, 0)), 60);

        assertFalse(checker.isCourtroomFree(courtroomA, LocalDateTime.of(monday, LocalTime.of(10, 30)), 60));
    }

    @Test
    void noOverlap_judgeIsFree() {
        checker.recordBooking(judgeA, courtroomA, UUID.randomUUID(),
                LocalDateTime.of(monday, LocalTime.of(10, 0)), 60);

        // 11:00 starts exactly when the previous ends — no overlap
        assertTrue(checker.isJudgeFree(judgeA, LocalDateTime.of(monday, LocalTime.of(11, 0)), 60));
    }

    @Test
    void judgeAvailability_withinWindow() {
        assertTrue(checker.isWithinJudgeAvailability(
                judgeA, LocalDateTime.of(monday, LocalTime.of(10, 0)), 60, judges));
    }

    @Test
    void judgeAvailability_outsideWindow() {
        // Judge B available 09:00–12:00 on Monday, check 13:00 for 60 min
        assertFalse(checker.isWithinJudgeAvailability(
                judgeB, LocalDateTime.of(monday, LocalTime.of(13, 0)), 60, judges));
    }

    @Test
    void courtroomAvailability_outsideWindow() {
        // Room B available 09:00–12:00 on Monday, check 13:00
        assertFalse(checker.isWithinCourtroomAvailability(
                courtroomB, LocalDateTime.of(monday, LocalTime.of(13, 0)), 60, courtrooms));
    }

    @Test
    void linkedCaseSequencing_valid() {
        UUID parentCaseId = UUID.randomUUID();
        UUID childCaseId = UUID.randomUUID();

        // Parent case scheduled at 10:00
        checker.recordBooking(judgeA, courtroomA, parentCaseId,
                LocalDateTime.of(monday, LocalTime.of(10, 0)), 60);

        // Child links to parent, proposed at 11:00 — valid (after parent)
        SchedulingInput.CaseInfo childCase = SchedulingInput.CaseInfo.builder()
                .caseId(childCaseId).caseNumber("CHILD-001")
                .linkedCaseId(parentCaseId).priorityScore(java.math.BigDecimal.TEN).build();

        assertTrue(checker.isLinkedCaseSequencingValid(childCase,
                LocalDateTime.of(monday, LocalTime.of(11, 0))));
    }

    @Test
    void linkedCaseSequencing_invalid() {
        UUID parentCaseId = UUID.randomUUID();
        UUID childCaseId = UUID.randomUUID();

        // Parent scheduled at 14:00
        checker.recordBooking(judgeA, courtroomA, parentCaseId,
                LocalDateTime.of(monday, LocalTime.of(14, 0)), 60);

        // Child proposed at 10:00 — invalid (before parent)
        SchedulingInput.CaseInfo childCase = SchedulingInput.CaseInfo.builder()
                .caseId(childCaseId).caseNumber("CHILD-001")
                .linkedCaseId(parentCaseId).priorityScore(java.math.BigDecimal.TEN).build();

        assertFalse(checker.isLinkedCaseSequencingValid(childCase,
                LocalDateTime.of(monday, LocalTime.of(10, 0))));
    }

    @Test
    void linkedCaseSequencing_prerequisiteNotScheduled() {
        UUID parentCaseId = UUID.randomUUID();
        // Parent is NOT scheduled at all
        SchedulingInput.CaseInfo childCase = SchedulingInput.CaseInfo.builder()
                .caseId(UUID.randomUUID()).caseNumber("CHILD-001")
                .linkedCaseId(parentCaseId).priorityScore(java.math.BigDecimal.TEN).build();

        assertFalse(checker.isLinkedCaseSequencingValid(childCase,
                LocalDateTime.of(monday, LocalTime.of(10, 0))));
    }

    @Test
    void checkAll_returnsAllViolations_notShortCircuited() {
        // Book Judge A at 10:00–11:00 AND Courtroom A at 10:00–11:00
        checker.recordBooking(judgeA, courtroomA, UUID.randomUUID(),
                LocalDateTime.of(monday, LocalTime.of(10, 0)), 60);

        // Candidate also at 10:00 — violates BOTH judge and courtroom booking
        CandidateSlot candidate = CandidateSlot.builder()
                .judgeId(judgeA).judgeName("Judge A")
                .courtroomId(courtroomA).courtroomName("Room A")
                .startTime(LocalDateTime.of(monday, LocalTime.of(10, 0)))
                .durationMinutes(60).build();

        SchedulingInput.CaseInfo caseInfo = SchedulingInput.CaseInfo.builder()
                .caseId(UUID.randomUUID()).caseNumber("TEST-001")
                .priorityScore(java.math.BigDecimal.TEN).build();

        List<String> violations = checker.checkAll(candidate, 60, caseInfo, judges, courtrooms);

        // Should have at least 2 violations (judge booked + courtroom booked)
        assertTrue(violations.size() >= 2,
                "Expected multiple violations but got: " + violations);
    }
}
