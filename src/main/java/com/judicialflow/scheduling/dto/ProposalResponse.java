package com.judicialflow.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST response for a single proposed case→slot assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalResponse {

    private UUID proposalId;
    private UUID caseId;
    private String caseNumber;
    private BigDecimal casePriorityScore;

    // Assigned slot
    private UUID judgeId;
    private String judgeName;
    private UUID courtroomId;
    private String courtroomName;
    private LocalDateTime proposedTime;
    private int durationMinutes;

    private String status;

    /** The decision log explaining why this slot was chosen. */
    private DecisionLogResponse decisionLog;
}
