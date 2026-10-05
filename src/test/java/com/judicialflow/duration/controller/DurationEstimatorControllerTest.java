package com.judicialflow.duration.controller;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.duration.dto.DurationEstimateResponse;
import com.judicialflow.duration.service.DurationEstimatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DurationEstimatorController.class)
@AutoConfigureMockMvc(addFilters = false)
class DurationEstimatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DurationEstimatorService estimatorService;

    @MockBean
    private CaseRepository caseRepository;

    @Test
    void testGetEstimateForCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        Case mockCase = new Case();
        mockCase.setId(caseId);
        mockCase.setCaseType(CaseType.CIVIL);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(mockCase));

        DurationEstimateResponse response = DurationEstimateResponse.builder()
                .predictedDurationDays(120)
                .minDurationDays(100)
                .maxDurationDays(140)
                .basis("based on 10 similar cases")
                .build();

        when(estimatorService.estimateForCase(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/estimates/cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictedDurationDays").value(120))
                .andExpect(jsonPath("$.basis").value("based on 10 similar cases"));
    }

    @Test
    void testGetEstimate_NotFound() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/estimates/cases/" + caseId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testForceRetrain() throws Exception {
        mockMvc.perform(post("/api/v1/estimates/train"))
                .andExpect(status().isOk())
                .andExpect(content().string("Models retrained successfully"));
    }
}
