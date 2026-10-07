package com.judicialflow.simulation.validation;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SimulationMetricsCalculatorTest {

    private final SimulationMetricsCalculator calculator = new SimulationMetricsCalculator();
    private final SimulationStatsCalculator statsCalculator = new SimulationStatsCalculator();

    @Test
    @DisplayName("Hand-computed 3-case example: verify exact delays, cohorts, right-censoring at T, and pendency")
    void testHandComputed3CaseExample() {
        // Setup 3 cases:
        // Base Date: 2026-01-01 (sim start)
        // Case 1 (Backlog): BAIL, filed 2025-12-15 (17d before sim), heard day 3 (wait from sim start = 2 days), DISPOSED on day 3.
        // Case 2 (Arrival): POCSO, filed 2026-01-02 (day 2), heard day 17 (wait from filing = 15 days, >14d threshold), FILED.
        // Case 3 (Arrival): MATRIMONIAL, filed 2026-01-01, never heard (right-censored, end date = day 21, wait = 20 days > 14d threshold).
        LocalDate simStart = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 21); // 20 calendar days after sim start

        SimulationCase c1 = SimulationCase.builder()
                .caseId(UUID.randomUUID())
                .caseNumber("CASE-1")
                .caseType(CaseType.BAIL)
                .filingDate(simStart.minusDays(17))
                .initialBacklog(true)
                .firstHearingDate(simStart.plusDays(2))
                .disposalDate(simStart.plusDays(2))
                .currentStatus(CaseStatus.DISPOSED)
                .build();

        SimulationCase c2 = SimulationCase.builder()
                .caseId(UUID.randomUUID())
                .caseNumber("CASE-2")
                .caseType(CaseType.POCSO)
                .filingDate(simStart.plusDays(1))
                .initialBacklog(false)
                .firstHearingDate(simStart.plusDays(16))
                .currentStatus(CaseStatus.FILED)
                .build();

        SimulationCase c3 = SimulationCase.builder()
                .caseId(UUID.randomUUID())
                .caseNumber("CASE-3")
                .caseType(CaseType.MATRIMONIAL)
                .filingDate(simStart)
                .initialBacklog(false)
                .firstHearingDate(null) // never heard (right-censored)
                .currentStatus(CaseStatus.FILED)
                .build();

        List<SimulationCase> cases = List.of(c1, c2, c3);
        Map<UUID, Integer> judgeLoad = Map.of(UUID.randomUUID(), 2);

        ArmMetrics metrics = calculator.calculateMetrics(
                "TEST_ARM",
                cases,
                simStart,
                endDate,
                14,
                List.of(7, 14, 30),
                10,
                2,
                judgeLoad
        );

        // 1. Backlog Cohort:
        // Case 1: wait from sim start = 2 days (<= 14)
        assertEquals(2.0, metrics.getBacklogMeanDelay(), 0.001);
        assertEquals(0.0, metrics.getBacklogPercentDelayedPastT(), 0.001);
        assertEquals(0, metrics.getBacklogCensoredCount());
        assertEquals(1, metrics.getBacklogTotalCount());

        // 2. Arrivals Cohort:
        // Case 2: delay = 16 - 1 = 15 days (> 14)
        // Case 3: right-censored wait = 20 days (> 14), unheard at end
        // Observed mean delay for heard arrival cases = 15.0 days
        assertEquals(15.0, metrics.getArrivalsMeanDelay(), 0.001);
        assertEquals(1, metrics.getArrivalsCensoredCount(), "Case 3 must count as censored");
        assertEquals(2, metrics.getArrivalsTotalCount());
        // Both Case 2 (15d > 14d) and Case 3 (20d > 14d) delayed past T=14 -> 100%
        assertEquals(100.0, metrics.getArrivalsPercentDelayedPastT(), 0.001);

        // 3. Disposals and Pendency
        assertEquals(1, metrics.getTotalDisposals());
        assertEquals(2, metrics.getTotalPendingAtEnd());
        assertEquals(19.0, metrics.getMeanTimeToDisposalDisposedCases(), 0.001); // 17 + 2 = 19 days
    }

    @Test
    @DisplayName("Percentile, StdDev, and Paired 95% CI Outcome Labeling")
    void testStatisticalFunctionsAndCILabeling() {
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        assertEquals(30.0, SimulationMetricsCalculator.percentile(values, 50.0), 0.001);
        assertEquals(46.0, SimulationMetricsCalculator.percentile(values, 90.0), 0.001);

        List<Integer> counts = List.of(10, 10, 10);
        assertEquals(0.0, SimulationMetricsCalculator.calculateStdDev(counts), 0.001);

        // Verify CI labeling logic:
        // Scenario A: CI strictly negative for lower-is-better metric -> Improved
        ArmMetrics f1 = ArmMetrics.builder().arrivalsMeanDelay(25.0).totalHearingsHeld(100).build();
        ArmMetrics e1 = ArmMetrics.builder().arrivalsMeanDelay(15.0).totalHearingsHeld(100).build();
        MetricSummary s1 = statsCalculator.computeSummary("Arrivals Delay", List.of(f1, f1), List.of(e1, e1), ArmMetrics::getArrivalsMeanDelay, true);
        assertEquals("Improved", s1.getOutcome());
        assertEquals(-40.0, s1.getRelativeChangePercent(), 0.01);

        // Scenario B: CI strictly positive for lower-is-better metric -> Worse
        ArmMetrics f2 = ArmMetrics.builder().arrivalsMeanDelay(10.0).build();
        ArmMetrics e2 = ArmMetrics.builder().arrivalsMeanDelay(20.0).build();
        MetricSummary s2 = statsCalculator.computeSummary("Arrivals Delay", List.of(f2, f2), List.of(e2, e2), ArmMetrics::getArrivalsMeanDelay, true);
        assertEquals("Worse", s2.getOutcome());
        assertEquals(100.0, s2.getRelativeChangePercent(), 0.01);

        // Scenario C: CI straddles 0 -> No significant difference
        ArmMetrics fa = ArmMetrics.builder().arrivalsMeanDelay(20.0).build();
        ArmMetrics fb = ArmMetrics.builder().arrivalsMeanDelay(20.0).build();
        ArmMetrics ea = ArmMetrics.builder().arrivalsMeanDelay(15.0).build();
        ArmMetrics eb = ArmMetrics.builder().arrivalsMeanDelay(25.0).build();
        MetricSummary s3 = statsCalculator.computeSummary("Arrivals Delay", List.of(fa, fb), List.of(ea, eb), ArmMetrics::getArrivalsMeanDelay, true);
        assertEquals("No significant difference", s3.getOutcome());

        // Scenario D: Parity check with tolerance
        MetricSummary p1 = statsCalculator.computeSummary("Hearings", List.of(f1), List.of(e1), m -> (double) m.getTotalHearingsHeld(), false, true, 5.0);
        assertTrue(p1.getOutcome().contains("PASS"));
    }
}
