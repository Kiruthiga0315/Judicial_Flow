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
@RequestMapping("/api/dev/generator")
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
     * NJDG Approximate Aggregate Calibration:
     * - Case Types:
     *   - Criminal (75%): BAIL (~10%), POCSO (~5%), CRIMINAL_OTHER (~60%)
     *   - Civil (25%): CIVIL (~20%), MATRIMONIAL (~5%)
     * 
     * - Pendency Duration (Age of case):
     *   - < 1 year: 30%
     *   - 1-3 years: 30%
     *   - 3-5 years: 17%
     *   - 5-10 years: 15%
     *   - > 10 years: 8%
     *
     * Note: This is SYNTHETIC data calibrated to NJDG aggregates. 
     * It does NOT represent real case records or real individuals.
     */

    @PostMapping("/cases")
    public String generateCases(@RequestParam(defaultValue = "100") int count) {
        log.info("Generating {} synthetic cases based on NJDG aggregates...", count);
        List<Case> casesToSave = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CaseType type = determineCaseType();
            LocalDate filingDate = determineFilingDate();
            
            // Phase 2: Disposal Rates (approx 20% of generated cases are historical/disposed)
            CaseStatus status = (random.nextInt(100) < 20) ? CaseStatus.DISPOSED : CaseStatus.PENDING;
            
            // Phase 2: Citations (generate random citations for criminal types)
            String citations = null;
            if (type == CaseType.BAIL || type == CaseType.POCSO || type == CaseType.CRIMINAL_OTHER) {
                citations = "IPC " + (300 + random.nextInt(200));
            }

            Case legalCase = Case.builder()
                    .caseNumber("SYN-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .caseType(type)
                    .filingDate(filingDate)
                    .currentStatus(status)
                    .citations(citations)
                    .priorAdjournments(random.nextInt(10)) // random 0-9 adjournments
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

    private LocalDate determineFilingDate() {
        int rand = random.nextInt(100);
        LocalDate today = LocalDate.now();
        int daysAgo = 0;

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
