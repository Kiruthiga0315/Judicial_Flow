package com.judicialflow.ingestion.services;

import com.judicialflow.audit.AuditService;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.ingestion.dto.CaseFilterCriteria;
import com.judicialflow.ingestion.dto.CaseRegistryStatsResponse;
import com.judicialflow.priority.config.PriorityWeightsConfig;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.priority.service.PriorityScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceStatsUnitTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private JudgeRepository judgeRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PriorityScoreRepository priorityScoreRepository;

    private PriorityScoreCalculator priorityScoreCalculator;

    private CaseService caseService;

    @BeforeEach
    void setUp() {
        PriorityWeightsConfig weights = new PriorityWeightsConfig();
        priorityScoreCalculator = new PriorityScoreCalculator(weights);
        caseService = new CaseService(null, null, caseRepository,
                judgeRepository,
                auditService,
                priorityScoreRepository,
                priorityScoreCalculator
        );
    }

    @Test
    @DisplayName("getRegistryStats correctly aggregates total, critical (>=70), high (45-69), and scheduled cases")
    void testGetRegistryStatsAggregatesProperly() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();

        // id1: BAIL with 30 days pending and 1 adjournment -> score = 40 + 6 + 5 = 51.0 (high statutory)
        Case c1 = Case.builder()
                .id(id1)
                .caseNumber("BAIL-001")
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now().minusDays(30))
                .currentStatus(CaseStatus.SCHEDULED)
                .priorAdjournments(1)
                .build();

        // id2: POCSO with 180 days pending and 4 adjournments -> score >= 70 (critical urgency)
        Case c2 = Case.builder()
                .id(id2)
                .caseNumber("POCSO-002")
                .caseType(CaseType.POCSO)
                .filingDate(LocalDate.now().minusDays(180))
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(4)
                .build();

        // id3: CIVIL fresh filing -> score < 45 (normal/low)
        Case c3 = Case.builder()
                .id(id3)
                .caseNumber("CIVIL-003")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(5))
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(0)
                .build();

        // id4: DISPOSED case -> excluded from active urgency counts
        Case c4 = Case.builder()
                .id(id4)
                .caseNumber("DISP-004")
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now().minusDays(200))
                .currentStatus(CaseStatus.DISPOSED)
                .build();

        when(caseRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2, c3, c4));
        when(priorityScoreRepository.findAllCaseScoresSummary()).thenReturn(Collections.emptyList());

        CaseRegistryStatsResponse stats = caseService.getRegistryStats(new CaseFilterCriteria());

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalCases()).isEqualTo(4);
        assertThat(stats.getAllocatedSlotsCount()).isEqualTo(1); // c1 is SCHEDULED
        assertThat(stats.getCriticalUrgencyCount()).isEqualTo(1); // c2 has score >= 70
        assertThat(stats.getHighStatutoryCount()).isEqualTo(1); // c1 has score 45-69
    }

    @Test
    @DisplayName("getRegistryStats uses persisted scores from repository when available")
    void testGetRegistryStatsUsesPersistedScoresWhenAvailable() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Case c1 = Case.builder()
                .id(id1)
                .caseNumber("TEST-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.SCHEDULED)
                .build();

        Case c2 = Case.builder()
                .id(id2)
                .caseNumber("TEST-002")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.HEARD)
                .build();

        when(caseRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2));
        // Persisted scores: c1 = 78.5 (critical), c2 = 52.0 (high)
        Object[] row1 = new Object[]{id1, BigDecimal.valueOf(78.5)};
        Object[] row2 = new Object[]{id2, BigDecimal.valueOf(52.0)};
        when(priorityScoreRepository.findAllCaseScoresSummary()).thenReturn(List.of(row1, row2));

        CaseRegistryStatsResponse stats = caseService.getRegistryStats(new CaseFilterCriteria());

        assertThat(stats.getTotalCases()).isEqualTo(2);
        assertThat(stats.getAllocatedSlotsCount()).isEqualTo(2);
        assertThat(stats.getCriticalUrgencyCount()).isEqualTo(1);
        assertThat(stats.getHighStatutoryCount()).isEqualTo(1);
    }
}

