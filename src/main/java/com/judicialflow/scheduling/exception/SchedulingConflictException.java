package com.judicialflow.scheduling.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SchedulingConflictException extends RuntimeException {
    private final UUID runningRunId;

    public SchedulingConflictException(UUID runningRunId) {
        super("A scheduling run is already in progress with ID: " + runningRunId);
        this.runningRunId = runningRunId;
    }
}
