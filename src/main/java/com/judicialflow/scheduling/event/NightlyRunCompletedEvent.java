package com.judicialflow.scheduling.event;

import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

/**
 * Event published when a nightly scheduling run finishes computing diffs and reprioritizations.
 */
@Getter
public class NightlyRunCompletedEvent extends ApplicationEvent {

    private final UUID runId;
    private final List<ScheduleDiffSummary.ReprioritizationDiff> reprioritizations;

    public NightlyRunCompletedEvent(Object source, UUID runId, List<ScheduleDiffSummary.ReprioritizationDiff> reprioritizations) {
        super(source);
        this.runId = runId;
        this.reprioritizations = reprioritizations != null ? reprioritizations : List.of();
    }
}
