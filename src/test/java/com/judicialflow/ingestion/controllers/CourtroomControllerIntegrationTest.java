package com.judicialflow.ingestion.controllers;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.CourtroomAvailabilityWindow;
import com.judicialflow.ingestion.dto.CreateCourtroomRequest;
import com.judicialflow.ingestion.dto.SetCourtroomAvailabilityRequest;
import com.judicialflow.ingestion.dto.UpdateCourtroomRequest;
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
class CourtroomControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CourtroomRepository courtroomRepository;

    @BeforeEach
    void setUp() {
        courtroomRepository.deleteAll();
    }

    @Test
    @DisplayName("CRUD: Successfully create, retrieve, update, and list courtrooms")
    void testCourtroomCrudLifecycle() throws Exception {
        CourtroomAvailabilityWindow window = CourtroomAvailabilityWindow.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .notes("Standard court day")
                .build();

        CreateCourtroomRequest createReq = CreateCourtroomRequest.builder()
                .name("Court Hall 101")
                .capacity(75)
                .availability(List.of(window))
                .build();

        // 1. Create Courtroom
        String createResponse = mockMvc.perform(post("/api/v1/courtrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Court Hall 101"))
                .andExpect(jsonPath("$.capacity").value(75))
                .andExpect(jsonPath("$.availability", hasSize(1)))
                .andExpect(jsonPath("$.availability[0].dayOfWeek").value("MONDAY"))
                .andReturn().getResponse().getContentAsString();

        String courtroomIdStr = objectMapper.readTree(createResponse).get("id").asText();
        UUID courtroomId = UUID.fromString(courtroomIdStr);

        // 2. Get Courtroom by ID
        mockMvc.perform(get("/api/v1/courtrooms/{id}", courtroomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courtroomIdStr))
                .andExpect(jsonPath("$.name").value("Court Hall 101"));

        // 3. Update Courtroom
        UpdateCourtroomRequest updateReq = UpdateCourtroomRequest.builder()
                .name("Court Hall 101 (Renovated)")
                .capacity(90)
                .build();

        mockMvc.perform(put("/api/v1/courtrooms/{id}", courtroomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Court Hall 101 (Renovated)"))
                .andExpect(jsonPath("$.capacity").value(90));

        // 4. Set Capacity and Availability (JSON column in real PostgreSQL)
        CourtroomAvailabilityWindow window2 = CourtroomAvailabilityWindow.builder()
                .dayOfWeek(DayOfWeek.THURSDAY)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(16, 0))
                .notes("POCSO special hearing schedule")
                .build();

        SetCourtroomAvailabilityRequest setReq = SetCourtroomAvailabilityRequest.builder()
                .capacity(100)
                .availability(List.of(window, window2))
                .build();

        mockMvc.perform(put("/api/v1/courtrooms/{id}/availability", courtroomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(100))
                .andExpect(jsonPath("$.availability", hasSize(2)))
                .andExpect(jsonPath("$.availability[1].dayOfWeek").value("THURSDAY"));

        // 5. List Courtrooms with pagination
        mockMvc.perform(get("/api/v1/courtrooms?page=0&size=5&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Validation Failure 1: Blank courtroom name returns 400 Bad Request")
    void testCreateCourtroomWithBlankNameFails() throws Exception {
        CreateCourtroomRequest invalidReq = CreateCourtroomRequest.builder()
                .name("   ")
                .capacity(50)
                .build();

        mockMvc.perform(post("/api/v1/courtrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Courtroom name is required"));
    }

    @Test
    @DisplayName("Validation Failure 2: Non-positive capacity returns 400 Bad Request")
    void testCreateCourtroomWithNegativeCapacityFails() throws Exception {
        CreateCourtroomRequest invalidReq = CreateCourtroomRequest.builder()
                .name("Hall 202")
                .capacity(0) // must be at least 1
                .build();

        mockMvc.perform(post("/api/v1/courtrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'capacity')].message").value("Courtroom capacity must be at least 1"));
    }

    @Test
    @DisplayName("Validation Failure 3: Non-existent courtroom ID returns 404 Not Found")
    void testGetNonExistentCourtroomReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/courtrooms/{id}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Courtroom not found with id: " + nonExistentId)));
    }
}
