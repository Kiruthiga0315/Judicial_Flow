package com.judicialflow.scheduling.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a scheduling proposal is approved and committed as a Hearing.
 * Phase 7 notification services can subscribe to this event.
 */
@Getter
public class HearingCommittedEvent extends ApplicationEvent {

    private final UUID hearingId;
    private final UUID caseId;
    private final UUID judgeId;
    private final UUID courtroomId;
    private final UUID proposalId;
    private final LocalDateTime scheduledTime;
    private final int durationMinutes;

    public HearingCommittedEvent(
            Object source,
            UUID hearingId,
            UUID caseId,
            UUID judgeId,
            UUID courtroomId,
            UUID proposalId,
            LocalDateTime scheduledTime,
            int durationMinutes) {
        super(source);
        this.hearingId = hearingId;
        this.caseId = caseId;
        this.judgeId = judgeId;
        this.courtroomId = courtroomId;
        this.proposalId = proposalId;
        this.scheduledTime = scheduledTime;
        this.durationMinutes = durationMinutes;
    }
}
