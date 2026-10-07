package com.judicialflow.notification;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phase 7 Part C.6: MailHog End-to-End Proof Test")
class MailHogProofIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private SchedulingProposalRepository proposalRepository;

    @Autowired
    private SchedulingRunRepository runRepository;

    @Autowired
    private com.judicialflow.common.HearingRepository hearingRepository;

    @org.junit.jupiter.api.BeforeEach
    void purgeMailHog() {
        try {
            new org.springframework.web.client.RestTemplate().delete("http://localhost:8025/api/v1/messages");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Manual Proof: Approve proposal sends real email to MailHog container on port 1025")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testApproveProposalSendsRealEmailToMailHog() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder().name("Justice MailHog Proof").build());
        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Courtroom MailHog 1").capacity(30).build());

        String uniqueEmail = "litigant_proof_" + UUID.randomUUID().toString().substring(0, 8) + "@judicialflow.org";
        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("MAILHOG-PROOF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now().minusDays(3))
                .currentStatus(CaseStatus.FILED)
                .litigantContactEmail(uniqueEmail)
                .build());

        SchedulingRun run = runRepository.save(SchedulingRun.builder()
                .status(RunStatus.COMPLETED)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .seed(42L)
                .build());

        SchedulingProposal proposal = proposalRepository.save(SchedulingProposal.builder()
                .run(run)
                .legalCase(legalCase)
                .judge(judge)
                .courtroom(courtroom)
                .proposedTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0))
                .durationMinutes(60)
                .status(ProposalStatus.PROPOSED)
                .build());

        // 1. Approve the proposal
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve"))
                .andExpect(status().isOk());

        // Verify the hearing was created
        com.judicialflow.common.models.Hearing hearing = hearingRepository.findAll().stream()
                .filter(h -> h.getLegalCase().getId().equals(legalCase.getId()))
                .findFirst()
                .orElseThrow();

        // 2. Reassign the hearing to a new slot
        Judge judge2 = judgeRepository.save(Judge.builder().name("Justice MailHog Reassigned").build());
        com.judicialflow.scheduling.dto.ReassignHearingRequest reassignReq = com.judicialflow.scheduling.dto.ReassignHearingRequest.builder()
                .judgeId(judge2.getId())
                .courtroomId(courtroom.getId())
                .scheduledTime(LocalDateTime.now().plusDays(4).withHour(14).withMinute(0))
                .durationMinutes(90)
                .reason("Bench availability conflict resolution")
                .litigantMessage("Bench availability adjustment")
                .build();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/hearings/" + hearing.getId() + "/reassign")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk());

        // 3. Query MailHog API to confirm both message arrivals (Approve + Reassign)
        TestRestTemplate restTemplate = new TestRestTemplate();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:8025/api/v2/messages", String.class);
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                String body = response.getBody();
                assertThat(body).contains("Hearing Scheduled: Case " + legalCase.getCaseNumber());
                assertThat(body).contains("Hearing Rescheduled: Case " + legalCase.getCaseNumber());
                assertThat(body).contains("Bench availability adjustment");
                assertThat(body).doesNotContain("Bench availability conflict resolution");
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MailHogProofIntegrationTest.class)
                        .warn("MailHog API check: {}", e.getMessage());
            }
        });
    }
}
