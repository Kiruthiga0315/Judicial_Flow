package com.judicialflow.notification;

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
import com.judicialflow.notification.model.NotificationLog;
import com.judicialflow.notification.repository.NotificationLogRepository;
import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import com.judicialflow.scheduling.dto.ReassignHearingRequest;
import com.judicialflow.scheduling.event.NightlyRunCompletedEvent;
import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.model.RunStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import com.judicialflow.security.UserRepository;
import com.judicialflow.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phase 7 Part C: Email Notifications & Audit Log Integration Tests")
class NotificationEmailIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private HearingRepository hearingRepository;

    @Autowired
    private SchedulingProposalRepository proposalRepository;

    @Autowired
    private SchedulingRunRepository runRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
        reset(mailSender);
    }

    @Test
    @DisplayName("Approve proposal sends scheduled email to litigantContactEmail and logs to notification_log")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testApproveProposalSendsScheduledEmail() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder().name("Justice EmailTest").build());
        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Courtroom Email 101").capacity(40).build());

        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("MAIL-SCHED-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(10))
                .currentStatus(CaseStatus.FILED)
                .litigantContactEmail("litigant1@example.com")
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

        // Approve proposal
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve"))
                .andExpect(status().isOk());

        // Await async email processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender, atLeastOnce()).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getTo()).contains("litigant1@example.com");
            assertThat(sent.getSubject()).contains("Hearing Scheduled: Case MAIL-SCHED-001");
            assertThat(sent.getText()).contains("Justice EmailTest");
            assertThat(sent.getText()).contains("Courtroom Email 101");

            List<NotificationLog> logs = notificationLogRepository.findByRecipient("litigant1@example.com");
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getStatus()).isEqualTo("SENT");
            assertThat(logs.get(0).getNotificationType()).isEqualTo("HEARING_SCHEDULED");
            assertThat(logs.get(0).getCaseId()).isEqualTo(legalCase.getId());
        });
    }

    @Test
    @DisplayName("Reassign hearing sends rescheduled email including previous slot and reason")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testReassignHearingSendsRescheduledEmail() throws Exception {
        Judge judge1 = judgeRepository.save(Judge.builder().name("Judge Prev").build());
        Judge judge2 = judgeRepository.save(Judge.builder().name("Judge Next").build());
        Courtroom cr1 = courtroomRepository.save(Courtroom.builder().name("Room Prev").capacity(30).build());
        Courtroom cr2 = courtroomRepository.save(Courtroom.builder().name("Room Next").capacity(30).build());

        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("MAIL-RESCHED-002")
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now().minusDays(5))
                .currentStatus(CaseStatus.SCHEDULED)
                .litigantContactEmail("litigant2@example.com")
                .build());

        LocalDateTime prevSlot = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0);
        LocalDateTime newSlot = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0);

        Hearing hearing = hearingRepository.save(Hearing.builder()
                .legalCase(legalCase)
                .judge(judge1)
                .courtroom(cr1)
                .scheduledTime(prevSlot)
                .estimatedDurationMinutes(45)
                .status(HearingStatus.SCHEDULED)
                .build());

        ReassignHearingRequest reassignReq = ReassignHearingRequest.builder()
                .judgeId(judge2.getId())
                .courtroomId(cr2.getId())
                .scheduledTime(newSlot)
                .durationMinutes(45)
                .reason("Witness unavailability on previous date")
                .litigantMessage("Administrative roster update")
                .build();

        mockMvc.perform(put("/api/v1/hearings/" + hearing.getId() + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender, atLeastOnce()).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getTo()).contains("litigant2@example.com");
            assertThat(sent.getSubject()).contains("Hearing Rescheduled: Case MAIL-RESCHED-002");
            assertThat(sent.getText()).contains("Judge Next");
            assertThat(sent.getText()).contains("Room Next");
            assertThat(sent.getText()).contains("Administrative roster update");
            // Must NOT expose the registrar's internal free-text reason to litigant
            assertThat(sent.getText()).doesNotContain("Witness unavailability on previous date");

            List<NotificationLog> logs = notificationLogRepository.findByRecipient("litigant2@example.com");
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getStatus()).isEqualTo("SENT");
            assertThat(logs.get(0).getNotificationType()).isEqualTo("HEARING_RESCHEDULED");
        });
    }

    @Test
    @DisplayName("Null litigantContactEmail is skipped with a logged warning, without throwing error")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testNullLitigantEmailIsSkipped() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder().name("Judge NullEmail").build());
        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Room NullEmail").capacity(20).build());

        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("MAIL-NULL-003")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(3))
                .currentStatus(CaseStatus.FILED)
                .litigantContactEmail(null) // Null email!
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
                .proposedTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0))
                .durationMinutes(60)
                .status(ProposalStatus.PROPOSED)
                .build());

        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve"))
                .andExpect(status().isOk());

        // Wait to verify mailSender is never called
        Thread.sleep(500);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("SMTP failure does NOT fail or roll back proposal approval; writes FAILED log with retries")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testSmtpFailureDoesNotBlockApproval() throws Exception {
        doThrow(new MailSendException("SMTP connection refused to port 1025"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        Judge judge = judgeRepository.save(Judge.builder().name("Judge SmtpFail").build());
        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Room SmtpFail").capacity(25).build());

        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("MAIL-FAIL-004")
                .caseType(CaseType.POCSO)
                .filingDate(LocalDate.now().minusDays(8))
                .currentStatus(CaseStatus.FILED)
                .litigantContactEmail("litigant_fail@example.com")
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
                .proposedTime(LocalDateTime.now().plusDays(2).withHour(11).withMinute(0))
                .durationMinutes(60)
                .status(ProposalStatus.PROPOSED)
                .build());

        // Approval must succeed with 200 OK despite SMTP failure!
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve"))
                .andExpect(status().isOk());

        // Hearing must still be created
        assertThat(hearingRepository.findAll()).anyMatch(h -> h.getLegalCase().getId().equals(legalCase.getId()));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<NotificationLog> logs = notificationLogRepository.findByRecipient("litigant_fail@example.com");
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");
            assertThat(logs.get(0).getErrorMessage()).contains("SMTP connection refused");
            assertThat(logs.get(0).getRetryCount()).isEqualTo(3); // 3 bounded retries performed
        });
    }

    @Test
    @DisplayName("Reprioritization digest is sent once per nightly run listing all qualifying cases")
    void testReprioritizationDigestSentOncePerRun() {
        // Create registrar user
        userRepository.findByUsername("regUserDigest").ifPresent(userRepository::delete);
        userRepository.save(com.judicialflow.security.User.builder()
                .username("regUserDigest")
                .passwordHash("pass")
                .role(UserRole.REGISTRAR)
                .email("registrar_digest@judicialflow.org")
                .enabled(true)
                .build());

        UUID runId = UUID.randomUUID();
        List<ScheduleDiffSummary.ReprioritizationDiff> diffs = List.of(
                ScheduleDiffSummary.ReprioritizationDiff.builder()
                        .caseId(UUID.randomUUID())
                        .caseNumber("CASE-SHIFT-001")
                        .previousScore(BigDecimal.valueOf(10.0))
                        .currentScore(BigDecimal.valueOf(18.5))
                        .scoreDelta(BigDecimal.valueOf(8.5))
                        .details("Score increased by 8.5")
                        .build(),
                ScheduleDiffSummary.ReprioritizationDiff.builder()
                        .caseId(UUID.randomUUID())
                        .caseNumber("CASE-SHIFT-002")
                        .previousScore(BigDecimal.valueOf(25.0))
                        .currentScore(BigDecimal.valueOf(17.0))
                        .scoreDelta(BigDecimal.valueOf(-8.0))
                        .details("Score dropped by 8.0")
                        .build()
        );

        eventPublisher.publishEvent(new NightlyRunCompletedEvent(this, runId, diffs));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender, times(1)).send(captor.capture()); // ONE digest email sent!

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getTo()).contains("registrar_digest@judicialflow.org");
            assertThat(sent.getSubject()).contains("Nightly Reprioritization Digest");
            assertThat(sent.getText()).contains("CASE-SHIFT-001");
            assertThat(sent.getText()).contains("CASE-SHIFT-002");

            List<NotificationLog> logs = notificationLogRepository.findByNotificationType("REPRIORITIZATION_DIGEST");
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getStatus()).isEqualTo("SENT");
        });
    }

    @Test
    @DisplayName("Hearing at 09:00 slot produces email saying '09:00 AM IST' and matching API value with +05:30 offset")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testHearingAt09SlotProducesMatchingEmailAndApiTime() throws Exception {
        Judge judge = judgeRepository.save(Judge.builder().name("Justice Morning").build());
        Courtroom courtroom = courtroomRepository.save(Courtroom.builder().name("Courtroom 9AM").capacity(50).build());

        Case legalCase = caseRepository.save(Case.builder()
                .caseNumber("TIME-ZONE-0900")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now().minusDays(5))
                .currentStatus(CaseStatus.FILED)
                .litigantContactEmail("morninglitigant@example.com")
                .build());

        SchedulingRun run = runRepository.save(SchedulingRun.builder()
                .status(RunStatus.COMPLETED)
                .horizonDays(5)
                .defaultDurationMinutes(60)
                .seed(99L)
                .build());

        LocalDateTime nineAmSlot = LocalDate.now().plusDays(3).atTime(9, 0, 0);

        SchedulingProposal proposal = proposalRepository.save(SchedulingProposal.builder()
                .run(run)
                .legalCase(legalCase)
                .judge(judge)
                .courtroom(courtroom)
                .proposedTime(nineAmSlot)
                .durationMinutes(60)
                .status(ProposalStatus.PROPOSED)
                .build());

        // Approve proposal
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve"))
                .andExpect(status().isOk());

        // Check email text contains "09:00 AM IST"
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender, atLeastOnce()).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getText()).contains("09:00 AM IST");
        });

        // Check API response for hearings/cases/{caseId}
        mockMvc.perform(get("/api/v1/hearings/cases/" + legalCase.getId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.scheduledTime").value(org.hamcrest.Matchers.containsString("09:00:00+05:30")));
    }
}
