package com.judicialflow.ingestion.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.ingestion.dto.RegisterCaseRequest;
import com.judicialflow.notification.repository.NotificationLogRepository;
import com.judicialflow.common.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseRegistrationIntegrationTest extends com.judicialflow.AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        caseRepository.deleteAll();
        auditLogRepository.deleteAll();
        notificationLogRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "REGISTRAR")
    void validRegistration_createsCaseWithDefaultsAndScoreAndAudit() throws Exception {
        RegisterCaseRequest request = RegisterCaseRequest.builder()
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now().minusDays(5))
                .litigantContactEmail("test@example.com")
                .build();

        mockMvc.perform(post("/api/v1/cases/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseDetails.caseNumber").exists())
                .andExpect(jsonPath("$.caseDetails.currentStatus").value("FILED"))
                .andExpect(jsonPath("$.caseDetails.priorAdjournments").value(0))
                .andExpect(jsonPath("$.initialScore").isNumber())
                .andExpect(jsonPath("$.scoreBreakdown").isArray());

        List<Case> cases = caseRepository.findAll();
        assertThat(cases).hasSize(1);
        Case saved = cases.get(0);
        assertThat(saved.getCaseNumber()).startsWith("BAIL/");
        
        assertThat(auditLogRepository.findAll()).hasSize(1);
        assertThat(notificationLogRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void roleCheck_judgeGets403() throws Exception {
        RegisterCaseRequest request = RegisterCaseRequest.builder()
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/cases/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REGISTRAR")
    void invalidInputs_returns400() throws Exception {
        RegisterCaseRequest request = RegisterCaseRequest.builder()
                .caseType(null)
                .filingDate(LocalDate.now().plusDays(1)) // future
                .litigantContactEmail("not-an-email")
                .build();

        mockMvc.perform(post("/api/v1/cases/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
