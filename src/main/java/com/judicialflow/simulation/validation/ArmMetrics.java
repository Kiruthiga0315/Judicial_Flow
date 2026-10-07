package com.judicialflow.simulation.validation;

import com.judicialflow.common.enums.CaseType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Metric results for a single arm (FCFS or JudicialFlow Engine) in a simulation run.
 */
@Data
@Builder
public class ArmMetrics {
    private String armName; // "FCFS" or "JUDICIALFLOW_ENGINE"

    // Primary Metric: Arrivals Cohort (Cases that arrived during the simulation, wait from filing to 1st hearing)
    private double arrivalsMeanDelay;
    private double arrivalsMedianDelay;
    private double arrivalsP90Delay;
    private double arrivalsPercentDelayedPastT; // at T=14
    private int arrivalsCensoredCount;
    private int arrivalsTotalCount;

    // Backlog Cohort (Initial backlog cases, wait from simulation start date to 1st hearing)
    private double backlogMeanDelay;
    private double backlogMedianDelay;
    private double backlogP90Delay;
    private double backlogPercentDelayedPastT; // at T=14
    private int backlogCensoredCount;
    private int backlogTotalCount;

    // Aggregate Statutory Priority Metrics (BAIL, POCSO, MATRIMONIAL)
    private double statutoryMeanFirstHearingDelay;
    private double statutoryMedianFirstHearingDelay;
    private double statutoryP90FirstHearingDelay;
    private double statutoryPercentDelayedPastT; // at T=14
    private Map<Integer, Double> sensitivityDelayedPercent; // T=7, 14, 30

    // Pendency (Reported globally and by type: statutory vs non-priority)
    private double meanTimeToDisposalDisposedCases;
    private double statutoryMeanTimeToDisposal;
    private double nonPriorityMeanTimeToDisposal;
    private double meanAgePendingCasesAtEnd;
    private double statutoryMeanAgePendingCasesAtEnd;
    private double nonPriorityMeanAgePendingCasesAtEnd;
    private int totalDisposals;
    private int totalPendingAtEnd;

    // Aging Distribution by Case Type (Buckets: 0-30, 31-90, 91-180, 181-365, 365+)
    private Map<CaseType, Map<String, Integer>> agingDistribution;

    // Trade-offs & Operations
    private double nonPriorityP90Wait;
    private double nonPriorityMaxWait;
    private double arrivalsNonPriorityMaxWait;
    private double backlogNonPriorityMaxWait;
    private int totalHearingsHeld;
    private double slotUtilizationRate;
    private double judgeWorkloadStdDev;

    // Variant workloads for baseline comparison
    private Double fcfsRoundRobinWorkloadStdDev;
    private Double fcfsTieredWorkloadStdDev;
    private Double fcfsLeastLoadedWorkloadStdDev;
    private Double fcfsNaiveWorkloadStdDev;

    // Survivorship & Combined Lower Bound Pendency Measures (0.4)
    private double overallDisposedPct;
    private double overallPendingPct;
    private double overallCombinedMeanLowerBound;

    private double statutoryDisposedPct;
    private double statutoryPendingPct;
    private double statutoryCombinedMeanLowerBound;

    private double nonPriorityDisposedPct;
    private double nonPriorityPendingPct;
    private double nonPriorityCombinedMeanLowerBound;

    // Separate Non-Priority Sub-Type Diagnosis (0.5)
    private double civilDisposedPct;
    private double civilPendingPct;
    private double civilMeanTimeToDisposal;
    private double civilCombinedMeanLowerBound;
    private double civilMeanAgePendingAtEnd;
    private double civilP90Wait;
    private double civilArrivalsSharePct;

    private double criminalOtherDisposedPct;
    private double criminalOtherPendingPct;
    private double criminalOtherMeanTimeToDisposal;
    private double criminalOtherCombinedMeanLowerBound;
    private double criminalOtherMeanAgePendingAtEnd;
    private double criminalOtherP90Wait;
    private double criminalOtherArrivalsSharePct;
}
