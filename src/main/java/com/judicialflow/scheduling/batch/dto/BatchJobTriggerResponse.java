package com.judicialflow.scheduling.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobTriggerResponse {
    private Long jobExecutionId;
    private String jobName;
    private String status;
    private String exitCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String runId;
    private Integer recomputedCasesCount;
    private Integer assignedCount;
    private ScheduleDiffSummary diffSummary;
}
