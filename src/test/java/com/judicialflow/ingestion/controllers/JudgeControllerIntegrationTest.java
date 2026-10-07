package com.judicialflow.ingestion.controllers;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.models.Judge;
import com.judicialflow.common.models.JudgeAvailabilityWindow;
import com.judicialflow.ingestion.dto.CreateJudgeRequest;
import com.judicialflow.ingestion.dto.SetJudgeAvailabilityRequest;
import com.judicialflow.ingestion.dto.UpdateJudgeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser(roles = "REGISTRAR")
class JudgeControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private com.judicialflow.common.HearingRepository hearingRepository;

    @Autowired
    private com.judicialflow.common.CaseRepository caseRepository;

    @BeforeEach
    void setUp() {
        hearingRepository.deleteAll();
        caseRepository.deleteAll();
        judgeRepository.deleteAll();
    }

    @Test
    @DisplayName("CRUD: Successfully create, retrieve, update, and list judges")
    void testJudgeCrudLifecycle() throws Exception {
        JudgeAvailabilityWindow window = JudgeAvailabilityWindow.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(16, 30))
                .notes("Morning civil hearings")
                .build();

        CreateJudgeRequest createReq = CreateJudgeRequest.builder()
                .name("Hon. Justice A. K. Sikri")
                .specialization("Civil & Commercial Law")
                .availabilityWindows(List.of(window))
                .build();

        // 1. Create Judge
        String createResponse = mockMvc.perform(post("/api/v1/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Hon. Justice A. K. Sikri"))
                .andExpect(jsonPath("$.specialization").value("Civil & Commercial Law"))
                .andExpect(jsonPath("$.availabilityWindows", hasSize(1)))
                .andExpect(jsonPath("$.availabilityWindows[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.availabilityWindows[0].startTime").value("09:30"))
                .andReturn().getResponse().getContentAsString();

        String judgeIdStr = objectMapper.readTree(createResponse).get("id").asText();
        UUID judgeId = UUID.fromString(judgeIdStr);

        // 2. Get Judge by ID
        mockMvc.perform(get("/api/v1/judges/{id}", judgeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(judgeIdStr))
                .andExpect(jsonPath("$.name").value("Hon. Justice A. K. Sikri"));

        // 3. Update Judge
        UpdateJudgeRequest updateReq = UpdateJudgeRequest.builder()
                .name("Hon. Justice A. K. Sikri (Senior Division)")
                .specialization("Constitutional Bench")
                .build();

        mockMvc.perform(put("/api/v1/judges/{id}", judgeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hon. Justice A. K. Sikri (Senior Division)"))
                .andExpect(jsonPath("$.specialization").value("Constitutional Bench"));

        // 4. Set Availability Windows (Testing JSON column persistence in real PostgreSQL)
        JudgeAvailabilityWindow window2 = JudgeAvailabilityWindow.builder()
                .dayOfWeek(DayOfWeek.WEDNESDAY)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(15, 0))
                .notes("Bail applications bench")
                .build();

        SetJudgeAvailabilityRequest availReq = SetJudgeAvailabilityRequest.builder()
                .availabilityWindows(List.of(window, window2))
                .build();

        mockMvc.perform(put("/api/v1/judges/{id}/availability", judgeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(availReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityWindows", hasSize(2)))
                .andExpect(jsonPath("$.availabilityWindows[1].dayOfWeek").value("WEDNESDAY"));

        // 5. List Judges with pagination
        mockMvc.perform(get("/api/v1/judges?page=0&size=10&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("Validation Failure 1: Blank judge name returns 400 Bad Request")
    void testCreateJudgeWithBlankNameFails() throws Exception {
        CreateJudgeRequest invalidReq = CreateJudgeRequest.builder()
                .name("   ")
                .specialization("Criminal")
                .build();

        mockMvc.perform(post("/api/v1/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Judge name is required"));
    }

    @Test
    @DisplayName("Validation Failure 2: Availability window startTime after endTime returns 400")
    void testJudgeAvailabilityStartTimeAfterEndTimeFails() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder()
                .name("Hon. Justice D. Y. Chandrachud")
                .specialization("Civil")
                .build());

        JudgeAvailabilityWindow invalidWindow = JudgeAvailabilityWindow.builder()
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(9, 0)) // End time before start time!
                .build();

        SetJudgeAvailabilityRequest request = SetJudgeAvailabilityRequest.builder()
                .availabilityWindows(List.of(invalidWindow))
                .build();

        mockMvc.perform(put("/api/v1/judges/{id}/availability", judge.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("startTime (17:00) must be before endTime (09:00)")));
    }

    @Test
    @DisplayName("Validation Failure 3: Non-existent judge ID returns 404 Not Found")
    void testGetNonExistentJudgeReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/judges/{id}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Judge not found with id: " + nonExistentId)));
    }
}
