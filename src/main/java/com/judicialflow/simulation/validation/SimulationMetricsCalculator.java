package com.judicialflow.simulation.validation;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure calculator for simulation metrics.
 * Separately calculates Arrivals Cohort and Backlog Cohort with explicit right-censoring.
 */
@Component
public class SimulationMetricsCalculator {

    public static final Set<CaseType> STATUTORY_PRIORITY_TYPES = Set.of(
            CaseType.BAIL, CaseType.POCSO, CaseType.MATRIMONIAL
    );

    public static final List<String> AGING_BUCKETS = List.of(
            "0-30", "31-90", "91-180", "181-365", "365+"
    );

    public ArmMetrics calculateMetrics(
            String armName,
            List<SimulationCase> cases,
            LocalDate simulationEndDate,
            int defaultThresholdT,
            List<Integer> sensitivityThresholds,
            int totalAvailableSlots,
            int totalHearingsHeld,
            Map<UUID, Integer> judgeHearingCounts
    ) {
        // Fallback start date if not provided (assume 90 business days ~ 126 calendar days prior)
        LocalDate startDate = simulationEndDate.minusDays(126);
        return calculateMetrics(armName, cases, startDate, simulationEndDate, defaultThresholdT, sensitivityThresholds, totalAvailableSlots, totalHearingsHeld, judgeHearingCounts);
    }

    public ArmMetrics calculateMetrics(
            String armName,
            List<SimulationCase> cases,
            LocalDate simulationStartDate,
            LocalDate simulationEndDate,
            int defaultThresholdT,
            List<Integer> sensitivityThresholds,
            int totalAvailableSlots,
            int totalHearingsHeld,
            Map<UUID, Integer> judgeHearingCounts
    ) {
        // 1. Statutory cases (BAIL, POCSO, MATRIMONIAL)
        List<SimulationCase> statutoryCases = cases.stream()
                .filter(c -> STATUTORY_PRIORITY_TYPES.contains(c.getCaseType()))
                .collect(Collectors.toList());

        // Separate Cohorts:
        // (a) Arrivals Cohort: cases that arrived during the simulation (not in initial backlog)
        List<SimulationCase> statutoryArrivals = statutoryCases.stream()
                .filter(c -> !c.isInitialBacklog())
                .collect(Collectors.toList());

        // (b) Backlog Cohort: initial backlog cases
        List<SimulationCase> statutoryBacklog = statutoryCases.stream()
                .filter(SimulationCase::isInitialBacklog)
                .collect(Collectors.toList());

        // (a) Arrivals Cohort Metrics: wait from filing date to 1st hearing
        List<Double> arrivalsDelays = new ArrayList<>();
        int arrivalsCensoredCount = 0;
        int arrivalsDelayedPastT = 0;
        for (SimulationCase sc : statutoryArrivals) {
            if (sc.getFirstHearingDate() != null) {
                long wait = Math.max(0, ChronoUnit.DAYS.between(sc.getFilingDate(), sc.getFirstHearingDate()));
                arrivalsDelays.add((double) wait);
                if (wait > defaultThresholdT) {
                    arrivalsDelayedPastT++;
                }
            } else {
                arrivalsCensoredCount++;
                long wait = Math.max(0, ChronoUnit.DAYS.between(sc.getFilingDate(), simulationEndDate));
                if (wait > defaultThresholdT) {
                    arrivalsDelayedPastT++;
                }
            }
        }
        Collections.sort(arrivalsDelays);
        double arrivalsMeanDelay = arrivalsDelays.isEmpty() ? 0.0 : arrivalsDelays.stream().mapToDouble(d -> d).average().orElse(0.0);
        double arrivalsMedianDelay = percentile(arrivalsDelays, 50.0);
        double arrivalsP90Delay = percentile(arrivalsDelays, 90.0);
        double arrivalsPctDelayedPastT = statutoryArrivals.isEmpty() ? 0.0 : (double) arrivalsDelayedPastT / statutoryArrivals.size() * 100.0;

        // (b) Backlog Cohort Metrics: wait from simulation start date to 1st hearing
        List<Double> backlogDelays = new ArrayList<>();
        int backlogCensoredCount = 0;
        int backlogDelayedPastT = 0;
        for (SimulationCase sc : statutoryBacklog) {
            if (sc.getFirstHearingDate() != null) {
                long wait = Math.max(0, ChronoUnit.DAYS.between(simulationStartDate, sc.getFirstHearingDate()));
                backlogDelays.add((double) wait);
                if (wait > defaultThresholdT) {
                    backlogDelayedPastT++;
                }
            } else {
                backlogCensoredCount++;
                long wait = Math.max(0, ChronoUnit.DAYS.between(simulationStartDate, simulationEndDate));
                if (wait > defaultThresholdT) {
                    backlogDelayedPastT++;
                }
            }
        }
        Collections.sort(backlogDelays);
        double backlogMeanDelay = backlogDelays.isEmpty() ? 0.0 : backlogDelays.stream().mapToDouble(d -> d).average().orElse(0.0);
        double backlogMedianDelay = percentile(backlogDelays, 50.0);
        double backlogP90Delay = percentile(backlogDelays, 90.0);
        double backlogPctDelayedPastT = statutoryBacklog.isEmpty() ? 0.0 : (double) backlogDelayedPastT / statutoryBacklog.size() * 100.0;

        // Overall aggregate statutory first-hearing delays (observed heard delays)
        List<Double> observedDelays = new ArrayList<>();
        for (SimulationCase sc : statutoryCases) {
            if (sc.getFirstHearingDate() != null) {
                long delay = ChronoUnit.DAYS.between(sc.getFilingDate(), sc.getFirstHearingDate());
                observedDelays.add((double) Math.max(0, delay));
            }
        }
        Collections.sort(observedDelays);
        double meanDelay = observedDelays.isEmpty() ? 0.0 : observedDelays.stream().mapToDouble(d -> d).average().orElse(0.0);
        double medianDelay = percentile(observedDelays, 50.0);
        double p90Delay = percentile(observedDelays, 90.0);

        Map<Integer, Double> sensitivityMap = new LinkedHashMap<>();
        for (int t : sensitivityThresholds) {
            double pct = calculatePercentDelayedPastT(statutoryCases, simulationEndDate, t);
            sensitivityMap.put(t, pct);
        }
        double pctDelayedPastT = calculatePercentDelayedPastT(statutoryCases, simulationEndDate, defaultThresholdT);

        // 2. Pendency (Both Disposed and Pending at End)
        List<SimulationCase> disposedCases = cases.stream()
                .filter(c -> c.getCurrentStatus() == CaseStatus.DISPOSED)
                .collect(Collectors.toList());

        List<SimulationCase> pendingCases = cases.stream()
                .filter(c -> c.getCurrentStatus() != CaseStatus.DISPOSED)
                .collect(Collectors.toList());

        double meanTimeToDisposal = disposedCases.isEmpty() ? 0.0 :
                disposedCases.stream()
                        .mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposalDate())))
                        .average().orElse(0.0);

        List<SimulationCase> statutoryDisposed = disposedCases.stream()
                .filter(c -> STATUTORY_PRIORITY_TYPES.contains(c.getCaseType()))
                .toList();
        List<SimulationCase> nonPriorityDisposed = disposedCases.stream()
                .filter(c -> !STATUTORY_PRIORITY_TYPES.contains(c.getCaseType()))
                .toList();
        double statutoryMeanTimeToDisposal = statutoryDisposed.isEmpty() ? 0.0 :
                statutoryDisposed.stream()
                        .mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposalDate())))
                        .average().orElse(0.0);
        double nonPriorityMeanTimeToDisposal = nonPriorityDisposed.isEmpty() ? 0.0 :
                nonPriorityDisposed.stream()
                        .mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposalDate())))
                        .average().orElse(0.0);

        double meanAgePending = pendingCases.isEmpty() ? 0.0 :
                pendingCases.stream()
                        .mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), simulationEndDate)))
                        .average().orElse(0.0);

        List<SimulationCase> statutoryPending = pendingCases.stream()
                .filter(c -> STATUTORY_PRIORITY_TYPES.contains(c.getCaseType()))
                .toList();
        List<SimulationCase> nonPriorityPending = pendingCases.stream()
                .filter(c -> !STATUTORY_PRIORITY_TYPES.contains(c.getCaseType()))
                .toList();
        double statutoryMeanAgePending = statutoryPending.isEmpty() ? 0.0 :
                statutoryPending.stream()
                        .mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), simulationEndDate)))
                        .average().orElse(0.0);
        double nonPriorityMeanAgePending = nonPriorityPending.isEmpty() ? 0.0 :
                nonPriorityPending.stream()
                        .mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), simulationEndDate)))
                        .average().orElse(0.0);

        // 3. Aging distribution by case type at simulationEndDate
        Map<CaseType, Map<String, Integer>> agingDist = new EnumMap<>(CaseType.class);
        for (CaseType type : CaseType.values()) {
            Map<String, Integer> buckets = new LinkedHashMap<>();
            AGING_BUCKETS.forEach(b -> buckets.put(b, 0));
            agingDist.put(type, buckets);
        }

        for (SimulationCase sc : pendingCases) {
            long age = Math.max(0, ChronoUnit.DAYS.between(sc.getFilingDate(), simulationEndDate));
            String bucket = getBucketForAge(age);
            agingDist.get(sc.getCaseType()).merge(bucket, 1, Integer::sum);
        }

        // 4. Non-Priority Cases (CIVIL, CRIMINAL_OTHER) Trade-offs
        List<SimulationCase> nonPriorityCases = cases.stream()
                .filter(c -> !STATUTORY_PRIORITY_TYPES.contains(c.getCaseType()))
                .collect(Collectors.toList());

        List<Double> nonPriorityWaits = new ArrayList<>();
        List<Double> arrivalsNonPriorityWaits = new ArrayList<>();
        List<Double> backlogNonPriorityWaits = new ArrayList<>();

        for (SimulationCase npc : nonPriorityCases) {
            double wait = npc.getFirstHearingDate() != null ?
                    (double) ChronoUnit.DAYS.between(npc.getFilingDate(), npc.getFirstHearingDate()) :
                    (double) ChronoUnit.DAYS.between(npc.getFilingDate(), simulationEndDate);
            nonPriorityWaits.add(wait);
            if (npc.isInitialBacklog()) {
                backlogNonPriorityWaits.add(wait);
            } else {
                arrivalsNonPriorityWaits.add(wait);
            }
        }
        Collections.sort(nonPriorityWaits);
        double nonPriorityP90 = percentile(nonPriorityWaits, 90.0);
        double nonPriorityMax = nonPriorityWaits.isEmpty() ? 0.0 : nonPriorityWaits.get(nonPriorityWaits.size() - 1);
        double arrivalsNonPriorityMax = arrivalsNonPriorityWaits.isEmpty() ? 0.0 :
                arrivalsNonPriorityWaits.stream().mapToDouble(d -> d).max().orElse(0.0);
        double backlogNonPriorityMax = backlogNonPriorityWaits.isEmpty() ? 0.0 :
                backlogNonPriorityWaits.stream().mapToDouble(d -> d).max().orElse(0.0);

        // 5. Survivorship & Combined Lower Bound Pendency Measures (0.4)
        List<SimulationCase> allArrivals = cases.stream().filter(c -> !c.isInitialBacklog()).toList();
        double overallDisposedPct = cases.isEmpty() ? 0.0 : (double) disposedCases.size() / cases.size() * 100.0;
        double overallPendingPct = cases.isEmpty() ? 0.0 : (double) pendingCases.size() / cases.size() * 100.0;
        double overallCombinedLowerBound = cases.isEmpty() ? 0.0 :
                cases.stream().mapToDouble(c -> c.getCurrentStatus() == CaseStatus.DISPOSED ?
                        Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposalDate())) :
                        Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), simulationEndDate))).average().orElse(0.0);

        SubTypeMetrics statMetrics = computeSubTypeMetrics(statutoryCases, allArrivals, simulationEndDate);
        SubTypeMetrics nonPrioMetrics = computeSubTypeMetrics(nonPriorityCases, allArrivals, simulationEndDate);

        List<SimulationCase> civilCases = cases.stream().filter(c -> c.getCaseType() == CaseType.CIVIL).toList();
        SubTypeMetrics civilMetrics = computeSubTypeMetrics(civilCases, allArrivals, simulationEndDate);

        List<SimulationCase> crimCases = cases.stream().filter(c -> c.getCaseType() == CaseType.CRIMINAL_OTHER).toList();
        SubTypeMetrics crimMetrics = computeSubTypeMetrics(crimCases, allArrivals, simulationEndDate);

        // 6. Operational metrics
        double slotUtilization = totalAvailableSlots > 0 ?
                Math.min(1.0, (double) totalHearingsHeld / totalAvailableSlots) : 0.0;

        double judgeStdDev = calculateStdDev(judgeHearingCounts.values());

        return ArmMetrics.builder()
                .armName(armName)
                .arrivalsMeanDelay(arrivalsMeanDelay)
                .arrivalsMedianDelay(arrivalsMedianDelay)
                .arrivalsP90Delay(arrivalsP90Delay)
                .arrivalsPercentDelayedPastT(arrivalsPctDelayedPastT)
                .arrivalsCensoredCount(arrivalsCensoredCount)
                .arrivalsTotalCount(statutoryArrivals.size())
                .backlogMeanDelay(backlogMeanDelay)
                .backlogMedianDelay(backlogMedianDelay)
                .backlogP90Delay(backlogP90Delay)
                .backlogPercentDelayedPastT(backlogPctDelayedPastT)
                .backlogCensoredCount(backlogCensoredCount)
                .backlogTotalCount(statutoryBacklog.size())
                .statutoryMeanFirstHearingDelay(meanDelay)
                .statutoryMedianFirstHearingDelay(medianDelay)
                .statutoryP90FirstHearingDelay(p90Delay)
                .statutoryPercentDelayedPastT(pctDelayedPastT)
                .sensitivityDelayedPercent(sensitivityMap)
                .meanTimeToDisposalDisposedCases(meanTimeToDisposal)
                .statutoryMeanTimeToDisposal(statutoryMeanTimeToDisposal)
                .nonPriorityMeanTimeToDisposal(nonPriorityMeanTimeToDisposal)
                .meanAgePendingCasesAtEnd(meanAgePending)
                .statutoryMeanAgePendingCasesAtEnd(statutoryMeanAgePending)
                .nonPriorityMeanAgePendingCasesAtEnd(nonPriorityMeanAgePending)
                .totalDisposals(disposedCases.size())
                .totalPendingAtEnd(pendingCases.size())
                .agingDistribution(agingDist)
                .nonPriorityP90Wait(nonPriorityP90)
                .nonPriorityMaxWait(nonPriorityMax)
                .arrivalsNonPriorityMaxWait(arrivalsNonPriorityMax)
                .backlogNonPriorityMaxWait(backlogNonPriorityMax)
                .totalHearingsHeld(totalHearingsHeld)
                .slotUtilizationRate(slotUtilization)
                .judgeWorkloadStdDev(judgeStdDev)
                .overallDisposedPct(overallDisposedPct)
                .overallPendingPct(overallPendingPct)
                .overallCombinedMeanLowerBound(overallCombinedLowerBound)
                .statutoryDisposedPct(statMetrics.disposedPct())
                .statutoryPendingPct(statMetrics.pendingPct())
                .statutoryCombinedMeanLowerBound(statMetrics.combinedLowerBound())
                .nonPriorityDisposedPct(nonPrioMetrics.disposedPct())
                .nonPriorityPendingPct(nonPrioMetrics.pendingPct())
                .nonPriorityCombinedMeanLowerBound(nonPrioMetrics.combinedLowerBound())
                .civilDisposedPct(civilMetrics.disposedPct())
                .civilPendingPct(civilMetrics.pendingPct())
                .civilMeanTimeToDisposal(civilMetrics.meanTimeToDisposal())
                .civilCombinedMeanLowerBound(civilMetrics.combinedLowerBound())
                .civilMeanAgePendingAtEnd(civilMetrics.meanAgePending())
                .civilP90Wait(civilMetrics.p90Wait())
                .civilArrivalsSharePct(civilMetrics.arrivalsSharePct())
                .criminalOtherDisposedPct(crimMetrics.disposedPct())
                .criminalOtherPendingPct(crimMetrics.pendingPct())
                .criminalOtherMeanTimeToDisposal(crimMetrics.meanTimeToDisposal())
                .criminalOtherCombinedMeanLowerBound(crimMetrics.combinedLowerBound())
                .criminalOtherMeanAgePendingAtEnd(crimMetrics.meanAgePending())
                .criminalOtherP90Wait(crimMetrics.p90Wait())
                .criminalOtherArrivalsSharePct(crimMetrics.arrivalsSharePct())
                .build();
    }

    private record SubTypeMetrics(
            double disposedPct,
            double pendingPct,
            double meanTimeToDisposal,
            double combinedLowerBound,
            double meanAgePending,
            double p90Wait,
            double arrivalsSharePct
    ) {}

    private SubTypeMetrics computeSubTypeMetrics(List<SimulationCase> subCases, List<SimulationCase> allArrivals, LocalDate simulationEndDate) {
        if (subCases.isEmpty()) {
            return new SubTypeMetrics(0, 0, 0, 0, 0, 0, 0);
        }
        List<SimulationCase> disposed = subCases.stream().filter(c -> c.getCurrentStatus() == CaseStatus.DISPOSED).toList();
        List<SimulationCase> pending = subCases.stream().filter(c -> c.getCurrentStatus() != CaseStatus.DISPOSED).toList();
        double dispPct = (double) disposed.size() / subCases.size() * 100.0;
        double pendPct = (double) pending.size() / subCases.size() * 100.0;
        double meanDisposal = disposed.isEmpty() ? 0.0 :
                disposed.stream().mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposalDate()))).average().orElse(0.0);
        double combinedLowerBound = subCases.stream().mapToDouble(c ->
                c.getCurrentStatus() == CaseStatus.DISPOSED ?
                        Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposalDate())) :
                        Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), simulationEndDate))
        ).average().orElse(0.0);
        double meanPendingAge = pending.isEmpty() ? 0.0 :
                pending.stream().mapToDouble(c -> Math.max(0, ChronoUnit.DAYS.between(c.getFilingDate(), simulationEndDate))).average().orElse(0.0);

        List<Double> waits = new ArrayList<>();
        for (SimulationCase sc : subCases) {
            double wait = sc.getFirstHearingDate() != null ?
                    (double) ChronoUnit.DAYS.between(sc.getFilingDate(), sc.getFirstHearingDate()) :
                    (double) ChronoUnit.DAYS.between(sc.getFilingDate(), simulationEndDate);
            waits.add(wait);
        }
        Collections.sort(waits);
        double p90 = percentile(waits, 90.0);

        long arrivalsCount = subCases.stream().filter(c -> !c.isInitialBacklog()).count();
        double shareArrivals = allArrivals.isEmpty() ? 0.0 : (double) arrivalsCount / allArrivals.size() * 100.0;

        return new SubTypeMetrics(dispPct, pendPct, meanDisposal, combinedLowerBound, meanPendingAge, p90, shareArrivals);
    }

    private double calculatePercentDelayedPastT(List<SimulationCase> cases, LocalDate endDate, int thresholdDays) {
        if (cases.isEmpty()) return 0.0;
        int delayedCount = 0;
        for (SimulationCase sc : cases) {
            if (sc.getFirstHearingDate() != null) {
                long delay = ChronoUnit.DAYS.between(sc.getFilingDate(), sc.getFirstHearingDate());
                if (delay > thresholdDays) {
                    delayedCount++;
                }
            } else {
                long age = ChronoUnit.DAYS.between(sc.getFilingDate(), endDate);
                if (age > thresholdDays) {
                    delayedCount++;
                }
            }
        }
        return (double) delayedCount / cases.size() * 100.0;
    }

    private String getBucketForAge(long ageDays) {
        if (ageDays <= 30) return "0-30";
        if (ageDays <= 90) return "31-90";
        if (ageDays <= 180) return "91-180";
        if (ageDays <= 365) return "181-365";
        return "365+";
    }

    public static double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) return 0.0;
        if (sortedValues.size() == 1) return sortedValues.get(0);
        double rank = (percentile / 100.0) * (sortedValues.size() - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) return sortedValues.get(low);
        double d = rank - low;
        return sortedValues.get(low) * (1 - d) + sortedValues.get(high) * d;
    }

    public static double calculateStdDev(Collection<Integer> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double mean = values.stream().mapToInt(v -> v).average().orElse(0.0);
        double sumSq = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum();
        return Math.sqrt(sumSq / values.size());
    }
}
