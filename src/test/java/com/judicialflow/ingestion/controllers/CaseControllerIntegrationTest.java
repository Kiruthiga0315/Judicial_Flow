package com.judicialflow.ingestion.controllers;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Judge;
import com.judicialflow.ingestion.dto.CreateCaseRequest;
import com.judicialflow.ingestion.dto.UpdateCaseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(roles = "REGISTRAR")
class CaseControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private com.judicialflow.scheduling.repository.SchedulingProposalRepository proposalRepository;

    @Autowired
    private com.judicialflow.scheduling.repository.SchedulingRunRepository runRepository;

    @Autowired
    private com.judicialflow.common.HearingRepository hearingRepository;

    @BeforeEach
    void setUp() {
        hearingRepository.deleteAll();
        proposalRepository.deleteAll();
        runRepository.deleteAll();
        caseRepository.deleteAll();
        judgeRepository.deleteAll();
    }

    @Test
    @DisplayName("CRUD: Successfully create, retrieve, update, filter, and soft-delete cases")
    void testCaseCrudAndLifecycle() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder()
                .name("Hon. Justice R. F. Nariman")
                .specialization("Civil")
                .build());

        CreateCaseRequest parentReq = CreateCaseRequest.builder()
                .caseNumber("CIV-2024-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.of(2024, 1, 15))
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(0)
                .assignedJudgeId(judge.getId())
                .build();

        // 1. Create Parent Case
        String parentJson = mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.caseNumber").value("CIV-2024-001"))
                .andExpect(jsonPath("$.caseType").value("CIVIL"))
                .andExpect(jsonPath("$.assignedJudgeId").value(judge.getId().toString()))
                .andExpect(jsonPath("$.deleted").value(false))
                .andReturn().getResponse().getContentAsString();

        UUID parentId = UUID.fromString(objectMapper.readTree(parentJson).get("id").asText());

        // 2. Create Child Linked Case
        CreateCaseRequest childReq = CreateCaseRequest.builder()
                .caseNumber("CIV-2024-002")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.of(2024, 2, 20))
                .currentStatus(CaseStatus.FILED)
                .linkedCaseId(parentId)
                .assignedJudgeId(judge.getId())
                .build();

        String childJson = mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedCaseId").value(parentId.toString()))
                .andExpect(jsonPath("$.linkedCaseNumber").value("CIV-2024-001"))
                .andReturn().getResponse().getContentAsString();

        UUID childId = UUID.fromString(objectMapper.readTree(childJson).get("id").asText());

        // 3. Get Case by ID
        mockMvc.perform(get("/api/v1/cases/{id}", childId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(childId.toString()))
                .andExpect(jsonPath("$.caseNumber").value("CIV-2024-002"));

        // 4. Update Case
        UpdateCaseRequest updateReq = UpdateCaseRequest.builder()
                .caseNumber("CIV-2024-002-AMENDED")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.of(2024, 2, 20))
                .currentStatus(CaseStatus.SCHEDULED)
                .priorAdjournments(2)
                .linkedCaseId(parentId)
                .assignedJudgeId(judge.getId())
                .build();

        mockMvc.perform(put("/api/v1/cases/{id}", childId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber").value("CIV-2024-002-AMENDED"))
                .andExpect(jsonPath("$.currentStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.priorAdjournments").value(2));

        // 5. List with Filters and Pagination
        mockMvc.perform(get("/api/v1/cases")
                        .param("status", "SCHEDULED")
                        .param("caseType", "CIVIL")
                        .param("judgeId", judge.getId().toString())
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-12-31")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "caseNumber,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].caseNumber").value("CIV-2024-002-AMENDED"));

        // 6. Soft-delete Child Case
        mockMvc.perform(delete("/api/v1/cases/{id}", childId))
                .andExpect(status().isNoContent());

        // Verify soft-deleted in database
        Case softDeletedInDb = caseRepository.findById(childId).orElseThrow();
        assertTrue(softDeletedInDb.isDeleted());

        // Verify standard GET returns 404
        mockMvc.perform(get("/api/v1/cases/{id}", childId))
                .andExpect(status().isNotFound());

        // Verify GET with includeDeleted=true returns 200
        mockMvc.perform(get("/api/v1/cases/{id}", childId).param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        // Verify omitted from list by default
        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(parentId.toString()));
    }

    @Test
    @DisplayName("Validation Failure 1: Missing filing date returns 400 Bad Request")
    void testCreateCaseMissingFilingDateFails() throws Exception {
        String payload = """
                {
                    "caseNumber": "CRIM-2024-100",
                    "caseType": "BAIL",
                    "currentStatus": "FILED"
                }
                """;

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'filingDate')].message").value("Filing date is required"));
    }

    @Test
    @DisplayName("Validation Failure 2: Blank case number returns 400 Bad Request")
    void testCreateCaseBlankCaseNumberFails() throws Exception {
        CreateCaseRequest req = CreateCaseRequest.builder()
                .caseNumber("   ")
                .caseType(CaseType.POCSO)
                .filingDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'caseNumber')].message").value("Case number is required"));
    }

    @Test
    @DisplayName("Validation Failure 3: Invalid case type returns 400 Bad Request")
    void testCreateCaseInvalidCaseTypeFails() throws Exception {
        String payload = """
                {
                    "caseNumber": "INV-2024-999",
                    "caseType": "TRAFFIC_FINE",
                    "filingDate": "2024-03-01"
                }
                """;

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Invalid case type provided")));
    }

    @Test
    @DisplayName("Validation Failure 4: Future filing date returns 400 Bad Request")
    void testCreateCaseFutureFilingDateFails() throws Exception {
        CreateCaseRequest req = CreateCaseRequest.builder()
                .caseNumber("FUT-2099-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().plusDays(10))
                .build();

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'filingDate')].message").value("Filing date cannot be in the future"));
    }

    @Test
    @DisplayName("Validation Failure 5: Duplicate case number returns 409 Conflict")
    void testDuplicateCaseNumberReturnsConflict() throws Exception {
        caseRepository.save(Case.builder()
                .caseNumber("DUP-2024-001")
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .build());

        CreateCaseRequest req = CreateCaseRequest.builder()
                .caseNumber("DUP-2024-001")
                .caseType(CaseType.MATRIMONIAL)
                .filingDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("Validation Failure 6: Circular linked-case reference is rejected with 400")
    void testCircularLinkedCaseReferenceRejected() throws Exception {
        // Case A links to nothing
        Case caseA = caseRepository.save(Case.builder()
                .caseNumber("CASE-A")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .build());

        // Case B links to Case A
        Case caseB = caseRepository.save(Case.builder()
                .caseNumber("CASE-B")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .linkedCase(caseA)
                .build());

        // Test 6a: Case A trying to link to itself
        UpdateCaseRequest selfLinkReq = UpdateCaseRequest.builder()
                .caseNumber("CASE-A")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .linkedCaseId(caseA.getId()) // self-link
                .build();

        mockMvc.perform(put("/api/v1/cases/{id}", caseA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfLinkReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("a case cannot link to itself")));

        // Test 6b: Cycle! Case A trying to link to Case B (A -> B -> A cycle)
        UpdateCaseRequest cycleReq = UpdateCaseRequest.builder()
                .caseNumber("CASE-A")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .linkedCaseId(caseB.getId()) // cycle!
                .build();

        mockMvc.perform(put("/api/v1/cases/{id}", caseA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cycleReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Circular linked-case reference detected")));
    }
}
