package com.judicialflow.scheduling.engine;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Validates hard constraints for the scheduling engine.
 *
 * <h2>Hard constraints enforced</h2>
 * <ol>
 *   <li><b>No judge double-booking</b>: A judge cannot have overlapping hearings.</li>
 *   <li><b>No courtroom double-booking</b>: A courtroom cannot have overlapping hearings.</li>
 *   <li><b>Judge availability</b>: The hearing must fall within a declared JudgeAvailabilityWindow.</li>
 *   <li><b>Courtroom availability</b>: The hearing must fall within a declared CourtroomAvailabilityWindow.</li>
 *   <li><b>Linked case sequencing</b>: If case C links to case P, then P must be heard before C.</li>
 * </ol>
 *
 * <p>This class is stateless and performs no I/O. All data is passed in via method parameters.
 * It maintains an internal booking ledger that tracks both existing hearings AND newly proposed
 * assignments, so that intra-run double-booking is detected.
 */
public class HardConstraintChecker {
    private static final Comparator<TimeSlot> TIME_SLOT_COMPARATOR =
            Comparator.comparing(TimeSlot::start).thenComparing(TimeSlot::end);

    // Internal booking ledgers: track all committed + proposed bookings
    private final Map<UUID, TreeSet<TimeSlot>> judgeBookings = new HashMap<>();
    private final Map<UUID, TreeSet<TimeSlot>> courtroomBookings = new HashMap<>();
    // Track hearing times for linked-case sequencing
    private final Map<UUID, LocalDateTime> caseHearingTimes = new HashMap<>();

    // Inner record for time intervals
    public record TimeSlot(LocalDateTime start, LocalDateTime end) {
        public boolean overlaps(TimeSlot other) {
            return this.start.isBefore(other.end) && other.start.isBefore(this.end);
        }
    }

    /**
     * Initialize the checker with existing committed hearings.
     */
    public void loadExistingHearings(List<SchedulingInput.ExistingHearing> existingHearings) {
        // For each existing hearing, add to the appropriate booking ledger
        for (var h : existingHearings) {
            TimeSlot slot = new TimeSlot(h.getStartTime(), h.getStartTime().plusMinutes(h.getDurationMinutes()));
            judgeBookings.computeIfAbsent(h.getJudgeId(), k -> new TreeSet<>(TIME_SLOT_COMPARATOR)).add(slot);
            courtroomBookings.computeIfAbsent(h.getCourtroomId(), k -> new TreeSet<>(TIME_SLOT_COMPARATOR)).add(slot);
            caseHearingTimes.put(h.getCaseId(), h.getStartTime());
        }
    }

    /**
     * Check ALL hard constraints for a candidate slot. Returns a list of violation
     * descriptions. If the list is empty, all constraints are satisfied.
     */
    public List<String> checkAll(CandidateSlot candidate, int durationMinutes,
                                  SchedulingInput.CaseInfo caseInfo,
                                  List<SchedulingInput.JudgeInfo> judges,
                                  List<SchedulingInput.CourtroomInfo> courtrooms) {
        List<String> violations = new ArrayList<>();
        
        if (!isJudgeFree(candidate.getJudgeId(), candidate.getStartTime(), durationMinutes)) {
            violations.add("Judge is already booked at this time.");
        }
        if (!isCourtroomFree(candidate.getCourtroomId(), candidate.getStartTime(), durationMinutes)) {
            violations.add("Courtroom is already booked at this time.");
        }
        if (!isWithinJudgeAvailability(candidate.getJudgeId(), candidate.getStartTime(), durationMinutes, judges)) {
            violations.add("Time is outside judge's availability windows.");
        }
        if (!isWithinCourtroomAvailability(candidate.getCourtroomId(), candidate.getStartTime(), durationMinutes, courtrooms)) {
            violations.add("Time is outside courtroom's availability windows.");
        }
        if (!isLinkedCaseSequencingValid(caseInfo, candidate.getStartTime())) {
            violations.add("Linked prerequisite case is not scheduled before this time.");
        }
        
        return violations;
    }

    public boolean isJudgeFree(UUID judgeId, LocalDateTime start, int durationMinutes) {
        TreeSet<TimeSlot> bookings = judgeBookings.get(judgeId);
        if (bookings == null || bookings.isEmpty()) return true;
        TimeSlot proposed = new TimeSlot(start, start.plusMinutes(durationMinutes));
        TimeSlot floor = bookings.floor(proposed);
        if (floor != null && floor.end.isAfter(proposed.start)) return false;
        TimeSlot ceil = bookings.ceiling(proposed);
        if (ceil != null && ceil.start.isBefore(proposed.end)) return false;
        return true;
    }

    public boolean isCourtroomFree(UUID courtroomId, LocalDateTime start, int durationMinutes) {
        TreeSet<TimeSlot> bookings = courtroomBookings.get(courtroomId);
        if (bookings == null || bookings.isEmpty()) return true;
        TimeSlot proposed = new TimeSlot(start, start.plusMinutes(durationMinutes));
        TimeSlot floor = bookings.floor(proposed);
        if (floor != null && floor.end.isAfter(proposed.start)) return false;
        TimeSlot ceil = bookings.ceiling(proposed);
        if (ceil != null && ceil.start.isBefore(proposed.end)) return false;
        return true;
    }

    public boolean isWithinJudgeAvailability(UUID judgeId, LocalDateTime start, int durationMinutes, List<SchedulingInput.JudgeInfo> judges) {
        SchedulingInput.JudgeInfo judge = judges.stream()
            .filter(j -> j.getJudgeId().equals(judgeId))
            .findFirst()
            .orElse(null);
            
        if (judge == null || judge.getAvailabilityWindows() == null) {
            return false;
        }

        DayOfWeek day = start.getDayOfWeek();
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = startTime.plusMinutes(durationMinutes);
        
        // Handle next-day overlap
        if (endTime.isBefore(startTime)) {
            return false;
        }

        for (SchedulingInput.AvailabilityWindow window : judge.getAvailabilityWindows()) {
            if (window.getDayOfWeek() == day) {
                if (!startTime.isBefore(window.getStartTime()) && !endTime.isAfter(window.getEndTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isWithinCourtroomAvailability(UUID courtroomId, LocalDateTime start, int durationMinutes, List<SchedulingInput.CourtroomInfo> courtrooms) {
        SchedulingInput.CourtroomInfo cr = courtrooms.stream()
            .filter(c -> c.getCourtroomId().equals(courtroomId))
            .findFirst()
            .orElse(null);
            
        if (cr == null || cr.getAvailabilityWindows() == null) {
            return false;
        }

        DayOfWeek day = start.getDayOfWeek();
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = startTime.plusMinutes(durationMinutes);

        if (endTime.isBefore(startTime)) {
            return false;
        }

        for (SchedulingInput.AvailabilityWindow window : cr.getAvailabilityWindows()) {
            if (window.getDayOfWeek() == day) {
                if (!startTime.isBefore(window.getStartTime()) && !endTime.isAfter(window.getEndTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isLinkedCaseSequencingValid(SchedulingInput.CaseInfo caseInfo, LocalDateTime proposedStart) {
        if (caseInfo.getLinkedCaseId() == null) {
            return true;
        }
        LocalDateTime linkedCaseTime = caseHearingTimes.get(caseInfo.getLinkedCaseId());
        if (linkedCaseTime == null) {
            return false; // Linked case is not scheduled at all yet
        }
        return linkedCaseTime.isBefore(proposedStart);
    }

    /**
     * Record a new booking after a successful assignment.
     * MUST be called after each assignment to keep the ledger current.
     */
    public void recordBooking(UUID judgeId, UUID courtroomId, UUID caseId, LocalDateTime start, int durationMinutes) {
        TimeSlot slot = new TimeSlot(start, start.plusMinutes(durationMinutes));
        judgeBookings.computeIfAbsent(judgeId, k -> new TreeSet<>(TIME_SLOT_COMPARATOR)).add(slot);
        courtroomBookings.computeIfAbsent(courtroomId, k -> new TreeSet<>(TIME_SLOT_COMPARATOR)).add(slot);
        caseHearingTimes.put(caseId, start);
    }

    public void removeBooking(UUID judgeId, UUID courtroomId, UUID caseId, LocalDateTime start, int durationMinutes) {
        TimeSlot slot = new TimeSlot(start, start.plusMinutes(durationMinutes));
        TreeSet<TimeSlot> jb = judgeBookings.get(judgeId);
        if (jb != null) jb.remove(slot);
        TreeSet<TimeSlot> cb = courtroomBookings.get(courtroomId);
        if (cb != null) cb.remove(slot);
        caseHearingTimes.remove(caseId);
    }

    /** Get the recorded hearing time for a case (for linked-case lookups). */
    public Optional<LocalDateTime> getHearingTimeForCase(UUID caseId) {
        return Optional.ofNullable(caseHearingTimes.get(caseId));
    }
}
