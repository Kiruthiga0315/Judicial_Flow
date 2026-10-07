package com.judicialflow.scheduling;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.HearingRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.Hearing;
import com.judicialflow.common.models.Judge;
import com.judicialflow.security.User;
import com.judicialflow.security.UserRepository;
import com.judicialflow.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Judge Scoping Integration Tests (Server-side Hearing Isolation)")
class JudgeHearingScopingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HearingRepository hearingRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Judge judgeA;
    private Judge judgeB;
    private Case caseA;
    private Case caseB;
    private Hearing hearingA;
    private Hearing hearingB;

    @BeforeEach
    void setUpData() {
        hearingRepository.deleteAll();

        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);

        judgeA = judgeRepository.save(Judge.builder().name("Judge Alpha " + suffix).specialization("CIVIL").build());
        judgeB = judgeRepository.save(Judge.builder().name("Judge Beta " + suffix).specialization("CRIMINAL").build());

        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Courtroom Scoping " + suffix).capacity(50).build());

        caseA = caseRepository.save(Case.builder()
                .caseNumber("SCOPE-A-" + suffix)
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.SCHEDULED)
                .assignedJudge(judgeA)
                .build());

        caseB = caseRepository.save(Case.builder()
                .caseNumber("SCOPE-B-" + suffix)
                .caseType(CaseType.CRIMINAL_OTHER)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.SCHEDULED)
                .assignedJudge(judgeB)
                .build());

        hearingA = hearingRepository.save(Hearing.builder()
                .legalCase(caseA)
                .judge(judgeA)
                .courtroom(courtroom)
                .scheduledTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .estimatedDurationMinutes(60)
                .status(HearingStatus.SCHEDULED)
                .build());

        hearingB = hearingRepository.save(Hearing.builder()
                .legalCase(caseB)
                .judge(judgeB)
                .courtroom(courtroom)
                .scheduledTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0))
                .estimatedDurationMinutes(60)
                .status(HearingStatus.SCHEDULED)
                .build());

        if (!userRepository.existsByUsername("judgeAUser")) {
            userRepository.save(User.builder()
                    .username("judgeAUser")
                    .passwordHash(passwordEncoder.encode("pass123"))
                    .role(UserRole.JUDGE)
                    .judgeId(judgeA.getId())
                    .enabled(true)
                    .build());
        } else {
            User u = userRepository.findByUsername("judgeAUser").get();
            u.setJudgeId(judgeA.getId());
            userRepository.save(u);
        }

        if (!userRepository.existsByUsername("judgeBUser")) {
            userRepository.save(User.builder()
                    .username("judgeBUser")
                    .passwordHash(passwordEncoder.encode("pass123"))
                    .role(UserRole.JUDGE)
                    .judgeId(judgeB.getId())
                    .enabled(true)
                    .build());
        } else {
            User u = userRepository.findByUsername("judgeBUser").get();
            u.setJudgeId(judgeB.getId());
            userRepository.save(u);
        }
    }

    @Test
    @DisplayName("Judge A sees only Judge A's hearings and cannot access Judge B's hearings")
    @WithMockUser(username = "judgeAUser", roles = "JUDGE")
    void testJudgeASeesOnlyOwnHearings() throws Exception {
        // Calling /api/v1/hearings automatically restricts to Judge A
        mockMvc.perform(get("/api/v1/hearings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].judgeId", is(judgeA.getId().toString())));

        // Calling /api/v1/hearings?judgeId=JudgeB gets 403 Forbidden
        mockMvc.perform(get("/api/v1/hearings").param("judgeId", judgeB.getId().toString()))
                .andExpect(status().isForbidden());

        // Calling /api/v1/hearings/cases/{caseA} succeeds
        mockMvc.perform(get("/api/v1/hearings/cases/" + caseA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber", is(caseA.getCaseNumber())));

        // Calling /api/v1/hearings/cases/{caseB} gets 403 Forbidden
        mockMvc.perform(get("/api/v1/hearings/cases/" + caseB.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Registrar sees all hearings across all judges")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testRegistrarSeesAllHearings() throws Exception {
        mockMvc.perform(get("/api/v1/hearings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/hearings/cases/" + caseB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber", is(caseB.getCaseNumber())));
    }

    @Test
    @DisplayName("Admin sees all hearings across all judges")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testAdminSeesAllHearings() throws Exception {
        mockMvc.perform(get("/api/v1/hearings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/hearings/cases/" + caseB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber", is(caseB.getCaseNumber())));
    }

    @Test
    @DisplayName("Judge with null judge_id gets empty list on /hearings and 403 on /hearings/cases/{id}")
    @WithMockUser(username = "unlinkedJudgeUser", roles = "JUDGE")
    void testJudgeWithNullJudgeIdFailClosed() throws Exception {
        if (!userRepository.existsByUsername("unlinkedJudgeUser")) {
            userRepository.save(User.builder()
                    .username("unlinkedJudgeUser")
                    .passwordHash(passwordEncoder.encode("pass123"))
                    .role(UserRole.JUDGE)
                    .judgeId(null)
                    .enabled(true)
                    .build());
        }

        // /hearings returns empty list, never all hearings
        mockMvc.perform(get("/api/v1/hearings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // /hearings/cases/{caseA} returns 403 Forbidden
        mockMvc.perform(get("/api/v1/hearings/cases/" + caseA.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Judge is forbidden (403) from accessing proposals and scheduling runs")
    @WithMockUser(username = "judgeAUser", roles = "JUDGE")
    void testJudgeForbiddenOnProposalsAndRuns() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/proposals/latest"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/scheduling/runs/latest"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dev judge linked to Judge record returns own assigned hearings on /hearings")
    @WithMockUser(username = "judge", roles = "JUDGE")
    void testSeededDevJudgeLinkedToJudgeEntityAndReturnsHearings() throws Exception {
        // Link judge user to judgeA
        User devJudgeUser = userRepository.findByUsername("judge").orElseGet(() ->
                User.builder()
                        .username("judge")
                        .passwordHash(passwordEncoder.encode("judge123"))
                        .role(UserRole.JUDGE)
                        .enabled(true)
                        .build());
        devJudgeUser.setJudgeId(judgeA.getId());
        userRepository.save(devJudgeUser);

        mockMvc.perform(get("/api/v1/hearings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].judgeId", is(judgeA.getId().toString())))
                .andExpect(jsonPath("$[0].caseNumber", is(caseA.getCaseNumber())));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownData() {
        hearingRepository.deleteAll();
    }
}
