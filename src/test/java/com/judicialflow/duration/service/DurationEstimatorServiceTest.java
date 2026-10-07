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
import org.springframework.test.util.ReflectionTestUtils;

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
        ReflectionTestUtils.setField(durationEstimatorService, "seed", 42L);
        lenient().when(durationEstimateRepository.findByCourtCaseId(any())).thenReturn(Optional.empty());
        lenient().when(durationEstimateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testTrainAndPredict_LOOCV_with8Cases() {
        List<Case> cases = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
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
        assertThat(response.getBasis()).contains("leave-one-out");
        
        verify(durationEstimateRepository).save(any(DurationEstimate.class));
    }

    @Test
    void testTrainAndPredict_Clamping_NegativeSubtraction() {
        List<Case> cases = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Case c = new Case();
            c.setId(UUID.randomUUID());
            c.setCaseType(CaseType.BAIL);
            c.setCurrentStatus(CaseStatus.DISPOSED);
            c.setPriorAdjournments(i); // Strong positive correlation with Adjournments
            c.setFilingDate(LocalDate.now().minusDays(10L * i)); // Very short days!
            c.setDisposedDate(LocalDate.now());
            cases.add(c);
        }

        when(caseRepository.findAll()).thenReturn(cases);

        Case target = new Case();
        target.setId(UUID.randomUUID());
        target.setCaseType(CaseType.BAIL);
        target.setPriorAdjournments(0); // This will predict close to 0 or negative before clamping

        DurationEstimateResponse response = durationEstimatorService.estimateForCase(target);

        assertThat(response.getPredictedDurationDays()).isGreaterThanOrEqualTo(1.0);
        assertThat(response.getMinDurationDays()).isGreaterThanOrEqualTo(1);
        assertThat(response.getMaxDurationDays()).isGreaterThanOrEqualTo(1);
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
        assertThat(response.getBasis()).contains("fallback, insufficient data");
    }

    @Test
    void testSeedStability_ProducesIdenticalModels() {
        List<Case> cases = new ArrayList<>();
        for (int i = 1; i <= 35; i++) { // > 30 triggers 80/20 split
            Case c = new Case();
            c.setId(UUID.randomUUID()); // Random UUIDs!
            c.setCaseType(CaseType.CRIMINAL_OTHER);
            c.setCurrentStatus(CaseStatus.DISPOSED);
            c.setPriorAdjournments(i % 5);
            c.setFilingDate(LocalDate.now().minusDays(50L * i));
            c.setDisposedDate(LocalDate.now());
            cases.add(c);
        }

        when(caseRepository.findAll()).thenReturn(cases);

        // Train 1
        DurationEstimatorService service1 = new DurationEstimatorService(caseRepository, durationEstimateRepository);
        ReflectionTestUtils.setField(service1, "seed", 42L);
        service1.trainModels();
        
        // Train 2
        DurationEstimatorService service2 = new DurationEstimatorService(caseRepository, durationEstimateRepository);
        ReflectionTestUtils.setField(service2, "seed", 42L);
        service2.trainModels();

        Case target = new Case();
        target.setId(UUID.randomUUID());
        target.setCaseType(CaseType.CRIMINAL_OTHER);
        target.setPriorAdjournments(2);

        DurationEstimateResponse r1 = service1.estimateForCase(target);
        DurationEstimateResponse r2 = service2.estimateForCase(target);

        assertThat(r1.getPredictedDurationDays()).isEqualTo(r2.getPredictedDurationDays());
        assertThat(r1.getTestMae()).isEqualTo(r2.getTestMae());
    }

    @Test
    void testMultiTypeFixture_deliberateInsufficientDataAndBaselineComparison() {
        List<Case> cases = new ArrayList<>();

        // 1. BAIL: 12 cases with strong linear correlation (adjournment * 20 days) -> Beats baseline
        for (int i = 1; i <= 12; i++) {
            Case c = new Case();
            c.setId(UUID.randomUUID());
            c.setCaseType(CaseType.BAIL);
            c.setCurrentStatus(CaseStatus.DISPOSED);
            c.setPriorAdjournments(i);
            c.setFilingDate(LocalDate.now().minusDays(20L * i));
            c.setDisposedDate(LocalDate.now());
            cases.add(c);
        }

        // 2. CRIMINAL_OTHER: 35 cases where regression does not beat baseline
        for (int i = 1; i <= 35; i++) {
            Case c = new Case();
            c.setId(UUID.randomUUID());
            c.setCaseType(CaseType.CRIMINAL_OTHER);
            c.setCurrentStatus(CaseStatus.DISPOSED);
            c.setPriorAdjournments(i % 5);
            c.setFilingDate(LocalDate.now().minusDays(50L * i));
            c.setDisposedDate(LocalDate.now());
            cases.add(c);
        }

        // 3. POCSO: 3 cases (< THRESHOLD_FALLBACK = 8) -> Deliberate insufficient data
        for (int i = 1; i <= 3; i++) {
            Case c = new Case();
            c.setId(UUID.randomUUID());
            c.setCaseType(CaseType.POCSO);
            c.setCurrentStatus(CaseStatus.DISPOSED);
            c.setPriorAdjournments(i);
            c.setFilingDate(LocalDate.now().minusDays(40L * i));
            c.setDisposedDate(LocalDate.now());
            cases.add(c);
        }

        when(caseRepository.findAll()).thenReturn(cases);

        // Train models
        durationEstimatorService.trainModels();

        // Check BAIL: should beat baseline and report sample count
        Case bailTarget = new Case();
        bailTarget.setId(UUID.randomUUID());
        bailTarget.setCaseType(CaseType.BAIL);
        bailTarget.setPriorAdjournments(3);

        DurationEstimateResponse bailResp = durationEstimatorService.estimateForCase(bailTarget);
        assertThat(bailResp.isBeatsBaseline()).isTrue();
        assertThat(bailResp.getTrainingSampleCount()).isEqualTo(11);
        assertThat(bailResp.getTestMae()).isLessThan(bailResp.getBaselineMaeDays());
        assertThat(bailResp.getBasis()).contains("beats Baseline MAE");

        // Check CRIMINAL_OTHER: did not beat baseline -> must fall back to naive per-type-mean baseline
        Case crimTarget = new Case();
        crimTarget.setId(UUID.randomUUID());
        crimTarget.setCaseType(CaseType.CRIMINAL_OTHER);
        crimTarget.setPriorAdjournments(2);

        DurationEstimateResponse crimResp = durationEstimatorService.estimateForCase(crimTarget);
        assertThat(crimResp.isBeatsBaseline()).isFalse();
        assertThat(crimResp.getTrainingSampleCount()).isEqualTo(28); // 80% of 35
        assertThat(crimResp.getBasis()).contains("keeping type on naive mean baseline");

        // Check POCSO: insufficient data -> fallback
        Case pocsoTarget = new Case();
        pocsoTarget.setId(UUID.randomUUID());
        pocsoTarget.setCaseType(CaseType.POCSO);
        pocsoTarget.setPriorAdjournments(1);

        DurationEstimateResponse pocsoResp = durationEstimatorService.estimateForCase(pocsoTarget);
        assertThat(pocsoResp.getPredictedDurationDays()).isEqualTo(180);
        assertThat(pocsoResp.getBasis()).contains("fallback, insufficient data");

        // Verify model evaluations map
        var evals = durationEstimatorService.getModelEvaluations();
        assertThat(evals).containsKey(CaseType.BAIL);
        assertThat(evals).containsKey(CaseType.CRIMINAL_OTHER);
        assertThat(evals.get(CaseType.BAIL).isBeatsBaseline()).isTrue();
        assertThat(evals.get(CaseType.CRIMINAL_OTHER).isUsingBaseline()).isTrue();
        assertThat(evals.get(CaseType.POCSO)).isNull(); // Insufficient data
    }
}
