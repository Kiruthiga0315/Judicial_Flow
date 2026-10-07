package com.judicialflow.security;

import com.judicialflow.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Security and Role-Based Access Control Integration Tests")
class SecurityRbacIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("1. No credentials on protected admin endpoint returns 401 Unauthorized")
    void testNoCredentialsReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. REGISTRAR user on admin endpoint returns 403 Forbidden")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testRegistrarOnAdminEndpointReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. ADMIN user on admin endpoint returns 200 OK")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testAdminOnAdminEndpointReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .param("triggerSource", "RBAC_TEST")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("4. Unauthenticated call to case endpoint returns 401 Unauthorized")
    void testUnauthenticatedCallToCaseEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. JUDGE attempting a write operation returns 403 Forbidden")
    @WithMockUser(username = "judgeUser", roles = "JUDGE")
    void testJudgeAttemptingWriteReturns403() throws Exception {
        String casePayload = """
                {
                    "caseNumber": "TEST-JUDGE-WRITE-001",
                    "caseType": "CIVIL",
                    "filingDate": "2026-01-01"
                }
                """;
        mockMvc.perform(post("/api/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(casePayload))
                .andExpect(status().isForbidden());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("6. Real DB-seeded user with HTTP Basic authenticates successfully via BCrypt")
    void testRealDbUserHttpBasicAuthenticationSuccess() throws Exception {
        userRepository.findByUsername("dbAdmin").ifPresent(userRepository::delete);
        User testUser = User.builder()
                .username("dbAdmin")
                .passwordHash(passwordEncoder.encode("secretAdminPass123!"))
                .role(UserRole.ADMIN)
                .email("dbadmin@judicialflow.org")
                .enabled(true)
                .build();
        userRepository.save(testUser);

        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("dbAdmin", "secretAdminPass123!"))
                        .param("triggerSource", "DB_BASIC_AUTH_TEST")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7. Real DB-seeded user with wrong password returns 401 Unauthorized")
    void testRealDbUserWrongPasswordReturns401() throws Exception {
        userRepository.findByUsername("dbRegistrar").ifPresent(userRepository::delete);
        User testUser = User.builder()
                .username("dbRegistrar")
                .passwordHash(passwordEncoder.encode("secretRegPass123!"))
                .role(UserRole.REGISTRAR)
                .email("dbregistrar@judicialflow.org")
                .enabled(true)
                .build();
        userRepository.save(testUser);

        mockMvc.perform(get("/api/v1/cases")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("dbRegistrar", "wrongPassword!"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. JUDGE attempting to approve a proposal returns 403 Forbidden")
    @WithMockUser(username = "judgeUser", roles = "JUDGE")
    void testJudgeAttemptingApproveProposalReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + java.util.UUID.randomUUID() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Route-sweep: enumerate all mapped /api/** endpoints and assert unauthenticated -> 401 (allow-listing public routes)")
    void testRouteSweepUnauthenticatedEndpointsReturn401() throws Exception {
        java.util.Set<String> publicAllowList = java.util.Set.of(
                "/api/v1/auth/login",
                "/actuator/health"
        );

        var handlerMethods = handlerMapping.getHandlerMethods();
        for (var entry : handlerMethods.entrySet()) {
            var matchingCondition = entry.getKey().getPathPatternsCondition();
            if (matchingCondition == null) continue;

            for (var pattern : matchingCondition.getPatterns()) {
                String path = pattern.getPatternString();
                if (path.startsWith("/api/")) {
                    if (publicAllowList.contains(path)) {
                        continue;
                    }

                    // Replace path variables {id}, {proposalId}, etc. with random UUID for testing
                    String testPath = path.replaceAll("\\{[^}]+\\}", java.util.UUID.randomUUID().toString());

                    var methods = entry.getKey().getMethodsCondition().getMethods();
                    if (methods.isEmpty()) {
                        mockMvc.perform(get(testPath))
                                .andExpect(status().isUnauthorized());
                    } else {
                        for (var httpMethod : methods) {
                            switch (httpMethod.name()) {
                                case "GET" -> mockMvc.perform(get(testPath)).andExpect(status().isUnauthorized());
                                case "POST" -> mockMvc.perform(post(testPath).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
                                case "PUT" -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(testPath).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
                                case "DELETE" -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(testPath)).andExpect(status().isUnauthorized());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("10. /api/v1/auth/me unauthenticated returns 401 Unauthorized")
    void testAuthMeUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. /api/v1/auth/me authenticated returns 200 OK with user info")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testAuthMeAuthenticatedReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.authenticated").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.username").value("registrarUser"));
    }

    @Test
    @DisplayName("12. /api/v1/auth/login is public and returns 200 OK")
    void testAuthLoginPublicReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("13. Simulation admin endpoints RBAC: 202/200 for ADMIN, 403 for REGISTRAR and JUDGE, 401 for anonymous")
    void testSimulationAdminEndpointsRbac() throws Exception {
        // 1. Anonymous -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/admin/simulation/run"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/simulation/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/simulation/report"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/simulation/test-run-id"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/simulation/test-run-id/report"))
                .andExpect(status().isUnauthorized());

        // 2. REGISTRAR -> 403 Forbidden
        mockMvc.perform(post("/api/v1/admin/simulation/run")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("regUser").roles("REGISTRAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("regUser").roles("REGISTRAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/report")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("regUser").roles("REGISTRAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/test-run-id")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("regUser").roles("REGISTRAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/test-run-id/report")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("regUser").roles("REGISTRAR")))
                .andExpect(status().isForbidden());

        // 3. JUDGE -> 403 Forbidden
        mockMvc.perform(post("/api/v1/admin/simulation/run")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("judgeUser").roles("JUDGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("judgeUser").roles("JUDGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/report")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("judgeUser").roles("JUDGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/test-run-id")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("judgeUser").roles("JUDGE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/test-run-id/report")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("judgeUser").roles("JUDGE")))
                .andExpect(status().isForbidden());

        // 4. ADMIN -> 202 for run, 200 for status, 200 for report, 200/404 for {id} (access allowed)
        var runAction = mockMvc.perform(post("/api/v1/admin/simulation/run")
                        .param("quick", "true")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("adminUser").roles("ADMIN")))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("ACCEPTED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.runId").exists())
                .andReturn();

        String responseBody = runAction.getResponse().getContentAsString();
        String runId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.runId");

        mockMvc.perform(get("/api/v1/admin/simulation/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("adminUser").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/simulation/report")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("adminUser").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/simulation/" + runId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("adminUser").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.runId").value(runId));
    }
}
