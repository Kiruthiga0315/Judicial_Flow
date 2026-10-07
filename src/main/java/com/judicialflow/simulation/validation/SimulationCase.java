package com.judicialflow.simulation.validation;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory case model for simulation runs.
 * Holds pre-drawn hearing outcome sequence for common random numbers (CRN).
 */
@Data
@Builder
public class SimulationCase {
    private UUID caseId;
    private String caseNumber;
    private CaseType caseType;
    private LocalDate filingDate;
    private LocalDate statutoryDeadline;
    private UUID linkedCaseId;
    private int estimatedDurationMinutes;
    private int caseIndex; // generation index

    private boolean initialBacklog;
    private LocalDate effectiveFilingDate;

    // Pre-drawn CRN outcomes: true = ADJOURN, false = DISPOSE
    // Indexed by hearing count (0 for 1st hearing, 1 for 2nd hearing, etc.)
    @Builder.Default
    private List<Boolean> preDrawnAdjournmentOutcomes = new ArrayList<>();

    // Runtime state (per arm)
    private CaseStatus currentStatus;
    private int priorAdjournments;
    private LocalDate eligibleAfterDate;
    private LocalDate firstHearingDate;
    private LocalDate disposalDate;
    @Builder.Default
    private List<LocalDateTime> hearingHistory = new ArrayList<>();

    /**
     * Deep copy for arm isolation (FCFS vs Engine).
     */
    public SimulationCase copy() {
        return SimulationCase.builder()
                .caseId(this.caseId)
                .caseNumber(this.caseNumber)
                .caseType(this.caseType)
                .filingDate(this.filingDate)
                .statutoryDeadline(this.statutoryDeadline)
                .linkedCaseId(this.linkedCaseId)
                .estimatedDurationMinutes(this.estimatedDurationMinutes)
                .caseIndex(this.caseIndex)
                .initialBacklog(this.initialBacklog)
                .effectiveFilingDate(this.effectiveFilingDate)
                .preDrawnAdjournmentOutcomes(new ArrayList<>(this.preDrawnAdjournmentOutcomes))
                .currentStatus(this.currentStatus)
                .priorAdjournments(this.priorAdjournments)
                .eligibleAfterDate(this.eligibleAfterDate)
                .firstHearingDate(this.firstHearingDate)
                .disposalDate(this.disposalDate)
                .hearingHistory(new ArrayList<>(this.hearingHistory))
                .build();
    }

    /**
     * Converts to domain Case model for scoring.
     */
    public Case toDomainCase() {
        return Case.builder()
                .id(this.caseId)
                .caseNumber(this.caseNumber)
                .caseType(this.caseType)
                .filingDate(this.filingDate)
                .statutoryDeadline(this.statutoryDeadline)
                .currentStatus(this.currentStatus)
                .priorAdjournments(this.priorAdjournments)
                .build();
    }
}
