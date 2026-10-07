package com.judicialflow.simulation.validation;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.priority.dto.PriorityScoreResult;
import com.judicialflow.priority.service.PriorityScoreCalculator;
import com.judicialflow.scheduling.engine.CandidateSlot;
import com.judicialflow.scheduling.engine.HardConstraintChecker;
import com.judicialflow.scheduling.engine.SchedulingEngine;
import com.judicialflow.scheduling.engine.SchedulingInput;
import com.judicialflow.scheduling.engine.SchedulingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes discrete-event time-stepped simulation for both FCFS and JudicialFlow Engine arms.
 * Replays identical caseload arrivals and pre-drawn CRN outcomes without writing to domain JPA tables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationRunnerService {

    private final SimulationConfig simulationConfig;
    private final SimulationCaseloadGenerator caseloadGenerator;
    private final PriorityScoreCalculator scoreCalculator;
    private final FcfsScheduler fcfsScheduler;
    private final SimulationMetricsCalculator metricsCalculator;

    public record SeedArmResult(
            long seed,
            String arrivalHash,
            ArmMetrics fcfsMetrics,
            ArmMetrics fcfsTieredMetrics,
            ArmMetrics engineMetrics,
            boolean hardConstraintViolationsDetected,
            boolean arrivalHashMatched
    ) {}

    public record ScenarioExecutionResult(
            String scenarioKey,
            String scenarioName,
            double capacityMultiplier,
            double offeredLoadFactor,
            double statutoryShareArrivalsPercent,
            double statutoryOfferedLoad,
            List<SeedArmResult> seedResults,
            List<MetricSummary> metricSummaries,
            List<MetricSummary> tieredMetricSummaries,
            Map<CaseType, Map<String, Integer>> fcfsAggregatedAging,
            Map<CaseType, Map<String, Integer>> engineAggregatedAging,
            boolean sanityChecksPassed
    ) {}

    public record SensitivityResult(
            String scenarioKey,
            String parameterName,
            String valueLabel,
            int seedCount,
            MetricSummary arrivalsMeanDelaySummary,
            MetricSummary arrivalsDelayedPastTPctSummary,
            MetricSummary nonPriorityP90WaitSummary,
            MetricSummary totalDisposalsSummary,
            MetricSummary arrivalsMeanDelayTieredSummary,
            MetricSummary arrivalsDelayedPastTPctTieredSummary,
            MetricSummary nonPriorityP90WaitTieredSummary,
            MetricSummary totalDisposalsTieredSummary
    ) {}

    /**
     * Runs a full scenario across all provided seeds.
     */
    public ScenarioExecutionResult runScenario(
            String scenarioKey,
            String scenarioName,
            double dailyArrivalRate,
            double capacityMultiplier,
            List<Long> seeds,
            LocalDate startDate,
            SimulationStatsCalculator statsCalculator
    ) {
        List<SeedArmResult> seedResults = new ArrayList<>();

        int dailySlots = simulationConfig.getCourtroomsCount() * simulationConfig.getSlotsPerDayPerRoom();
        double expectedHearings = 1.0 / (1.0 - simulationConfig.getAssumptions().getAdjournmentProbability());
        double offeredLoadFactor = dailySlots > 0 ? (dailyArrivalRate * expectedHearings) / dailySlots : 1.0;
        String loadLabel = String.format(Locale.US, "%.1f", offeredLoadFactor);

        for (Long seed : seeds) {
            long startNanos = System.nanoTime();
            SeedArmResult result = runSingleSeed(seed, dailyArrivalRate, capacityMultiplier, startDate);
            seedResults.add(result);
            double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            log.info("scenario={} seed={} done in {} s", loadLabel, seed, String.format(Locale.US, "%.2f", elapsedSeconds));
        }

        // Aggregate summaries across seeds
        List<ArmMetrics> fcfsList = seedResults.stream().map(SeedArmResult::fcfsMetrics).toList();
        List<ArmMetrics> tieredList = seedResults.stream().map(SeedArmResult::fcfsTieredMetrics).toList();
        List<ArmMetrics> engineList = seedResults.stream().map(SeedArmResult::engineMetrics).toList();

        List<MetricSummary> headlineSummaries = computeArmSummaries(fcfsList, engineList, statsCalculator);
        List<MetricSummary> tieredSummaries = computeArmSummaries(tieredList, engineList, statsCalculator);

        // Statutory share of arrivals from NJDG targets: BAIL (10%) + POCSO (5%) + MATRIMONIAL (5%) = 20.0%
        double statutoryShare = 20.0;
        double statutoryOfferedLoad = Math.round(offeredLoadFactor * (statutoryShare / 100.0) * 100.0) / 100.0;

        // Aggregate Aging Distributions
        Map<CaseType, Map<String, Integer>> fcfsAging = aggregateAging(fcfsList);
        Map<CaseType, Map<String, Integer>> engineAging = aggregateAging(engineList);

        // Sanity Check: All arrival hashes match, zero hard constraint violations, throughput parity
        boolean sanityPassed = seedResults.stream().allMatch(r ->
                r.arrivalHashMatched() && !r.hardConstraintViolationsDetected()
        );

        return new ScenarioExecutionResult(
                scenarioKey,
                scenarioName,
                capacityMultiplier,
                Math.round(offeredLoadFactor * 100.0) / 100.0,
                statutoryShare,
                statutoryOfferedLoad,
                seedResults,
                headlineSummaries,
                tieredSummaries,
                fcfsAging,
                engineAging,
                sanityPassed
        );
    }

    private List<MetricSummary> computeArmSummaries(
            List<ArmMetrics> baselineList,
            List<ArmMetrics> engineList,
            SimulationStatsCalculator statsCalculator
    ) {
        List<MetricSummary> summaries = new ArrayList<>();

        // PRIMARY METRICS: Arrivals Cohort (cases that arrived during the simulation)
        summaries.add(statsCalculator.computeSummary(
                "Arrivals Statutory Mean Delay (days)", baselineList, engineList, ArmMetrics::getArrivalsMeanDelay, true));
        summaries.add(statsCalculator.computeSummary(
                "Arrivals Statutory Median Delay (days)", baselineList, engineList, ArmMetrics::getArrivalsMedianDelay, true));
        summaries.add(statsCalculator.computeSummary(
                "Arrivals Statutory P90 Delay (days)", baselineList, engineList, ArmMetrics::getArrivalsP90Delay, true));
        summaries.add(statsCalculator.computeSummary(
                "Arrivals Statutory % Delayed > 14d", baselineList, engineList, ArmMetrics::getArrivalsPercentDelayedPastT, true));
        summaries.add(statsCalculator.computeSummary(
                "Arrivals Censored Count", baselineList, engineList, m -> (double) m.getArrivalsCensoredCount(), true));

        // BACKLOG COHORT (wait from simulation start date)
        summaries.add(statsCalculator.computeSummary(
                "Backlog Statutory Mean Delay (days)", baselineList, engineList, ArmMetrics::getBacklogMeanDelay, true));
        summaries.add(statsCalculator.computeSummary(
                "Backlog Statutory Median Delay (days)", baselineList, engineList, ArmMetrics::getBacklogMedianDelay, true));
        summaries.add(statsCalculator.computeSummary(
                "Backlog Statutory P90 Delay (days)", baselineList, engineList, ArmMetrics::getBacklogP90Delay, true));
        summaries.add(statsCalculator.computeSummary(
                "Backlog Statutory % Delayed > 14d", baselineList, engineList, ArmMetrics::getBacklogPercentDelayedPastT, true));
        summaries.add(statsCalculator.computeSummary(
                "Backlog Censored Count", baselineList, engineList, m -> (double) m.getBacklogCensoredCount(), true));

        // Overall Pendency & Disposals
        summaries.add(statsCalculator.computeSummary(
                "Mean Time to Disposal (days)", baselineList, engineList, ArmMetrics::getMeanTimeToDisposalDisposedCases, true));
        summaries.add(statsCalculator.computeSummary(
                "Statutory Mean Time to Disposal (days)", baselineList, engineList, ArmMetrics::getStatutoryMeanTimeToDisposal, true));
        summaries.add(statsCalculator.computeSummary(
                "Non-Priority Mean Time to Disposal (days)", baselineList, engineList, ArmMetrics::getNonPriorityMeanTimeToDisposal, true));
        summaries.add(statsCalculator.computeSummary(
                "Mean Pending Age at End (days)", baselineList, engineList, ArmMetrics::getMeanAgePendingCasesAtEnd, true));
        summaries.add(statsCalculator.computeSummary(
                "Statutory Mean Pending Age at End (days)", baselineList, engineList, ArmMetrics::getStatutoryMeanAgePendingCasesAtEnd, true));
        summaries.add(statsCalculator.computeSummary(
                "Non-Priority Mean Pending Age at End (days)", baselineList, engineList, ArmMetrics::getNonPriorityMeanAgePendingCasesAtEnd, true));
        summaries.add(statsCalculator.computeSummary(
                "Total Disposals", baselineList, engineList, m -> (double) m.getTotalDisposals(), false));

        // Survivorship & Combined Lower Bound Pendency Measures (0.4)
        summaries.add(statsCalculator.computeSummary(
                "Overall Disposed within Horizon (%)", baselineList, engineList, ArmMetrics::getOverallDisposedPct, false));
        summaries.add(statsCalculator.computeSummary(
                "Overall Still Pending at End (%)", baselineList, engineList, ArmMetrics::getOverallPendingPct, true));
        summaries.add(statsCalculator.computeSummary(
                "Combined Mean Time to Disposal Lower Bound (days)", baselineList, engineList, ArmMetrics::getOverallCombinedMeanLowerBound, true));
        summaries.add(statsCalculator.computeSummary(
                "Statutory Combined Lower Bound (days)", baselineList, engineList, ArmMetrics::getStatutoryCombinedMeanLowerBound, true));
        summaries.add(statsCalculator.computeSummary(
                "Non-Priority Combined Lower Bound (days)", baselineList, engineList, ArmMetrics::getNonPriorityCombinedMeanLowerBound, true));

        // Non-Priority Sub-Type Breakdown: CIVIL vs CRIMINAL_OTHER (0.5)
        summaries.add(statsCalculator.computeSummary(
                "CIVIL Mean Time to Disposal (disposed, days)", baselineList, engineList, ArmMetrics::getCivilMeanTimeToDisposal, true));
        summaries.add(statsCalculator.computeSummary(
                "CIVIL Combined Lower Bound (days)", baselineList, engineList, ArmMetrics::getCivilCombinedMeanLowerBound, true));
        summaries.add(statsCalculator.computeSummary(
                "CIVIL Disposed (%)", baselineList, engineList, ArmMetrics::getCivilDisposedPct, false));
        summaries.add(statsCalculator.computeSummary(
                "CIVIL Pending (%)", baselineList, engineList, ArmMetrics::getCivilPendingPct, true));
        summaries.add(statsCalculator.computeSummary(
                "CIVIL Mean Pending Age at End (days)", baselineList, engineList, ArmMetrics::getCivilMeanAgePendingAtEnd, true));
        summaries.add(statsCalculator.computeSummary(
                "CIVIL P90 Wait (days)", baselineList, engineList, ArmMetrics::getCivilP90Wait, true));
        summaries.add(statsCalculator.computeSummary(
                "CRIMINAL_OTHER Mean Time to Disposal (disposed, days)", baselineList, engineList, ArmMetrics::getCriminalOtherMeanTimeToDisposal, true));
        summaries.add(statsCalculator.computeSummary(
                "CRIMINAL_OTHER Combined Lower Bound (days)", baselineList, engineList, ArmMetrics::getCriminalOtherCombinedMeanLowerBound, true));
        summaries.add(statsCalculator.computeSummary(
                "CRIMINAL_OTHER Disposed (%)", baselineList, engineList, ArmMetrics::getCriminalOtherDisposedPct, false));
        summaries.add(statsCalculator.computeSummary(
                "CRIMINAL_OTHER Pending (%)", baselineList, engineList, ArmMetrics::getCriminalOtherPendingPct, true));
        summaries.add(statsCalculator.computeSummary(
                "CRIMINAL_OTHER Mean Pending Age at End (days)", baselineList, engineList, ArmMetrics::getCriminalOtherMeanAgePendingAtEnd, true));
        summaries.add(statsCalculator.computeSummary(
                "CRIMINAL_OTHER P90 Wait (days)", baselineList, engineList, ArmMetrics::getCriminalOtherP90Wait, true));

        // Non-Priority Trade-offs
        summaries.add(statsCalculator.computeSummary(
                "Non-Priority P90 Wait (days)", baselineList, engineList, ArmMetrics::getNonPriorityP90Wait, true));
        summaries.add(statsCalculator.computeSummary(
                "Arrivals Non-Priority Max Wait (days)", baselineList, engineList, ArmMetrics::getArrivalsNonPriorityMaxWait, true));
        summaries.add(statsCalculator.computeSummary(
                "Backlog Non-Priority Max Wait (days)", baselineList, engineList, ArmMetrics::getBacklogNonPriorityMaxWait, true));

        // Capacity and Throughput Parity Checks
        summaries.add(statsCalculator.computeSummary(
                "Total Hearings Held", baselineList, engineList, m -> (double) m.getTotalHearingsHeld(), false, true, 10.0));
        summaries.add(statsCalculator.computeSummary(
                "Slot Utilization Rate", baselineList, engineList, ArmMetrics::getSlotUtilizationRate, false, true, 0.05));

        // Judge Workload Distribution across variants
        summaries.add(statsCalculator.computeSummary(
                "Judge Workload StdDev (Baseline vs Engine)", baselineList, engineList, ArmMetrics::getJudgeWorkloadStdDev, true));
        summaries.add(statsCalculator.computeSummary(
                "Judge Workload StdDev (FCFS-Tiered)", baselineList, engineList, m -> m.getFcfsTieredWorkloadStdDev() != null ? m.getFcfsTieredWorkloadStdDev() : 0.0, true));
        summaries.add(statsCalculator.computeSummary(
                "Judge Workload StdDev (FCFS-LeastLoaded)", baselineList, engineList, m -> m.getFcfsLeastLoadedWorkloadStdDev() != null ? m.getFcfsLeastLoadedWorkloadStdDev() : 0.0, true));
        summaries.add(statsCalculator.computeSummary(
                "Judge Workload StdDev (FCFS-Naive Strawman)", baselineList, engineList, m -> m.getFcfsNaiveWorkloadStdDev() != null ? m.getFcfsNaiveWorkloadStdDev() : 0.0, true));

        return summaries;
    }

    /**
     * Simulates one seed for both FCFS and Engine arms.
     */
    public SeedArmResult runSingleSeed(
            long seed,
            double dailyArrivalRate,
            double capacityMultiplier,
            LocalDate startDate
    ) {
        return runSingleSeedWithAssumptions(seed, dailyArrivalRate, capacityMultiplier, startDate, simulationConfig.getAssumptions(), simulationConfig.getCadenceDays());
    }

    public SeedArmResult runSingleSeedWithAssumptions(
            long seed,
            double dailyArrivalRate,
            double capacityMultiplier,
            LocalDate startDate,
            SimulationConfig.Assumptions assumptions,
            int cadenceDays
    ) {
        int horizonDays = simulationConfig.getHorizonDays();
        int backlogSize = simulationConfig.getBacklogSize();

        // 1. Generate Caseload (same for both arms)
        SimulationCaseloadGenerator.CaseloadResult caseload = caseloadGenerator.generateCaseload(
                seed,
                startDate,
                horizonDays,
                backlogSize,
                dailyArrivalRate,
                assumptions
        );

        // Setup bench (judges & courtrooms)
        List<SchedulingInput.JudgeInfo> judges = createSimulatedJudges(simulationConfig.getJudgesCount());
        List<SchedulingInput.CourtroomInfo> courtrooms = createSimulatedCourtrooms(simulationConfig.getCourtroomsCount());

        // Clone cases for FCFS Headline (ROUND_ROBIN)
        List<SimulationCase> fcfsCases = new ArrayList<>();
        caseload.initialBacklog().forEach(c -> fcfsCases.add(c.copy()));
        Map<LocalDate, List<SimulationCase>> fcfsArrivals = copyArrivals(caseload.dailyArrivals());

        // Verify clone arrival hash
        String fcfsHash = SimulationCaseloadGenerator.computeArrivalHash(fcfsCases, fcfsArrivals);
        boolean hashMatches = caseload.arrivalHash().equals(fcfsHash);

        // Simulate FCFS Headline (Round-Robin)
        ArmExecutionResult fcfsExec = executeArm(
                "FCFS_ROUND_ROBIN",
                fcfsCases,
                fcfsArrivals,
                judges,
                courtrooms,
                startDate,
                horizonDays,
                cadenceDays,
                false,
                FcfsScheduler.FcfsVariant.ROUND_ROBIN,
                assumptions
        );

        // Clone and simulate FCFS Tiered (Secondary Baseline: Statutory first by filing date, then non-priority, round-robin judges)
        List<SimulationCase> tieredCases = new ArrayList<>();
        caseload.initialBacklog().forEach(c -> tieredCases.add(c.copy()));
        Map<LocalDate, List<SimulationCase>> tieredArrivals = copyArrivals(caseload.dailyArrivals());
        ArmExecutionResult tieredExec = executeArm(
                "FCFS_TIERED",
                tieredCases,
                tieredArrivals,
                judges,
                courtrooms,
                startDate,
                horizonDays,
                cadenceDays,
                false,
                FcfsScheduler.FcfsVariant.TIERED,
                assumptions
        );

        // Clone and simulate FCFS Least-Loaded for workload std dev comparison
        List<SimulationCase> leastLoadedCases = new ArrayList<>();
        caseload.initialBacklog().forEach(c -> leastLoadedCases.add(c.copy()));
        Map<LocalDate, List<SimulationCase>> leastLoadedArrivals = copyArrivals(caseload.dailyArrivals());
        ArmExecutionResult leastLoadedExec = executeArm(
                "FCFS_LEAST_LOADED",
                leastLoadedCases,
                leastLoadedArrivals,
                judges,
                courtrooms,
                startDate,
                horizonDays,
                cadenceDays,
                false,
                FcfsScheduler.FcfsVariant.LEAST_LOADED,
                assumptions
        );

        // Clone and simulate FCFS Naive for strawman workload std dev comparison
        List<SimulationCase> naiveCases = new ArrayList<>();
        caseload.initialBacklog().forEach(c -> naiveCases.add(c.copy()));
        Map<LocalDate, List<SimulationCase>> naiveArrivals = copyArrivals(caseload.dailyArrivals());
        ArmExecutionResult naiveExec = executeArm(
                "FCFS_NAIVE",
                naiveCases,
                naiveArrivals,
                judges,
                courtrooms,
                startDate,
                horizonDays,
                cadenceDays,
                false,
                FcfsScheduler.FcfsVariant.NAIVE,
                assumptions
        );

        // Clone cases for JudicialFlow Engine arm
        List<SimulationCase> engineCases = new ArrayList<>();
        caseload.initialBacklog().forEach(c -> engineCases.add(c.copy()));
        Map<LocalDate, List<SimulationCase>> engineArrivals = copyArrivals(caseload.dailyArrivals());

        // Simulate JudicialFlow Engine arm
        ArmExecutionResult engineExec = executeArm(
                "JUDICIALFLOW_ENGINE",
                engineCases,
                engineArrivals,
                judges,
                courtrooms,
                startDate,
                horizonDays,
                cadenceDays,
                true,
                null,
                assumptions
        );

        // Attach variant workload std devs to baseline & engine metrics
        double rrStdDev = fcfsExec.metrics.getJudgeWorkloadStdDev();
        double tieredStdDev = tieredExec.metrics.getJudgeWorkloadStdDev();
        double llStdDev = leastLoadedExec.metrics.getJudgeWorkloadStdDev();
        double naiveStdDev = naiveExec.metrics.getJudgeWorkloadStdDev();

        fcfsExec.metrics.setFcfsRoundRobinWorkloadStdDev(rrStdDev);
        fcfsExec.metrics.setFcfsTieredWorkloadStdDev(tieredStdDev);
        fcfsExec.metrics.setFcfsLeastLoadedWorkloadStdDev(llStdDev);
        fcfsExec.metrics.setFcfsNaiveWorkloadStdDev(naiveStdDev);

        tieredExec.metrics.setFcfsRoundRobinWorkloadStdDev(rrStdDev);
        tieredExec.metrics.setFcfsTieredWorkloadStdDev(tieredStdDev);
        tieredExec.metrics.setFcfsLeastLoadedWorkloadStdDev(llStdDev);
        tieredExec.metrics.setFcfsNaiveWorkloadStdDev(naiveStdDev);

        engineExec.metrics.setFcfsRoundRobinWorkloadStdDev(rrStdDev);
        engineExec.metrics.setFcfsTieredWorkloadStdDev(tieredStdDev);
        engineExec.metrics.setFcfsLeastLoadedWorkloadStdDev(llStdDev);
        engineExec.metrics.setFcfsNaiveWorkloadStdDev(naiveStdDev);

        boolean violations = fcfsExec.hasViolations || tieredExec.hasViolations || engineExec.hasViolations;

        return new SeedArmResult(
                seed,
                caseload.arrivalHash(),
                fcfsExec.metrics,
                tieredExec.metrics,
                engineExec.metrics,
                violations,
                hashMatches
        );
    }

    private Map<LocalDate, List<SimulationCase>> copyArrivals(Map<LocalDate, List<SimulationCase>> source) {
        Map<LocalDate, List<SimulationCase>> copy = new LinkedHashMap<>();
        source.forEach((d, list) -> {
            List<SimulationCase> copyList = new ArrayList<>();
            list.forEach(c -> copyList.add(c.copy()));
            copy.put(d, copyList);
        });
        return copy;
    }

    private record ArmExecutionResult(ArmMetrics metrics, boolean hasViolations) {}

    private ArmExecutionResult executeArm(
            String armName,
            List<SimulationCase> activeBacklog,
            Map<LocalDate, List<SimulationCase>> dailyArrivals,
            List<SchedulingInput.JudgeInfo> judges,
            List<SchedulingInput.CourtroomInfo> courtrooms,
            LocalDate startDate,
            int horizonDays,
            int cadenceDays,
            boolean isEngine,
            FcfsScheduler.FcfsVariant fcfsVariant,
            SimulationConfig.Assumptions assumptions
    ) {
        LocalDate currentSimDate = startDate;
        int businessDaysElapsed = 0;
        int totalHearingsHeld = 0;
        Map<UUID, Integer> judgeHearingCounts = new HashMap<>();
        judges.forEach(j -> judgeHearingCounts.put(j.getJudgeId(), 0));

        List<SimulationCase> allCases = new ArrayList<>(activeBacklog);
        boolean constraintViolations = false;

        SchedulingEngine engine = new SchedulingEngine();

        while (businessDaysElapsed < horizonDays) {
            int runHorizonDays = Math.min(cadenceDays, horizonDays - businessDaysElapsed);

            // Ingest arrivals up to current date
            List<SimulationCase> todaysArrivals = dailyArrivals.getOrDefault(currentSimDate, List.of());
            for (SimulationCase arr : todaysArrivals) {
                if (!allCases.contains(arr)) {
                    allCases.add(arr);
                    activeBacklog.add(arr);
                }
            }

            // Filter eligible cases: FILED, not disposed, eligibleAfterDate <= currentSimDate
            LocalDate finalCurrentSimDate = currentSimDate;
            List<SimulationCase> eligibleCases = activeBacklog.stream()
                    .filter(c -> c.getCurrentStatus() != CaseStatus.DISPOSED)
                    .filter(c -> c.getEligibleAfterDate() == null || !c.getEligibleAfterDate().isAfter(finalCurrentSimDate))
                    .collect(Collectors.toList());

            // Build SchedulingInput
            List<SchedulingInput.CaseInfo> caseInfos = new ArrayList<>();
            for (SimulationCase sc : eligibleCases) {
                BigDecimal score = BigDecimal.ZERO;
                if (isEngine) {
                    PriorityScoreResult ps = scoreCalculator.calculate(sc.toDomainCase(), finalCurrentSimDate);
                    score = ps.getTotalScore();
                }
                caseInfos.add(SchedulingInput.CaseInfo.builder()
                        .caseId(sc.getCaseId())
                        .caseNumber(sc.getCaseNumber())
                        .caseType(sc.getCaseType())
                        .statutoryPriority(sc.getCaseType() == CaseType.BAIL || sc.getCaseType() == CaseType.POCSO || sc.getCaseType() == CaseType.MATRIMONIAL)
                        .filingDate(sc.getFilingDate())
                        .effectiveFilingDate(sc.getEffectiveFilingDate() != null ? sc.getEffectiveFilingDate() : sc.getFilingDate())
                        .priorityScore(score)
                        .linkedCaseId(sc.getLinkedCaseId())
                        .estimatedDurationMinutes(sc.getEstimatedDurationMinutes())
                        .build());
            }

            SchedulingInput input = SchedulingInput.builder()
                    .cases(caseInfos)
                    .judges(judges)
                    .courtrooms(courtrooms)
                    .horizonStart(currentSimDate)
                    .horizonDays(runHorizonDays)
                    .defaultDurationMinutes(simulationConfig.getDefaultDurationMinutes())
                    .repairMaxIterations(100)
                    .build();

            // Run chosen scheduler
            SchedulingResult result;
            if (isEngine) {
                result = engine.solve(input, 42L);
            } else {
                result = fcfsScheduler.schedule(input, fcfsVariant);
            }

            // Verify zero hard-constraint violations in assignments
            if (hasHardConstraintViolations(result.getAssignments())) {
                constraintViolations = true;
            }

            // Execute scheduled hearings & process CRN outcomes
            for (SchedulingResult.ProposedAssignment pa : result.getAssignments()) {
                SimulationCase sc = findCase(eligibleCases, pa.getCaseId());
                if (sc == null) continue;

                LocalDate hearingDate = pa.getProposedTime().toLocalDate();
                if (sc.getFirstHearingDate() == null) {
                    sc.setFirstHearingDate(hearingDate);
                }
                sc.getHearingHistory().add(pa.getProposedTime());
                totalHearingsHeld++;
                judgeHearingCounts.merge(pa.getJudgeId(), 1, Integer::sum);

                // Outcome from pre-drawn CRN
                int hearingNum = sc.getHearingHistory().size() - 1;
                boolean adjourn = true;
                if (hearingNum < sc.getPreDrawnAdjournmentOutcomes().size()) {
                    adjourn = sc.getPreDrawnAdjournmentOutcomes().get(hearingNum);
                } else {
                    adjourn = false; // dispose if exceeds pre-drawn
                }

                if (adjourn) {
                    sc.setPriorAdjournments(sc.getPriorAdjournments() + 1);
                    sc.setCurrentStatus(CaseStatus.FILED);
                    // Cooling gap before next eligible hearing
                    sc.setEligibleAfterDate(hearingDate.plusDays(assumptions.getMinimumAdjournmentGapDays()));
                    if (!assumptions.isFcfsPreservesFilingDate()) {
                        // In sensitivity test: adjourned cases re-queued with new timestamp
                        sc.setEffectiveFilingDate(hearingDate);
                    }
                } else {
                    sc.setCurrentStatus(CaseStatus.DISPOSED);
                    sc.setDisposalDate(hearingDate);
                    activeBacklog.remove(sc);
                }
            }

            // Advance clock by cadenceDays
            int advanced = 0;
            while (advanced < runHorizonDays) {
                currentSimDate = currentSimDate.plusDays(1);
                if (currentSimDate.getDayOfWeek() != DayOfWeek.SATURDAY && currentSimDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    advanced++;
                    businessDaysElapsed++;
                    List<SimulationCase> arrs = dailyArrivals.getOrDefault(currentSimDate, List.of());
                    for (SimulationCase arr : arrs) {
                        if (!allCases.contains(arr)) {
                            allCases.add(arr);
                            activeBacklog.add(arr);
                        }
                    }
                }
            }
        }

        LocalDate simEndDate = currentSimDate;
        int totalAvailableSlots = horizonDays * courtrooms.size() * simulationConfig.getSlotsPerDayPerRoom();

        ArmMetrics metrics = metricsCalculator.calculateMetrics(
                armName,
                allCases,
                startDate,
                simEndDate,
                simulationConfig.getThresholdTDays(),
                simulationConfig.getSensitivityThresholds(),
                totalAvailableSlots,
                totalHearingsHeld,
                judgeHearingCounts
        );

        return new ArmExecutionResult(metrics, constraintViolations);
    }

    /**
     * Executes sensitivity analysis across Balanced and Overloaded scenarios.
     */
    public List<SensitivityResult> runSensitivityAnalysis(
            List<Long> seeds,
            LocalDate startDate,
            SimulationStatsCalculator statsCalculator
    ) {
        return runSensitivityAnalysis(seeds, startDate, statsCalculator, List.of());
    }

    public List<SensitivityResult> runSensitivityAnalysis(
            List<Long> seeds,
            LocalDate startDate,
            SimulationStatsCalculator statsCalculator,
            List<ScenarioExecutionResult> mainScenarioResults
    ) {
        List<SensitivityResult> results = new ArrayList<>();

        record Variation(String paramName, String valueLabel, double prob, int gap, boolean keepPos, int cadence) {}

        List<Variation> variations = List.of(
                new Variation("Baseline", "p=0.45, gap=7d, pos=keep, cad=5d", 0.45, 7, true, 5),
                new Variation("Adjournment Probability", "p = 0.25", 0.25, 7, true, 5),
                new Variation("Adjournment Probability", "p = 0.65", 0.65, 7, true, 5),
                new Variation("Minimum Cooling Gap", "gap = 3 days", 0.45, 3, true, 5),
                new Variation("Minimum Cooling Gap", "gap = 14 days", 0.45, 14, true, 5),
                new Variation("FCFS Adjourned Policy", "re-queued with new date", 0.45, 7, false, 5),
                new Variation("Rescheduling Cadence", "cadence = 1 day", 0.45, 7, true, 1),
                new Variation("Rescheduling Cadence", "cadence = 10 days", 0.45, 7, true, 10)
        );

        Map<String, Double> targetArrivalRates = new LinkedHashMap<>();
        targetArrivalRates.put("balanced", 19.25);
        targetArrivalRates.put("overloaded", 25.0);

        for (Map.Entry<String, Double> scenarioEntry : targetArrivalRates.entrySet()) {
            String scenKey = scenarioEntry.getKey();
            double arrivalRate = scenarioEntry.getValue();

            for (Variation v : variations) {
                List<SeedArmResult> seedResults = new ArrayList<>();

                // If Baseline variation and mainScenarioResults contains this scenario, reuse exact seed results to guarantee consistency
                Optional<ScenarioExecutionResult> mainMatch = mainScenarioResults.stream()
                        .filter(m -> m.scenarioKey().equals(scenKey))
                        .findFirst();

                if ("Baseline".equals(v.paramName()) && mainMatch.isPresent() && mainMatch.get().seedResults().size() == seeds.size()) {
                    seedResults = mainMatch.get().seedResults();
                } else {
                    SimulationConfig.Assumptions customAssumptions = new SimulationConfig.Assumptions();
                    customAssumptions.setAdjournmentProbability(v.prob());
                    customAssumptions.setMinimumAdjournmentGapDays(v.gap());
                    customAssumptions.setFcfsPreservesFilingDate(v.keepPos());

                    for (Long s : seeds) {
                        SeedArmResult res = runSingleSeedWithAssumptions(s, arrivalRate, 1.0, startDate, customAssumptions, v.cadence());
                        seedResults.add(res);
                    }
                }

                List<ArmMetrics> fcfsList = seedResults.stream().map(SeedArmResult::fcfsMetrics).toList();
                List<ArmMetrics> tieredList = seedResults.stream().map(SeedArmResult::fcfsTieredMetrics).toList();
                List<ArmMetrics> engineList = seedResults.stream().map(SeedArmResult::engineMetrics).toList();

                // Headline deltas: Engine vs FCFS-RR
                MetricSummary meanDelaySummary = statsCalculator.computeSummary("Arrivals Statutory Mean Delay", fcfsList, engineList, ArmMetrics::getArrivalsMeanDelay, true);
                MetricSummary delayedPctSummary = statsCalculator.computeSummary("Arrivals % Delayed > 14d", fcfsList, engineList, ArmMetrics::getArrivalsPercentDelayedPastT, true);
                MetricSummary nonPrioWaitSummary = statsCalculator.computeSummary("Non-Priority P90 Wait", fcfsList, engineList, ArmMetrics::getNonPriorityP90Wait, true);
                MetricSummary disposalsSummary = statsCalculator.computeSummary("Total Disposals", fcfsList, engineList, m -> (double) m.getTotalDisposals(), false);

                // Secondary deltas: Engine vs FCFS-Tiered
                MetricSummary meanDelayTieredSummary = statsCalculator.computeSummary("Arrivals Statutory Mean Delay", tieredList, engineList, ArmMetrics::getArrivalsMeanDelay, true);
                MetricSummary delayedPctTieredSummary = statsCalculator.computeSummary("Arrivals % Delayed > 14d", tieredList, engineList, ArmMetrics::getArrivalsPercentDelayedPastT, true);
                MetricSummary nonPrioWaitTieredSummary = statsCalculator.computeSummary("Non-Priority P90 Wait", tieredList, engineList, ArmMetrics::getNonPriorityP90Wait, true);
                MetricSummary disposalsTieredSummary = statsCalculator.computeSummary("Total Disposals", tieredList, engineList, m -> (double) m.getTotalDisposals(), false);

                results.add(new SensitivityResult(
                        scenKey,
                        v.paramName(),
                        v.valueLabel(),
                        seeds.size(),
                        meanDelaySummary,
                        delayedPctSummary,
                        nonPrioWaitSummary,
                        disposalsSummary,
                        meanDelayTieredSummary,
                        delayedPctTieredSummary,
                        nonPrioWaitTieredSummary,
                        disposalsTieredSummary
                ));
            }
        }

        return results;
    }

    private boolean hasHardConstraintViolations(List<SchedulingResult.ProposedAssignment> assignments) {
        Map<UUID, List<SchedulingResult.ProposedAssignment>> judgeBookings = new HashMap<>();
        Map<UUID, List<SchedulingResult.ProposedAssignment>> crBookings = new HashMap<>();

        for (SchedulingResult.ProposedAssignment a : assignments) {
            judgeBookings.computeIfAbsent(a.getJudgeId(), k -> new ArrayList<>()).add(a);
            crBookings.computeIfAbsent(a.getCourtroomId(), k -> new ArrayList<>()).add(a);
        }

        for (List<SchedulingResult.ProposedAssignment> list : judgeBookings.values()) {
            if (hasOverlap(list)) return true;
        }
        for (List<SchedulingResult.ProposedAssignment> list : crBookings.values()) {
            if (hasOverlap(list)) return true;
        }
        return false;
    }

    private boolean hasOverlap(List<SchedulingResult.ProposedAssignment> list) {
        list.sort(Comparator.comparing(SchedulingResult.ProposedAssignment::getProposedTime));
        for (int i = 0; i < list.size() - 1; i++) {
            LocalDateTime end1 = list.get(i).getProposedTime().plusMinutes(list.get(i).getDurationMinutes());
            LocalDateTime start2 = list.get(i + 1).getProposedTime();
            if (end1.isAfter(start2)) {
                return true;
            }
        }
        return false;
    }

    private SimulationCase findCase(List<SimulationCase> cases, UUID caseId) {
        for (SimulationCase c : cases) {
            if (c.getCaseId().equals(caseId)) return c;
        }
        return null;
    }

    private List<SchedulingInput.JudgeInfo> createSimulatedJudges(int count) {
        List<SchedulingInput.JudgeInfo> judges = new ArrayList<>();
        List<SchedulingInput.AvailabilityWindow> windows = createStandardWeekdayWindows();
        for (int i = 1; i <= count; i++) {
            UUID id = UUID.nameUUIDFromBytes(("SIM-JUDGE-" + i).getBytes(StandardCharsets.UTF_8));
            judges.add(SchedulingInput.JudgeInfo.builder()
                    .judgeId(id)
                    .judgeName("Judge " + i)
                    .availabilityWindows(windows)
                    .build());
        }
        return judges;
    }

    private List<SchedulingInput.CourtroomInfo> createSimulatedCourtrooms(int count) {
        List<SchedulingInput.CourtroomInfo> rooms = new ArrayList<>();
        List<SchedulingInput.AvailabilityWindow> windows = createStandardWeekdayWindows();
        for (int i = 1; i <= count; i++) {
            UUID id = UUID.nameUUIDFromBytes(("SIM-ROOM-" + i).getBytes(StandardCharsets.UTF_8));
            rooms.add(SchedulingInput.CourtroomInfo.builder()
                    .courtroomId(id)
                    .courtroomName("Courtroom " + i)
                    .availabilityWindows(windows)
                    .build());
        }
        return rooms;
    }

    private List<SchedulingInput.AvailabilityWindow> createStandardWeekdayWindows() {
        List<SchedulingInput.AvailabilityWindow> windows = new ArrayList<>();
        List<DayOfWeek> weekdays = List.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        );
        int slotsPerDay = simulationConfig.getSlotsPerDayPerRoom();
        for (DayOfWeek day : weekdays) {
            if (slotsPerDay == 7) {
                // Morning session: 09:00 - 12:00 (3 slots of 60 mins)
                windows.add(SchedulingInput.AvailabilityWindow.builder()
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(12, 0))
                        .build());
                // Afternoon session: 13:00 - 17:00 (4 slots of 60 mins, 12:00-13:00 lunch recess)
                windows.add(SchedulingInput.AvailabilityWindow.builder()
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(13, 0))
                        .endTime(LocalTime.of(17, 0))
                        .build());
            } else {
                windows.add(SchedulingInput.AvailabilityWindow.builder()
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(9, 0).plusHours(slotsPerDay))
                        .build());
            }
        }
        return windows;
    }

    private Map<CaseType, Map<String, Integer>> aggregateAging(List<ArmMetrics> metricsList) {
        Map<CaseType, Map<String, Integer>> aggregated = new EnumMap<>(CaseType.class);
        for (CaseType type : CaseType.values()) {
            Map<String, Integer> bucketSums = new LinkedHashMap<>();
            SimulationMetricsCalculator.AGING_BUCKETS.forEach(b -> bucketSums.put(b, 0));
            for (ArmMetrics m : metricsList) {
                Map<String, Integer> dist = m.getAgingDistribution().get(type);
                if (dist != null) {
                    dist.forEach((bucket, count) -> bucketSums.merge(bucket, count, Integer::sum));
                }
            }
            aggregated.put(type, bucketSums);
        }
        return aggregated;
    }
}
