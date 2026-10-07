package com.judicialflow.ingestion.config;

import com.judicialflow.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@DisplayName("OpenAPI Dev Profile Access Tests")
class OpenApiDevProfileIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("OpenAPI: /v3/api-docs is publicly accessible without authentication in dev profile")
    void testOpenApiDocsAccessibleInDevProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger UI: /swagger-ui/index.html is publicly accessible without authentication in dev profile")
    void testSwaggerUiAccessibleInDevProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
