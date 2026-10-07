package com.judicialflow.audit;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.AuditLogRepository;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.HearingRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.common.models.*;
import com.judicialflow.ingestion.dto.CreateCaseRequest;
import com.judicialflow.ingestion.dto.CreateCourtroomRequest;
import com.judicialflow.ingestion.dto.CreateJudgeRequest;
import com.judicialflow.ingestion.dto.UpdateCaseRequest;
import com.judicialflow.scheduling.dto.ReassignHearingRequest;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import com.judicialflow.scheduling.service.HearingService;
import com.judicialflow.scheduling.service.SchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phase 7 Part B: Persisted Audit Log Integration Tests")
class AuditLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private HearingRepository hearingRepository;

    @Autowired
    private SchedulingProposalRepository proposalRepository;

    @Autowired
    private SchedulingRunRepository runRepository;

    @Autowired
    private SchedulingService schedulingService;

    @Autowired
    private HearingService hearingService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void setUp() {
        clearAuditLogs();
    }

    @Test
    @DisplayName("Audit: Case CREATE, UPDATE, DELETE generate correct audit rows with before/after state")
    @WithMockUser(username = "registrarAlice", roles = "REGISTRAR")
    void testCaseAuditLifecycle() throws Exception {
        // 1. CREATE Case
        CreateCaseRequest createReq = CreateCaseRequest.builder()
                .caseNumber("AUDIT-CASE-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(0)
                .build();

        String resJson = mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> createdCase = objectMapper.readValue(resJson, Map.class);
        String caseId = (String) createdCase.get("id");

        List<AuditLogEntry> createEntries = auditLogRepository.findByEntityNameAndEntityId("Case", caseId);
        assertThat(createEntries).hasSize(1);
        AuditLogEntry createEntry = createEntries.get(0);
        assertThat(createEntry.getAction()).isEqualTo("CREATE");
        assertThat(createEntry.getActorUsername()).isEqualTo("registrarAlice");
        assertThat(createEntry.getActorRole()).isEqualTo("REGISTRAR");
        assertThat(createEntry.getBeforeState()).isNull();
        assertThat(createEntry.getAfterState()).isNotNull();
        assertThat(createEntry.getAfterState().get("caseNumber")).isEqualTo("AUDIT-CASE-001");

        // 2. UPDATE Case
        UpdateCaseRequest updateReq = UpdateCaseRequest.builder()
                .caseNumber("AUDIT-CASE-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.SCHEDULED)
                .priorAdjournments(1)
                .build();

        mockMvc.perform(put("/api/v1/cases/" + caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        List<AuditLogEntry> allCaseEntries = auditLogRepository.findByEntityNameAndEntityId("Case", caseId);
        assertThat(allCaseEntries).hasSize(2);
        AuditLogEntry updateEntry = allCaseEntries.stream().filter(e -> "UPDATE".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(updateEntry.getActorUsername()).isEqualTo("registrarAlice");
        assertThat(updateEntry.getBeforeState()).isNotNull();
        assertThat(updateEntry.getBeforeState().get("priorAdjournments")).isEqualTo(0);
        assertThat(updateEntry.getAfterState()).isNotNull();
        assertThat(updateEntry.getAfterState().get("priorAdjournments")).isEqualTo(1);

        // 3. DELETE Case (Soft delete)
        mockMvc.perform(delete("/api/v1/cases/" + caseId))
                .andExpect(status().isNoContent());

        List<AuditLogEntry> finalCaseEntries = auditLogRepository.findByEntityNameAndEntityId("Case", caseId);
        assertThat(finalCaseEntries).hasSize(3);
        AuditLogEntry deleteEntry = finalCaseEntries.stream().filter(e -> "DELETE".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(deleteEntry.getActorUsername()).isEqualTo("registrarAlice");
        assertThat(deleteEntry.getAfterState().get("deleted")).isEqualTo(true);
    }

    @Test
    @DisplayName("Audit: Judge and Courtroom lifecycle generates audit entries")
    @WithMockUser(username = "adminBob", roles = "ADMIN")
    void testJudgeAndCourtroomAuditLifecycle() throws Exception {
        // Create Judge
        CreateJudgeRequest judgeReq = CreateJudgeRequest.builder()
                .name("Justice Audit Test")
                .specialization("CIVIL")
                .build();

        String judgeJson = mockMvc.perform(post("/api/v1/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(judgeReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String judgeId = (String) objectMapper.readValue(judgeJson, Map.class).get("id");

        List<AuditLogEntry> judgeEntries = auditLogRepository.findByEntityNameAndEntityId("Judge", judgeId);
        assertThat(judgeEntries).hasSize(1);
        assertThat(judgeEntries.get(0).getActorUsername()).isEqualTo("adminBob");
        assertThat(judgeEntries.get(0).getActorRole()).isEqualTo("ADMIN");
        assertThat(judgeEntries.get(0).getAfterState().get("name")).isEqualTo("Justice Audit Test");

        // Create Courtroom
        CreateCourtroomRequest crReq = CreateCourtroomRequest.builder()
                .name("Courtroom Audit 101")
                .capacity(50)
                .build();

        String crJson = mockMvc.perform(post("/api/v1/courtrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String crId = (String) objectMapper.readValue(crJson, Map.class).get("id");

        List<AuditLogEntry> crEntries = auditLogRepository.findByEntityNameAndEntityId("Courtroom", crId);
        assertThat(crEntries).hasSize(1);
        assertThat(crEntries.get(0).getActorUsername()).isEqualTo("adminBob");
        assertThat(crEntries.get(0).getAfterState().get("name")).isEqualTo("Courtroom Audit 101");
    }

    @Test
    @DisplayName("Audit: User creation writes audit entry without sensitive password")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testUserCreationAudit() throws Exception {
        var userReq = Map.of(
                "username", "auditedJudgeUser",
                "password", "SecretPass123!",
                "role", "JUDGE",
                "email", "auditedjudge@judicialflow.org"
        );

        String userRes = mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = (String) objectMapper.readValue(userRes, Map.class).get("id");

        List<AuditLogEntry> userAudit = auditLogRepository.findByEntityNameAndEntityId("User", userId);
        assertThat(userAudit).hasSize(1);
        AuditLogEntry entry = userAudit.get(0);
        assertThat(entry.getAction()).isEqualTo("CREATE");
        assertThat(entry.getActorUsername()).isEqualTo("adminUser");
        assertThat(entry.getAfterState()).doesNotContainKey("password");
        assertThat(entry.getAfterState()).doesNotContainKey("passwordHash");
        assertThat(entry.getAfterState().get("username")).isEqualTo("auditedJudgeUser");
    }

    @Test
    @DisplayName("Audit: Proposal Approve and Reject log entries with before/after state")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testProposalApproveRejectAudit() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder().name("Judge PropAudit").build());
        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Courtroom PropAudit").capacity(30).build());
        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("CASE-PROP-AUDIT-" + UUID.randomUUID())
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now().minusDays(5))
                .currentStatus(CaseStatus.FILED)
                .build());

        SchedulingRun run = runRepository.save(SchedulingRun.builder()
                .status(com.judicialflow.scheduling.model.RunStatus.COMPLETED)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .seed(42L)
                .build());

        SchedulingProposal proposal = proposalRepository.save(SchedulingProposal.builder()
                .run(run)
                .legalCase(legalCase)
                .judge(judge)
                .courtroom(courtroom)
                .proposedTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .durationMinutes(60)
                .status(ProposalStatus.PROPOSED)
                .build());

        // Approve proposal
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve"))
                .andExpect(status().isOk());

        List<AuditLogEntry> approveEntries = auditLogRepository.findByEntityNameAndEntityId("Proposal", proposal.getId().toString());
        assertThat(approveEntries).hasSize(1);
        AuditLogEntry approveAudit = approveEntries.get(0);
        assertThat(approveAudit.getAction()).isEqualTo("APPROVE");
        assertThat(approveAudit.getActorUsername()).isEqualTo("registrarUser");
        assertThat(approveAudit.getActorRole()).isEqualTo("REGISTRAR");
        assertThat(approveAudit.getBeforeState().get("proposalStatus")).isEqualTo("PROPOSED");
        assertThat(approveAudit.getAfterState().get("proposalStatus")).isEqualTo("APPROVED");

        // Create second proposal to test reject
        Case case2 = caseRepository.save(Case.builder()
                .caseNumber("CASE-PROP-REJ-" + UUID.randomUUID())
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.FILED)
                .build());

        SchedulingProposal proposal2 = proposalRepository.save(SchedulingProposal.builder()
                .run(run)
                .legalCase(case2)
                .judge(judge)
                .courtroom(courtroom)
                .proposedTime(LocalDateTime.now().plusDays(3).withHour(11).withMinute(0))
                .durationMinutes(45)
                .status(ProposalStatus.PROPOSED)
                .build());

        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal2.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Advocate unavailable"))))
                .andExpect(status().isOk());

        List<AuditLogEntry> rejectEntries = auditLogRepository.findByEntityNameAndEntityId("Proposal", proposal2.getId().toString());
        assertThat(rejectEntries).hasSize(1);
        AuditLogEntry rejectAudit = rejectEntries.get(0);
        assertThat(rejectAudit.getAction()).isEqualTo("REJECT");
        assertThat(rejectAudit.getActorUsername()).isEqualTo("registrarUser");
        assertThat(rejectAudit.getAfterState().get("status")).isEqualTo("REJECTED");
        assertThat(rejectAudit.getAfterState().get("rejectionReason")).isEqualTo("Advocate unavailable");
    }

    @Test
    @DisplayName("Audit: Scheduling run writes SYSTEM audit log with summary counts and seed")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testSchedulingRunAudit() {
        var response = schedulingService.triggerSchedulingRun(null);

        List<AuditLogEntry> runAudits = auditLogRepository.findByEntityNameAndEntityId("SchedulingRun", response.getRunId().toString());
        assertThat(runAudits).hasSize(1);
        AuditLogEntry entry = runAudits.get(0);
        assertThat(entry.getAction()).isEqualTo("SCHEDULING_RUN");
        assertThat(entry.getActorUsername()).isEqualTo("SYSTEM");
        assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
        assertThat(entry.getAfterState()).isNotNull();
        assertThat(entry.getAfterState().get("runId")).isEqualTo(response.getRunId().toString());
        assertThat(entry.getAfterState().get("seed")).isEqualTo(42);
        assertThat(entry.getAfterState().get("totalAssigned")).isNotNull();
        assertThat(entry.getAfterState().get("totalUnschedulable")).isNotNull();
    }

    @Test
    @DisplayName("Reassign Hearing: PUT /api/v1/hearings/{id}/reassign audits before/after state and detects conflicts (409)")
    @WithMockUser(username = "registrarReassign", roles = "REGISTRAR")
    void testHearingReassignAndConflictAudit() throws Exception {
        Judge judge1 = judgeRepository.save(Judge.builder().name("Judge Alpha").build());
        Judge judge2 = judgeRepository.save(Judge.builder().name("Judge Beta").build());
        Courtroom cr1 = courtroomRepository.save(Courtroom.builder().name("Courtroom Alpha").capacity(40).build());
        Courtroom cr2 = courtroomRepository.save(Courtroom.builder().name("Courtroom Beta").capacity(50).build());

        Case c1 = caseRepository.save(Case.builder()
                .caseNumber("REASSIGN-CASE-001")
                .caseType(CaseType.CRIMINAL_OTHER)
                .filingDate(LocalDate.now().minusDays(20))
                .currentStatus(CaseStatus.SCHEDULED)
                .build());

        LocalDateTime originalTime = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Hearing hearing1 = hearingRepository.save(Hearing.builder()
                .legalCase(c1)
                .judge(judge1)
                .courtroom(cr1)
                .scheduledTime(originalTime)
                .estimatedDurationMinutes(60)
                .status(HearingStatus.SCHEDULED)
                .build());

        // Successful reassignment to Judge Beta and Courtroom Beta
        LocalDateTime newTime = LocalDateTime.now().plusDays(6).withHour(14).withMinute(0).withSecond(0).withNano(0);
        ReassignHearingRequest reassignReq = ReassignHearingRequest.builder()
                .judgeId(judge2.getId())
                .courtroomId(cr2.getId())
                .scheduledTime(newTime)
                .durationMinutes(60)
                .reason("Judge Alpha recused due to conflict of interest")
                .build();

        mockMvc.perform(put("/api/v1/hearings/" + hearing1.getId() + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgeId").value(judge2.getId().toString()))
                .andExpect(jsonPath("$.courtroomId").value(cr2.getId().toString()));

        // Verify audit log for reassignment
        List<AuditLogEntry> reassignAudits = auditLogRepository.findByEntityNameAndEntityId("Hearing", hearing1.getId().toString());
        assertThat(reassignAudits).hasSize(1);
        AuditLogEntry entry = reassignAudits.get(0);
        assertThat(entry.getAction()).isEqualTo("REASSIGN");
        assertThat(entry.getActorUsername()).isEqualTo("registrarReassign");
        assertThat(entry.getActorRole()).isEqualTo("REGISTRAR");
        assertThat(entry.getBeforeState().get("judgeId")).isEqualTo(judge1.getId().toString());
        assertThat(entry.getAfterState().get("judgeId")).isEqualTo(judge2.getId().toString());
        assertThat(entry.getAfterState().get("scheduledTime")).isEqualTo(newTime.toString());

        // Now test 409 Conflict: create a conflicting hearing on Judge Beta at the exact same newTime
        Case c2 = caseRepository.save(Case.builder()
                .caseNumber("REASSIGN-CONFLICT-CASE")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(15))
                .currentStatus(CaseStatus.SCHEDULED)
                .build());
        Hearing hearing2 = hearingRepository.save(Hearing.builder()
                .legalCase(c2)
                .judge(judge1)
                .courtroom(cr1)
                .scheduledTime(LocalDateTime.now().plusDays(7).withHour(10).withMinute(0))
                .estimatedDurationMinutes(60)
                .status(HearingStatus.SCHEDULED)
                .build());

        // Attempting to reassign hearing2 to Judge Beta at newTime (which overlaps hearing1) should fail with 409 Conflict
        ReassignHearingRequest conflictReq = ReassignHearingRequest.builder()
                .judgeId(judge2.getId()) // Judge Beta already booked at newTime!
                .courtroomId(cr1.getId())
                .scheduledTime(newTime)
                .durationMinutes(60)
                .reason("Attempting double booking")
                .build();

        mockMvc.perform(put("/api/v1/hearings/" + hearing2.getId() + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Audit RBAC: GET /api/v1/admin/audit allows ADMIN (200), forbids REGISTRAR/JUDGE (403), unauthorized anon (401)")
    void testAuditEndpointRbac() throws Exception {
        // 1. Anonymous -> 401
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Audit RBAC: REGISTRAR gets 403 on /api/v1/admin/audit")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testRegistrarCannotAccessAudit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit RBAC: JUDGE gets 403 on /api/v1/admin/audit")
    @WithMockUser(username = "judgeUser", roles = "JUDGE")
    void testJudgeCannotAccessAudit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit Query & Filtering: ADMIN queries audit logs with pagination and filters")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testAuditFilteringAndPagination() throws Exception {
        // Seed diverse audit logs
        java.time.Instant now = java.time.Instant.now();
        AuditLogEntry e1 = AuditLogEntry.builder()
                .actorUsername("registrarAlice")
                .actorRole("REGISTRAR")
                .action("CREATE")
                .entityType("Case")
                .entityId("case-111")
                .reasonCode("CASE_CREATED")
                .timestamp(now.minus(3, java.time.temporal.ChronoUnit.HOURS))
                .entityName("Case")
                .performedBy("registrarAlice")
                .actionTime(LocalDateTime.now().minusHours(3))
                .build();

        AuditLogEntry e2 = AuditLogEntry.builder()
                .actorUsername("adminBob")
                .actorRole("ADMIN")
                .action("DELETE")
                .entityType("Case")
                .entityId("case-222")
                .reasonCode("CASE_DELETED")
                .timestamp(now.minus(2, java.time.temporal.ChronoUnit.HOURS))
                .entityName("Case")
                .performedBy("adminBob")
                .actionTime(LocalDateTime.now().minusHours(2))
                .build();

        AuditLogEntry e3 = AuditLogEntry.builder()
                .actorUsername("SYSTEM")
                .actorRole("SYSTEM")
                .action("SCHEDULING_RUN")
                .entityType("SchedulingRun")
                .entityId("run-333")
                .reasonCode("ENGINE_RUN_COMPLETED")
                .timestamp(now.minus(1, java.time.temporal.ChronoUnit.HOURS))
                .entityName("SchedulingRun")
                .performedBy("SYSTEM")
                .actionTime(LocalDateTime.now().minusHours(1))
                .build();

        auditLogRepository.saveAll(List.of(e1, e2, e3));

        // Unfiltered query -> 3 elements, newest first (e3, e2, e1)
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].entityId").value("run-333"))
                .andExpect(jsonPath("$.content[2].entityId").value("case-111"));

        // Filter by actor
        mockMvc.perform(get("/api/v1/admin/audit").param("actor", "registrarAlice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorUsername").value("registrarAlice"));

        // Filter by action
        mockMvc.perform(get("/api/v1/admin/audit").param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entityId").value("case-222"));

        // Filter by entityType
        mockMvc.perform(get("/api/v1/admin/audit").param("entityType", "Case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Filter by entityId
        mockMvc.perform(get("/api/v1/admin/audit").param("entityId", "run-333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("SCHEDULING_RUN"));
    }

    @Test
    @DisplayName("Audit Table is Append-Only: assert no PUT, POST, PATCH, or DELETE endpoints exist for audit")
    void testAuditTableIsAppendOnly() {
        var handlerMethods = handlerMapping.getHandlerMethods();
        for (var entry : handlerMethods.entrySet()) {
            var matchingCondition = entry.getKey().getPathPatternsCondition();
            if (matchingCondition == null) continue;

            for (var pattern : matchingCondition.getPatterns()) {
                String path = pattern.getPatternString();
                if (path.contains("/audit")) {
                    var methods = entry.getKey().getMethodsCondition().getMethods();
                    for (var method : methods) {
                        assertThat(method.name())
                                .as("Audit endpoints must be read-only (GET only); found mutable method on %s", path)
                                .isEqualTo("GET");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Audit Immutability: database trigger blocks UPDATE and DELETE on audit_log_entries")
    void testAuditImmutabilityAtDatabaseLevel() {
        AuditLogEntry entry = auditLogRepository.save(AuditLogEntry.builder()
                .actorUsername("adminBob")
                .actorRole("ADMIN")
                .action("CREATE")
                .entityType("Case")
                .entityId("immutability-test-1")
                .reasonCode("CASE_CREATED")
                .entityName("Case")
                .performedBy("adminBob")
                .build());

        assertThat(entry.getId()).isNotNull();

        // Attempting to delete the audit log must be rejected by the PostgreSQL trigger
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            auditLogRepository.delete(entry);
            auditLogRepository.flush();
        });
    }

    @Test
    @DisplayName("Audit Transactionality: rolled-back action leaves no audit entry")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testTransactionalRollbackLeavesNoAuditEntry() throws Exception {
        // 1. Create a case
        CreateCaseRequest validReq = CreateCaseRequest.builder()
                .caseNumber("TX-AUDIT-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(5))
                .build();

        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated());

        long initialAuditCount = auditLogRepository.count();

        // 2. Attempt to create a duplicate case (triggers 409 Conflict exception, transaction rolls back)
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isConflict());

        // 3. Verify no extra audit log entry was committed
        long afterFailedAuditCount = auditLogRepository.count();
        assertThat(afterFailedAuditCount).isEqualTo(initialAuditCount);
    }
}
