package com.judicialflow.priority;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Priority Scoring REST endpoints.
 *
 * <p>Uses Testcontainers (PostgreSQL) via {@link AbstractIntegrationTest} and a
 * real Spring context.  The test seeds cases directly via the repository and then
 * verifies the HTTP responses — including the breakdown structure returned by the
 * API.
 */
import org.springframework.security.test.context.support.WithMockUser;

@DisplayName("PriorityScoreController integration tests")
@WithMockUser(roles = "REGISTRAR")
class PriorityScoreControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private PriorityScoreRepository priorityScoreRepository;

    @Autowired
    private com.judicialflow.scheduling.repository.SchedulingProposalRepository proposalRepository;

    @Autowired
    private com.judicialflow.scheduling.repository.SchedulingRunRepository runRepository;

    @Autowired
    private com.judicialflow.common.HearingRepository hearingRepository;

    @BeforeEach
    void cleanUp() {
        hearingRepository.deleteAll();
        proposalRepository.deleteAll();
        runRepository.deleteAll();
        priorityScoreRepository.deleteAll();
        caseRepository.deleteAll();
    }

    // =========================================================================
    // Seed helper
    // =========================================================================

    private Case seedCase(String number, CaseType type, LocalDate filingDate,
                           int adjournments, Case linked, CaseStatus status) {
        return caseRepository.save(Case.builder()
                .caseNumber(number)
                .caseType(type)
                .filingDate(filingDate)
                .currentStatus(status)
                .priorAdjournments(adjournments)
                .linkedCase(linked)
                .deleted(false)
                .build());
    }

    // =========================================================================
    // GET /api/priority/cases/{caseId}/score
    // =========================================================================

    @Test
    @DisplayName("GET /score: returns 200 with score and 4-factor breakdown for a BAIL case")
    void computeScoreForBailCase() throws Exception {
        Case legalCase = seedCase("BAIL-INT-001", CaseType.BAIL,
                LocalDate.now().minusDays(100), 2, null, CaseStatus.FILED);

        mockMvc.perform(get("/api/v1/priority/cases/{id}/score", legalCase.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(legalCase.getId().toString()))
                .andExpect(jsonPath("$.caseNumber").value("BAIL-INT-001"))
                .andExpect(jsonPath("$.totalScore").value(closeTo(70.0, 0.001)))
                // Breakdown must have 5 factors
                .andExpect(jsonPath("$.factors", hasSize(5)))
                // Highest factor must be case type urgency (50) – factors sorted desc
                .andExpect(jsonPath("$.factors[0].factorName").value("Case Type Urgency"))
                .andExpect(jsonPath("$.factors[0].contribution").value(closeTo(50.0, 0.001)))
                // Summary is present
                .andExpect(jsonPath("$.summary").isNotEmpty())
                // Persisted score ID must be a UUID string
                .andExpect(jsonPath("$.persistedScoreId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /score: persists a row to priority_scores table")
    void computeScorePersiststoDb() throws Exception {
        Case legalCase = seedCase("POCSO-INT-001", CaseType.POCSO,
                LocalDate.now().minusDays(50), 0, null, CaseStatus.FILED);

        mockMvc.perform(get("/api/v1/priority/cases/{id}/score", legalCase.getId()))
                .andExpect(status().isOk());

        // DB should now have one record for this case
        java.util.List<com.judicialflow.common.models.PriorityScore> allScores = priorityScoreRepository.findAll();
        org.assertj.core.api.Assertions.assertThat(allScores).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(allScores.get(0).getLegalCase().getId())
                .isEqualTo(legalCase.getId());
        org.assertj.core.api.Assertions.assertThat(allScores.get(0).getTotalScore())
                .isNotNull();
    }

    @Test
    @DisplayName("GET /score: returns 404 for unknown case ID")
    void computeScoreUnknownCase() throws Exception {
        mockMvc.perform(get("/api/v1/priority/cases/{id}/score",
                        java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /score: returns 404 for soft-deleted case")
    void computeScoreSoftDeletedCase() throws Exception {
        Case legalCase = seedCase("CIVIL-DEL-001", CaseType.CIVIL,
                LocalDate.now().minusDays(10), 0, null, CaseStatus.FILED);
        legalCase.setDeleted(true);
        legalCase.setDeletedAt(java.time.LocalDateTime.now());
        caseRepository.save(legalCase);

        mockMvc.perform(get("/api/v1/priority/cases/{id}/score", legalCase.getId()))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // GET /api/priority/cases/{caseId}/score/latest
    // =========================================================================

    @Test
    @DisplayName("GET /score/latest: returns computed score when none stored")
    void getLatestScoreNoHistory() throws Exception {
        Case legalCase = seedCase("MATRIMONIAL-INT-001", CaseType.MATRIMONIAL,
                LocalDate.now().minusDays(30), 1, null, CaseStatus.FILED);

        mockMvc.perform(get("/api/v1/priority/cases/{id}/score/latest", legalCase.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.factors", hasSize(5)));
    }

    @Test
    @DisplayName("GET /score/latest: each call to /score appends a new history row")
    void multipleComputeCallsAppendHistory() throws Exception {
        Case legalCase = seedCase("BAIL-HIST-001", CaseType.BAIL,
                LocalDate.now().minusDays(60), 0, null, CaseStatus.FILED);

        mockMvc.perform(get("/api/v1/priority/cases/{id}/score", legalCase.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/priority/cases/{id}/score", legalCase.getId()))
                .andExpect(status().isOk());

        // Two rows in history
        org.assertj.core.api.Assertions.assertThat(
                priorityScoreRepository.findAllByCaseIdOrderByComputedAtDesc(legalCase.getId()))
                .hasSize(2);
    }

    // =========================================================================
    // GET /api/priority/cases/top?limit=N
    // =========================================================================

    @Test
    @DisplayName("GET /top: returns top-N cases ordered by score descending")
    void topNCasesOrdered() throws Exception {
        // Seed 3 cases with different urgency levels
        seedCase("BAIL-TOP-001", CaseType.BAIL,
                LocalDate.now().minusDays(200), 3, null, CaseStatus.FILED);
        seedCase("CIVIL-TOP-001", CaseType.CIVIL,
                LocalDate.now().minusDays(10), 0, null, CaseStatus.FILED);
        seedCase("POCSO-TOP-001", CaseType.POCSO,
                LocalDate.now().minusDays(150), 2, null, CaseStatus.FILED);

        mockMvc.perform(get("/api/v1/priority/cases/top").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // First result should have higher score than second
                .andExpect(jsonPath("$[0].totalScore").isNumber())
                .andExpect(jsonPath("$[1].totalScore").isNumber())
                // All results should have breakdowns
                .andExpect(jsonPath("$[0].factors", hasSize(5)));
    }

    @Test
    @DisplayName("GET /top: DISPOSED cases are excluded from ranking")
    void topNExcludesDisposedCases() throws Exception {
        seedCase("BAIL-DISPOSED-001", CaseType.BAIL,
                LocalDate.now().minusDays(300), 5, null, CaseStatus.DISPOSED);
        Case openCase = seedCase("CIVIL-OPEN-001", CaseType.CIVIL,
                LocalDate.now().minusDays(10), 0, null, CaseStatus.FILED);

        mockMvc.perform(get("/api/v1/priority/cases/top").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value(openCase.getId().toString()));
    }

    @Test
    @DisplayName("GET /top: returns empty list when no open cases exist")
    void topNEmptyWhenNoCases() throws Exception {
        mockMvc.perform(get("/api/v1/priority/cases/top").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /top: default limit is 10")
    void topNDefaultLimit() throws Exception {
        for (int i = 0; i < 15; i++) {
            seedCase("CIVIL-DEF-" + String.format("%03d", i), CaseType.CIVIL,
                    LocalDate.now().minusDays(i + 1), 0, null, CaseStatus.FILED);
        }

        mockMvc.perform(get("/api/v1/priority/cases/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }
}
