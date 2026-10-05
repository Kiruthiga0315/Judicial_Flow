package com.judicialflow.ingestion.config;

import com.judicialflow.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("OpenAPI: /v3/api-docs is available and exposes valid OpenAPI schema with tags")
    void testOpenApiDocsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.0")))
                .andExpect(jsonPath("$.info.title").value("JudicialFlow Case Ingestion & Decision Support API"))
                .andExpect(jsonPath("$.paths['/api/cases']").exists())
                .andExpect(jsonPath("$.paths['/api/judges']").exists())
                .andExpect(jsonPath("$.paths['/api/courtrooms']").exists())
                .andExpect(jsonPath("$.components.schemas.CaseResponse").exists())
                .andExpect(jsonPath("$.components.schemas.JudgeResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CourtroomResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
    }

    @Test
    @DisplayName("Swagger UI: /swagger-ui/index.html is accessible without authentication")
    void testSwaggerUiAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
