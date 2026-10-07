package com.judicialflow.security;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.Judge;
import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.model.RunStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Role-by-Route Access Matrix Integration Test")
class RoleRouteMatrixIntegrationTest extends AbstractIntegrationTest {

    private static final String RANDOM_ID = UUID.randomUUID().toString();

    @Autowired
    private SchedulingRunRepository runRepository;

    @Autowired
    private SchedulingProposalRepository proposalRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SchedulingRun fixtureRun;
    private Judge fixtureJudge;

    @BeforeEach
    void setUpFixtures() {
        fixtureJudge = judgeRepository.save(Judge.builder()
                .name("Matrix Presiding Judge")
                .specialization("CIVIL")
                .build());

        Courtroom courtroom = courtroomRepository.save(Courtroom.builder()
                .name("Matrix Courtroom")
                .capacity(40)
                .build());

        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("MATRIX-CASE-" + UUID.randomUUID().toString().substring(0, 8))
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(5))
                .currentStatus(CaseStatus.FILED)
                .assignedJudge(fixtureJudge)
                .assignedCourtroom(courtroom)
                .build());

        fixtureRun = runRepository.save(SchedulingRun.builder()
                .status(RunStatus.COMPLETED)
                .horizonDays(14)
                .defaultDurationMinutes(60)
                .seed(42L)
                .triggeredAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .totalCasesInput(1)
                .totalAssigned(1)
                .totalUnschedulable(0)
                .build());

        proposalRepository.save(SchedulingProposal.builder()
                .run(fixtureRun)
                .legalCase(legalCase)
                .judge(fixtureJudge)
                .courtroom(courtroom)
                .proposedTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .durationMinutes(60)
                .status(ProposalStatus.PROPOSED)
                .build());

        // Ensure judgeUser exists and is linked to fixtureJudge
        if (!userRepository.existsByUsername("judgeUser")) {
            userRepository.save(User.builder()
                    .username("judgeUser")
                    .passwordHash(passwordEncoder.encode("pass123"))
                    .role(UserRole.JUDGE)
                    .judgeId(fixtureJudge.getId())
                    .enabled(true)
                    .build());
        } else {
            User u = userRepository.findByUsername("judgeUser").get();
            u.setJudgeId(fixtureJudge.getId());
            userRepository.save(u);
        }
    }

    // -------------------------------------------------------------
    // Anonymous matrix assertions: protected routes -> 401, public -> 200
    // -------------------------------------------------------------
    @Test
    @DisplayName("Matrix: Anonymous user cannot access protected endpoints (401)")
    void testAnonymousAccessMatrix() throws Exception {
        // Public endpoints
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());

        // Protected endpoints return 401
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/cases")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/cases").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/cases/" + RANDOM_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/cases/" + RANDOM_ID).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/cases/" + RANDOM_ID)).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/judges")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/judges").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/courtrooms")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/courtrooms").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/priority/cases/top")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/scheduling/runs/" + RANDOM_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/scheduling/run").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + RANDOM_ID + "/approve")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + RANDOM_ID + "/reject").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/estimates/cases/" + RANDOM_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/estimates/train")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/batch/reschedule")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/batch/status/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/batch/diff/" + RANDOM_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cases/aging-report")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hearings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hearings/cases/" + RANDOM_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/hearings/" + RANDOM_ID + "/reassign").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/priority/cases/" + RANDOM_ID + "/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/scheduling/runs/latest")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/scheduling/proposals/latest")).andExpect(status().isUnauthorized());

        // Simulation endpoints return 401 for anonymous
        mockMvc.perform(post("/api/v1/admin/simulation/run")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/simulation/" + RANDOM_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/simulation/report")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------
    // JUDGE matrix assertions: Read-only on judicial resources (200),
    // Forbidden (403) on writes, admin operations, and scheduling proposals/runs
    // -------------------------------------------------------------
    @Test
    @DisplayName("Matrix: JUDGE role - 200 on reads, 403 on writes, admin, and proposals/runs")
    @WithMockUser(username = "judgeUser", roles = "JUDGE")
    void testJudgeRoleMatrix() throws Exception {
        // Reads allowed (200 OK or 404 Not Found for non-existent IDs, but NEVER 401 or 403)
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/aging-report")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/judges")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courtrooms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hearings")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hearings/cases/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/priority/cases/top")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/priority/cases/" + RANDOM_ID + "/history")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/estimates/cases/" + RANDOM_ID)).andExpect(status().isNotFound());

        // Scheduling proposals and runs FORBIDDEN for JUDGE (403)
        mockMvc.perform(get("/api/v1/scheduling/runs/" + RANDOM_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/scheduling/runs/latest")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/scheduling/proposals/latest")).andExpect(status().isForbidden());

        // Writes FORBIDDEN (403)
        mockMvc.perform(post("/api/v1/cases").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/cases/" + RANDOM_ID).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/cases/" + RANDOM_ID)).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/judges").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/judges/" + RANDOM_ID).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courtrooms").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/courtrooms/" + RANDOM_ID).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/scheduling/run").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + RANDOM_ID + "/approve")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + RANDOM_ID + "/reject").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/hearings/" + RANDOM_ID + "/reassign").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/estimates/train")).andExpect(status().isForbidden());

        // Admin endpoints FORBIDDEN (403)
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/batch/status/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/batch/diff/" + RANDOM_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/simulation/run")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/" + RANDOM_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/report")).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------
    // REGISTRAR matrix assertions: Read and Write on operational resources,
    // Forbidden (403) on admin management endpoints
    // -------------------------------------------------------------
    @Test
    @DisplayName("Matrix: REGISTRAR role - allowed on operations, 403 on admin")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testRegistrarRoleMatrix() throws Exception {
        // Reads allowed (200 OK or 404 for non-existent IDs)
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/aging-report")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/judges")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courtrooms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hearings")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hearings/cases/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/priority/cases/top")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/priority/cases/" + RANDOM_ID + "/history")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scheduling/runs/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scheduling/runs/latest")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/scheduling/proposals/latest")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/estimates/cases/" + RANDOM_ID)).andExpect(status().isNotFound());

        // Operational writes allowed (NOT 401 or 403; could be 404/400 due to dummy payload/id)
        mockMvc.perform(get("/api/v1/cases/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/cases/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + RANDOM_ID + "/approve")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/hearings/" + RANDOM_ID + "/reassign").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/estimates/train")).andExpect(status().isOk());

        // Admin endpoints FORBIDDEN (403)
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/batch/status/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/batch/diff/" + RANDOM_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/simulation/run")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/" + RANDOM_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/simulation/report")).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------
    // ADMIN matrix assertions: Full access (reads, writes, admin)
    // -------------------------------------------------------------
    @Test
    @DisplayName("Matrix: ADMIN role - full access (2xx / not 401/403)")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testAdminRoleMatrix() throws Exception {
        // Operational reads allowed
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/aging-report")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/judges")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courtrooms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hearings")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hearings/cases/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/priority/cases/top")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/priority/cases/" + RANDOM_ID + "/history")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scheduling/runs/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/scheduling/runs/latest")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/scheduling/proposals/latest")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/estimates/cases/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/estimates/train")).andExpect(status().isOk());

        // Operational writes allowed
        mockMvc.perform(put("/api/v1/hearings/" + RANDOM_ID + "/reassign").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        // Admin endpoints allowed (200 OK)
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/batch/reschedule").param("triggerSource", "ADMIN_MATRIX_TEST"))
                .andExpect(status().isOk());

        // Simulation endpoints allowed for ADMIN (run returns 202 ACCEPTED, status and report allowed)
        mockMvc.perform(post("/api/v1/admin/simulation/run?quick=true")).andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/admin/simulation/" + RANDOM_ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/simulation/status")).andExpect(status().isOk());
    }
}
