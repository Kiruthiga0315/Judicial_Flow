package com.judicialflow.simulation;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/dev/generator")
@Slf4j
public class SyntheticCaseGeneratorController {

    private final CaseRepository caseRepository;
    private final Random random;

    public SyntheticCaseGeneratorController(
            CaseRepository caseRepository,
            @Value("${generator.seed:12345}") long seed) {
        this.caseRepository = caseRepository;
        this.random = new Random(seed);
        log.info("Initialized SyntheticCaseGeneratorController with seed: {}", seed);
    }

    /*
     * Caseload Calibration Targets & Citations:
     * - Verified NJDG Macro Aggregates (Source: National Judicial Data Grid Public Portal, 2023-2024):
     *   - Macro Criminal vs Civil ratio: ~75% Criminal, ~25% Civil
     *   - Age of case global pendency brackets: <1y: 30%, 1-3y: 30%, 3-5y: 17%, 5-10y: 15%, >10y: 8%
     * - Unverified figures labeled ASSUMPTION:
     *   - Sub-case types: BAIL (10%), POCSO (5%), CRIMINAL_OTHER (60%), CIVIL (20%), MATRIMONIAL (5%) [ASSUMPTION]
     *   - Historical closed case disposal rate: 20% [ASSUMPTION]
     *   - Adjournment relationship: (days / 60) + noise [ASSUMPTION]
     *
     * Note: This is strictly SYNTHETIC data generated for simulation and testing.
     * It does NOT represent real case records, real litigants, or empirical court outcomes.
     */

    @PostMapping("/cases")
    public String generateCases(@RequestParam(defaultValue = "100") int count) {
        log.info("Generating {} synthetic cases based on NJDG aggregates...", count);
        List<Case> casesToSave = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CaseType type = determineCaseType();
            LocalDate filingDate = determineFilingDate(type);
            
            // Phase 2: Disposal Rates
            CaseStatus status = (random.nextInt(100) < NjdgCalibrationTargets.DISPOSAL_RATE_PERCENTAGE) 
                                ? CaseStatus.DISPOSED : CaseStatus.FILED;
            
            LocalDate disposedDate = null;
            if (status == CaseStatus.DISPOSED) {
                // Random disposal date between filing date and today
                int daysSinceFiling = (int) java.time.temporal.ChronoUnit.DAYS.between(filingDate, LocalDate.now());
                int daysToDisposal = daysSinceFiling > 0 ? random.nextInt(daysSinceFiling) : 0;
                disposedDate = filingDate.plusDays(daysToDisposal);
            }

            int adjournments;
            /*
             * Duration vs priorAdjournments relationship:
             * Here, priorAdjournments is directly a deterministic linear function of duration (totalDays or daysSinceFiling)
             * plus random noise (adjournments = (days / 60) + random.nextInt(3)).
             * Thus, duration and priorAdjournments are NOT independent; priorAdjournments is derived as a function of duration,
             * and conversely duration is strongly tied to priorAdjournments.
             */
            if (status == CaseStatus.DISPOSED) {
                // To make the ML model have some actual signal, we loosely correlate adjournments with total duration.
                int totalDays = disposedDate != null ? (int) java.time.temporal.ChronoUnit.DAYS.between(filingDate, disposedDate) : 0;
                adjournments = (totalDays / 60) + random.nextInt(3); 
            } else {
                int daysSinceFiling = (int) java.time.temporal.ChronoUnit.DAYS.between(filingDate, LocalDate.now());
                adjournments = (daysSinceFiling / 60) + random.nextInt(3);
            }

            Case legalCase = Case.builder()
                    .caseNumber("SYN-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .caseType(type)
                    .filingDate(filingDate)
                    .currentStatus(status)
                    .disposedDate(disposedDate)
                    .priorAdjournments(adjournments)
                    .build();
            
            casesToSave.add(legalCase);
        }

        caseRepository.saveAll(casesToSave);
        log.info("Successfully generated and saved {} synthetic cases.", count);
        return "Generated " + count + " synthetic cases.";
    }

    private CaseType determineCaseType() {
        int rand = random.nextInt(100);
        if (rand < 10) return CaseType.BAIL;
        if (rand < 15) return CaseType.POCSO;
        if (rand < 75) return CaseType.CRIMINAL_OTHER;
        if (rand < 95) return CaseType.CIVIL;
        return CaseType.MATRIMONIAL;
    }

    private LocalDate determineFilingDate(CaseType type) {
        LocalDate today = LocalDate.now();
        int daysAgo = 0;

        // Type-specific duration distributions
        if (type == CaseType.BAIL) {
            /*
             * Assumption: No exact published NJDG figure isolates BAIL pendency nationally,
             * but bail matters are statutorily prioritized for liberty. 
             * Assumed distribution: 80% within 6 months, 20% 6-12 months.
             */
            int rand = random.nextInt(100);
            if (rand < 80) {
                daysAgo = random.nextInt(180);
            } else {
                daysAgo = 180 + random.nextInt(185);
            }
            return today.minusDays(daysAgo);
        } 
        
        if (type == CaseType.POCSO) {
            /*
             * Assumption based on POCSO Act mandate: Cases should ideally be disposed within 1 year.
             * Assumed distribution: 50% < 1 year, 40% 1-3 years, 10% 3+ years.
             */
            int rand = random.nextInt(100);
            if (rand < 50) {
                daysAgo = random.nextInt(365);
            } else if (rand < 90) {
                daysAgo = 365 + random.nextInt(730);
            } else {
                daysAgo = 365 * 3 + random.nextInt(730);
            }
            return today.minusDays(daysAgo);
        }

        // Default generic NJDG aggregate for CIVIL, MATRIMONIAL, CRIMINAL_OTHER
        int rand = random.nextInt(100);
        if (rand < 30) {
            // < 1 year
            daysAgo = random.nextInt(365);
        } else if (rand < 60) {
            // 1-3 years
            daysAgo = 365 + random.nextInt(365 * 2);
        } else if (rand < 77) {
            // 3-5 years
            daysAgo = 365 * 3 + random.nextInt(365 * 2);
        } else if (rand < 92) {
            // 5-10 years
            daysAgo = 365 * 5 + random.nextInt(365 * 5);
        } else {
            // > 10 years (up to 20 years for synthetic boundary)
            daysAgo = 365 * 10 + random.nextInt(365 * 10);
        }

        return today.minusDays(daysAgo);
    }
}
