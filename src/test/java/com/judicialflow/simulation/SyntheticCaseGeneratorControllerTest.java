package com.judicialflow.simulation;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyntheticCaseGeneratorControllerTest {

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private SyntheticCaseGeneratorController controller;

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
        
        // Count by CaseType
        Map<CaseType, Long> typeCounts = generatedCases.stream()
                .collect(Collectors.groupingBy(Case::getCaseType, Collectors.counting()));

        // Expected Targets
        Map<CaseType, Double> expectedPercentages = Map.of(
                CaseType.BAIL, 10.0,
                CaseType.POCSO, 5.0,
                CaseType.CRIMINAL_OTHER, 60.0,
                CaseType.CIVIL, 20.0,
                CaseType.MATRIMONIAL, 5.0
        );

        // Verify tolerance
        expectedPercentages.forEach((type, expectedPct) -> {
            long actualCount = typeCounts.getOrDefault(type, 0L);
            double actualPct = (actualCount * 100.0) / count;
            double diff = Math.abs(expectedPct - actualPct);
            
            System.out.printf("Type: %s | Expected: %.1f%% | Actual: %.1f%% | Diff: %.1f%%%n", 
                              type, expectedPct, actualPct, diff);
            
            assertTrue(diff <= tolerancePercentage, 
                    String.format("Distribution for %s (%.1f%%) exceeds expected (%.1f%%) by more than %.1f%% tolerance", 
                            type, actualPct, expectedPct, tolerancePercentage));
        });
    }
}
