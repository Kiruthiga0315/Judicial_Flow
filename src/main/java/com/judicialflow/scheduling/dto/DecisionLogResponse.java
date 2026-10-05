package com.judicialflow.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST response for the explainability log of a single scheduling decision.
 *
 * <p>This is the critical "explainable AI" payload: for every assignment the
 * engine makes, we record <em>what</em> was chosen, <em>what</em> the runner-up
 * was, and <em>why</em> the runner-up was passed over.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionLogResponse {

    private String caseNumber;

    // Chosen assignment
    private String chosenJudgeName;
    private String chosenCourtroomName;
    private LocalDateTime chosenTime;
    private BigDecimal chosenSoftScore;

    // Runner-up (may be null if only one candidate existed)
    private String runnerUpJudgeName;
    private String runnerUpCourtroomName;
    private LocalDateTime runnerUpTime;
    private BigDecimal runnerUpSoftScore;
    private String runnerUpRejectionReason;

    /** Hard constraints verified as satisfied for this assignment. */
    private java.util.List<String> constraintsSatisfied;

    /** Full human-readable explanation of the decision. */
    private String explanation;
}
