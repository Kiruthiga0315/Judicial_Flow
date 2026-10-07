package com.judicialflow.scheduling.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a Hearing is manually rescheduled / reassigned.
 */
@Getter
public class HearingRescheduledEvent extends ApplicationEvent {

    private final UUID hearingId;
    private final UUID caseId;
    private final UUID judgeId;
    private final UUID courtroomId;
    private final LocalDateTime previousScheduledTime;
    private final LocalDateTime newScheduledTime;
    private final int durationMinutes;
    private final UUID previousJudgeId;
    private final UUID previousCourtroomId;
    private final String reason;
    private final String litigantMessage;

    public HearingRescheduledEvent(
            Object source,
            UUID hearingId,
            UUID caseId,
            UUID judgeId,
            UUID courtroomId,
            LocalDateTime previousScheduledTime,
            LocalDateTime newScheduledTime,
            int durationMinutes,
            UUID previousJudgeId,
            UUID previousCourtroomId,
            String reason) {
        this(source, hearingId, caseId, judgeId, courtroomId, previousScheduledTime, newScheduledTime, durationMinutes, previousJudgeId, previousCourtroomId, reason, null);
    }

    public HearingRescheduledEvent(
            Object source,
            UUID hearingId,
            UUID caseId,
            UUID judgeId,
            UUID courtroomId,
            LocalDateTime previousScheduledTime,
            LocalDateTime newScheduledTime,
            int durationMinutes,
            UUID previousJudgeId,
            UUID previousCourtroomId,
            String reason,
            String litigantMessage) {
        super(source);
        this.hearingId = hearingId;
        this.caseId = caseId;
        this.judgeId = judgeId;
        this.courtroomId = courtroomId;
        this.previousScheduledTime = previousScheduledTime;
        this.newScheduledTime = newScheduledTime;
        this.durationMinutes = durationMinutes;
        this.previousJudgeId = previousJudgeId;
        this.previousCourtroomId = previousCourtroomId;
        this.reason = reason;
        this.litigantMessage = litigantMessage;
    }
}
