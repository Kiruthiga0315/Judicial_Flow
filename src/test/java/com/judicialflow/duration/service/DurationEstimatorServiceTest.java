package com.judicialflow.duration.service;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.duration.dto.DurationEstimateResponse;
import com.judicialflow.duration.model.DurationEstimate;
import com.judicialflow.duration.repository.DurationEstimateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DurationEstimatorServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private DurationEstimateRepository durationEstimateRepository;

    @InjectMocks
    private DurationEstimatorService durationEstimatorService;

    @BeforeEach
    void setUp() {
        when(durationEstimateRepository.findByCourtCaseId(any())).thenReturn(Optional.empty());
        when(durationEstimateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testTrainAndPredict_withData() {
        // Deterministic training with fixed data
        Case c1 = new Case();
        c1.setId(UUID.randomUUID());
        c1.setCaseType(CaseType.CIVIL);
        c1.setCurrentStatus(CaseStatus.DISPOSED);
        c1.setPriorAdjournments(1);
        c1.setFilingDate(LocalDate.now().minusDays(100));
        c1.setDisposedDate(LocalDate.now()); // 100 days

        Case c2 = new Case();
        c2.setId(UUID.randomUUID());
        c2.setCaseType(CaseType.CIVIL);
        c2.setCurrentStatus(CaseStatus.DISPOSED);
        c2.setPriorAdjournments(3);
        c2.setFilingDate(LocalDate.now().minusDays(150));
        c2.setDisposedDate(LocalDate.now()); // 150 days

        Case c3 = new Case();
        c3.setId(UUID.randomUUID());
        c3.setCaseType(CaseType.CIVIL);
        c3.setCurrentStatus(CaseStatus.DISPOSED);
        c3.setPriorAdjournments(5);
        c3.setFilingDate(LocalDate.now().minusDays(200));
        c3.setDisposedDate(LocalDate.now()); // 200 days

        when(caseRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        Case target = new Case();
        target.setId(UUID.randomUUID());
        target.setCaseType(CaseType.CIVIL);
        target.setPriorAdjournments(2);

        DurationEstimateResponse response = durationEstimatorService.estimateForCase(target);

        assertThat(response.getPredictedDurationDays()).isEqualTo(125.0);
        assertThat(response.getMinDurationDays()).isLessThanOrEqualTo(125);
        assertThat(response.getMaxDurationDays()).isGreaterThanOrEqualTo(125);
        assertThat(response.getBasis()).contains("3 similar synthetic cases");
        
        verify(durationEstimateRepository).save(any(DurationEstimate.class));
    }

    @Test
    void testPredict_insufficientData() {
        when(caseRepository.findAll()).thenReturn(List.of());

        Case target = new Case();
        target.setId(UUID.randomUUID());
        target.setCaseType(CaseType.POCSO);
        target.setPriorAdjournments(1);

        DurationEstimateResponse response = durationEstimatorService.estimateForCase(target);

        assertThat(response.getPredictedDurationDays()).isEqualTo(180);
        assertThat(response.getBasis()).contains("insufficient historical data");
    }
}
