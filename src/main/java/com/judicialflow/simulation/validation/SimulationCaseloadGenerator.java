package com.judicialflow.simulation.validation;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.simulation.NjdgCalibrationTargets;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Generates synthetic initial backlog and daily case arrivals calibrated to NJDG statistics.
 * Uses seed-derived pseudo-random numbers to guarantee identical caseloads across arms.
 */
@Component
public class SimulationCaseloadGenerator {

    public record CaseloadResult(
            List<SimulationCase> initialBacklog,
            Map<LocalDate, List<SimulationCase>> dailyArrivals,
            String arrivalHash
    ) {}

    /**
     * Generates backlog and arrivals for a simulation run.
     */
    public CaseloadResult generateCaseload(
            long seed,
            LocalDate startDate,
            int horizonDays,
            int backlogSize,
            double dailyArrivalRate,
            SimulationConfig.Assumptions assumptions
    ) {
        Random random = new Random(seed);
        List<SimulationCase> backlog = new ArrayList<>();
        Map<LocalDate, List<SimulationCase>> dailyArrivals = new LinkedHashMap<>();

        int caseCounter = 1;

        // 1. Initial Backlog: filed within past 1 to 180 days
        for (int i = 0; i < backlogSize; i++) {
            CaseType caseType = sampleCaseType(random);
            int daysAgo = 1 + random.nextInt(180);
            LocalDate filingDate = startDate.minusDays(daysAgo);

            SimulationCase simCase = createSimulationCase(
                    caseCounter++,
                    caseType,
                    filingDate,
                    true,
                    random,
                    assumptions
            );
            backlog.add(simCase);
        }

        // 2. Daily arrivals for each business day in horizon
        LocalDate current = startDate;
        int businessDaysAdded = 0;
        while (businessDaysAdded < horizonDays) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                // Poisson-like arrival count around dailyArrivalRate
                int arrivalCount = samplePoisson(dailyArrivalRate, random);
                List<SimulationCase> dayArrivals = new ArrayList<>();
                for (int k = 0; k < arrivalCount; k++) {
                    CaseType caseType = sampleCaseType(random);
                    SimulationCase simCase = createSimulationCase(
                            caseCounter++,
                            caseType,
                            current,
                            false,
                            random,
                            assumptions
                    );
                    dayArrivals.add(simCase);
                }
                dailyArrivals.put(current, dayArrivals);
                businessDaysAdded++;
            }
            current = current.plusDays(1);
        }

        // 3. Compute deterministic arrival hash
        String hash = computeArrivalHash(backlog, dailyArrivals);

        return new CaseloadResult(backlog, dailyArrivals, hash);
    }

    private SimulationCase createSimulationCase(
            int index,
            CaseType caseType,
            LocalDate filingDate,
            boolean isBacklog,
            Random random,
            SimulationConfig.Assumptions assumptions
    ) {
        UUID caseId = UUID.nameUUIDFromBytes(("SIM-CASE-" + index).getBytes(StandardCharsets.UTF_8));
        String caseNumber = String.format("SIM-%04d-%05d", filingDate.getYear(), index);

        // Pre-draw statutory deadline: e.g. BAIL 14-30 days from filing, POCSO 60-180 days
        LocalDate statutoryDeadline = null;
        if (caseType == CaseType.BAIL) {
            statutoryDeadline = filingDate.plusDays(14 + random.nextInt(16));
        } else if (caseType == CaseType.POCSO) {
            statutoryDeadline = filingDate.plusDays(60 + random.nextInt(120));
        }

        // Common Random Numbers: pre-draw sequence of adjournment outcomes
        List<Boolean> outcomes = new ArrayList<>();
        int maxHearings = assumptions.getMaxHearingsPerCase();
        for (int h = 0; h < maxHearings; h++) {
            // For the last allowed hearing, force disposal (false)
            if (h == maxHearings - 1) {
                outcomes.add(false);
            } else {
                boolean isAdjourned = random.nextDouble() < assumptions.getAdjournmentProbability();
                outcomes.add(isAdjourned);
            }
        }

        return SimulationCase.builder()
                .caseId(caseId)
                .caseNumber(caseNumber)
                .caseType(caseType)
                .filingDate(filingDate)
                .effectiveFilingDate(filingDate)
                .initialBacklog(isBacklog)
                .statutoryDeadline(statutoryDeadline)
                .linkedCaseId(null)
                .estimatedDurationMinutes(60)
                .caseIndex(index)
                .preDrawnAdjournmentOutcomes(outcomes)
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(0)
                .eligibleAfterDate(filingDate)
                .hearingHistory(new ArrayList<>())
                .build();
    }

    private CaseType sampleCaseType(Random random) {
        double r = random.nextDouble() * 100.0;
        double cumulative = 0.0;
        for (Map.Entry<CaseType, Double> entry : NjdgCalibrationTargets.CASE_TYPE_PERCENTAGES.entrySet()) {
            cumulative += entry.getValue();
            if (r <= cumulative) {
                return entry.getKey();
            }
        }
        return CaseType.CIVIL;
    }

    private int samplePoisson(double lambda, Random random) {
        // Knuth's Poisson generator
        double L = Math.exp(-lambda);
        double k = 1;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);
        return (int) (k - 2);
    }

    public static String computeArrivalHash(
            List<SimulationCase> backlog,
            Map<LocalDate, List<SimulationCase>> dailyArrivals
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (SimulationCase c : backlog) {
                sb.append(c.getCaseNumber()).append(":").append(c.getCaseType()).append(":").append(c.getFilingDate()).append(";");
            }
            dailyArrivals.forEach((date, list) -> {
                sb.append(date).append("->");
                for (SimulationCase c : list) {
                    sb.append(c.getCaseNumber()).append(":").append(c.getCaseType()).append(";");
                }
            });
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }
}
