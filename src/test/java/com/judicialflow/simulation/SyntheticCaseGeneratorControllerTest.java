package com.judicialflow.simulation;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyntheticCaseGeneratorControllerTest {

    @Mock
    private CaseRepository caseRepository;

    private SyntheticCaseGeneratorController controller;

    @BeforeEach
    void setUp() {
        // Use a fixed seed for deterministic testing
        controller = new SyntheticCaseGeneratorController(caseRepository, 42L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void test10kCaseDistributionMatchesNJDGWithTolerance() {
        int count = 10000;
        double tolerancePercentage = 1.5;

        // Execute generator
        controller.generateCases(count);

        // Capture saved cases
        ArgumentCaptor<List<Case>> captor = ArgumentCaptor.forClass(List.class);
        verify(caseRepository).saveAll(captor.capture());
        
        List<Case> generatedCases = captor.getValue();
        
        // Count by CaseType and verify disposed logic
        Map<CaseType, Long> typeCounts = generatedCases.stream()
                .peek(c -> {
                    if (c.getCurrentStatus() == com.judicialflow.common.enums.CaseStatus.DISPOSED) {
                        assertNotNull(c.getDisposedDate(), "Disposed cases must have a disposed_date");
                        assertTrue(!c.getDisposedDate().isBefore(c.getFilingDate()), "Disposed date cannot be before filing date");
                        assertTrue(!c.getDisposedDate().isAfter(java.time.LocalDate.now()), "Disposed date cannot be in the future");
                    } else {
                        assertNull(c.getDisposedDate(), "Non-disposed cases must not have a disposed_date");
                    }
                })
                .collect(Collectors.groupingBy(Case::getCaseType, Collectors.counting()));

        // Verify tolerance against approximate NJDG targets
        NjdgCalibrationTargets.CASE_TYPE_PERCENTAGES.forEach((type, expectedPct) -> {
            long actualCount = typeCounts.getOrDefault(type, 0L);
            double actualPct = (actualCount * 100.0) / count;
            double diff = Math.abs(expectedPct - actualPct);
            
            System.out.printf("Type: %s | Expected: %.1f%% | Actual: %.1f%% | Diff: %.1f%%%n", 
                              type, expectedPct, actualPct, diff);
            
            assertTrue(diff <= tolerancePercentage, 
                    String.format("Distribution for %s (%.1f%%) exceeds expected (%.1f%%) by more than %.1f points tolerance", 
                            type, actualPct, expectedPct, tolerancePercentage));
        });
    }
}
