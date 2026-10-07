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
    @DisplayName("OpenAPI: /v3/api-docs requires authentication when not in dev profile (returns 401)")
    void testOpenApiDocsUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger UI: /swagger-ui/index.html requires authentication when not in dev profile (returns 401)")
    void testSwaggerUiUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OpenAPI: /v3/api-docs is available and exposes valid schema when authenticated")
    @org.springframework.security.test.context.support.WithMockUser(username = "adminUser", roles = "ADMIN")
    void testOpenApiDocsAvailableWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.0")))
                .andExpect(jsonPath("$.info.title").value("JudicialFlow Case Ingestion & Decision Support API"))
                .andExpect(jsonPath("$.paths['/api/v1/cases']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/judges']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/courtrooms']").exists())
                .andExpect(jsonPath("$.components.schemas.CaseResponse").exists())
                .andExpect(jsonPath("$.components.schemas.JudgeResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CourtroomResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
    }

    @Test
    @DisplayName("Swagger UI: /swagger-ui/index.html is accessible when authenticated")
    @org.springframework.security.test.context.support.WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testSwaggerUiAccessibleWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
