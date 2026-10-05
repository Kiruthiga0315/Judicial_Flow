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
import java.util.ArrayList;
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
        // Need at least 5 cases for the split to happen.
        List<Case> cases = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Case c = new Case();
            c.setId(UUID.randomUUID());
            c.setCaseType(CaseType.CIVIL);
            c.setCurrentStatus(CaseStatus.DISPOSED);
            c.setPriorAdjournments(i);
            c.setFilingDate(LocalDate.now().minusDays(100L * i));
            c.setDisposedDate(LocalDate.now());
            cases.add(c);
        }

        when(caseRepository.findAll()).thenReturn(cases);

        Case target = new Case();
        target.setId(UUID.randomUUID());
        target.setCaseType(CaseType.CIVIL);
        target.setPriorAdjournments(2);

        DurationEstimateResponse response = durationEstimatorService.estimateForCase(target);

        assertThat(response.getPredictedDurationDays()).isGreaterThan(0);
        assertThat(response.getBasis()).contains("based on 4 training cases and validated on 1 holdout cases");
        
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
