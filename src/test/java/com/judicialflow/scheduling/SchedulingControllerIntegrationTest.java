package com.judicialflow.scheduling;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.JudgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the Phase 4 scheduling REST API.
 * Uses Testcontainers (via AbstractIntegrationTest) with real PostgreSQL.
 */
@Transactional
class SchedulingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String createJudge(String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"specialization\": \"Civil\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void setJudgeAvailability(String judgeId) throws Exception {
        String body = """
                {"availabilityWindows": [
                    {"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "17:00"}
                ]}""";
        mockMvc.perform(put("/api/judges/" + judgeId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String createCourtroom(String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/courtrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"capacity\": 50}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void setCourtroomAvailability(String courtroomId) throws Exception {
        String body = """
                {"availability": [
                    {"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "17:00"},
                    {"dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "17:00"}
                ]}""";
        mockMvc.perform(put("/api/courtrooms/" + courtroomId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String createCase(String caseNumber, String caseType) throws Exception {
        String body = String.format(
                "{\"caseNumber\": \"%s\", \"caseType\": \"%s\", \"filingDate\": \"2026-01-15\", \"currentStatus\": \"PENDING\"}",
                caseNumber, caseType);
        MvcResult res = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    void triggerSchedulingRun_withSeededData_returnsProposals() throws Exception {
        // Setup: 2 judges, 2 courtrooms, 3 pending cases
        String j1 = createJudge("Judge Alpha");
        String j2 = createJudge("Judge Beta");
        setJudgeAvailability(j1);
        setJudgeAvailability(j2);

        String c1 = createCourtroom("Court Room 1");
        String c2 = createCourtroom("Court Room 2");
        setCourtroomAvailability(c1);
        setCourtroomAvailability(c2);

        createCase("SCHED-001", "CIVIL");
        createCase("SCHED-002", "BAIL");
        createCase("SCHED-003", "POCSO");

        // Trigger scheduling run
        mockMvc.perform(post("/api/scheduling/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalAssigned").value(greaterThan(0)))
                .andExpect(jsonPath("$.proposals").isArray())
                .andExpect(jsonPath("$.proposals", hasSize(greaterThan(0))));
    }

    @Test
    void triggerSchedulingRun_noCases_returnsEmptyProposals() throws Exception {
        // Setup: judges and courtrooms but NO pending cases
        String j1 = createJudge("Judge Gamma");
        setJudgeAvailability(j1);
        String c1 = createCourtroom("Court Room 3");
        setCourtroomAvailability(c1);

        mockMvc.perform(post("/api/scheduling/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssigned").value(0))
                .andExpect(jsonPath("$.proposals", hasSize(0)));
    }

    @Test
    void manualOverride_createsHearingAndAudit() throws Exception {
        String judgeId = createJudge("Judge Override");
        setJudgeAvailability(judgeId);
        String courtroomId = createCourtroom("Court Room Override");
        setCourtroomAvailability(courtroomId);
        String caseId = createCase("OVERRIDE-001", "CIVIL");

        String body = String.format("""
                {
                    "caseId": "%s",
                    "judgeId": "%s",
                    "courtroomId": "%s",
                    "scheduledTime": "2026-09-21T10:00:00",
                    "durationMinutes": 60,
                    "reason": "Registrar requested specific judge",
                    "overriddenBy": "Registrar Kumar"
                }""", caseId, judgeId, courtroomId);

        mockMvc.perform(post("/api/scheduling/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber").value("OVERRIDE-001"))
                .andExpect(jsonPath("$.status").value("OVERRIDDEN"));
    }

    @Test
    void getSchedulingRun_afterRun_returnsDecisionLogs() throws Exception {
        // Setup
        String j1 = createJudge("Judge Lookup");
        setJudgeAvailability(j1);
        String c1 = createCourtroom("Court Room Lookup");
        setCourtroomAvailability(c1);
        createCase("LOOKUP-001", "BAIL");

        // Trigger run and extract runId
        MvcResult runResult = mockMvc.perform(post("/api/scheduling/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        String runId = objectMapper.readTree(runResult.getResponse().getContentAsString())
                .get("runId").asText();

        // Get the run by ID
        mockMvc.perform(get("/api/scheduling/runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.proposals[0].decisionLog").exists())
                .andExpect(jsonPath("$.proposals[0].decisionLog.explanation").isNotEmpty());
    }
}
