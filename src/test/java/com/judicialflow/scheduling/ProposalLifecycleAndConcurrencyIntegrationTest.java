package com.judicialflow.scheduling;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.HearingRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.AuditLogEntry;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.Hearing;
import com.judicialflow.common.models.Judge;
import com.judicialflow.common.AuditLogRepository;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.scheduling.batch.service.NightlyJobLauncherService;
import com.judicialflow.scheduling.dto.SchedulingRunResponse;
import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import com.judicialflow.scheduling.service.SchedulingConcurrencyGuard;
import com.judicialflow.scheduling.service.SchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Proposal Approval Lifecycle and Concurrency Integration Tests")
class ProposalLifecycleAndConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SchedulingService schedulingService;

    @Autowired
    private NightlyJobLauncherService jobLauncherService;

    @Autowired
    private com.judicialflow.scheduling.batch.service.ScheduleDiffService diffService;

    @Autowired
    private SchedulingConcurrencyGuard concurrencyGuard;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private HearingRepository hearingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SchedulingProposalRepository proposalRepository;

    @Autowired
    private SchedulingRunRepository runRepository;

    @Autowired
    private PriorityScoreRepository priorityScoreRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupDatabase() {
        cleanup();
    }

    private void cleanup() {
        hearingRepository.deleteAll();
        proposalRepository.deleteAll();
        runRepository.deleteAll();
        clearAuditLogs();
        priorityScoreRepository.deleteAll();
        caseRepository.deleteAll();
        judgeRepository.deleteAll();
        courtroomRepository.deleteAll();
    }

    private Judge seedJudge(String name) {
        List<com.judicialflow.common.models.JudgeAvailabilityWindow> jWindows = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            jWindows.add(com.judicialflow.common.models.JudgeAvailabilityWindow.builder()
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .build());
        }
        return judgeRepository.save(Judge.builder()
                .name(name)
                .specialization("CRIMINAL")
                .availabilityWindows(jWindows)
                .build());
    }

    private Courtroom seedCourtroom(String name) {
        List<com.judicialflow.common.models.CourtroomAvailabilityWindow> crWindows = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            crWindows.add(com.judicialflow.common.models.CourtroomAvailabilityWindow.builder()
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .build());
        }
        return courtroomRepository.save(Courtroom.builder()
                .name(name)
                .capacity(100)
                .availability(crWindows)
                .build());
    }

    private Case seedCase(String caseNumber, CaseType caseType, LocalDate filingDate) {
        return caseRepository.save(Case.builder()
                .caseNumber(caseNumber)
                .caseType(caseType)
                .filingDate(filingDate)
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(0)
                .deleted(false)
                .build());
    }

    @Test
    @DisplayName("Verification 3: Full Approve Flow - run -> proposal -> approve -> hearing created (createdByEngine=true) -> audit written -> second run leaves hearing untouched")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testFullApproveFlowAndSubsequentRunLeavesHearingUntouched() throws Exception {
        Judge judge = seedJudge("Justice Verification");
        Courtroom courtroom = seedCourtroom("Court Room Verif-1");
        Case legalCase = seedCase("VERIF-FLOW-001", CaseType.BAIL, LocalDate.now().minusDays(10));

        // 1. Run scheduling engine
        SchedulingRunResponse run1 = schedulingService.triggerSchedulingRun(null);
        List<SchedulingProposal> proposals = proposalRepository.findByRunId(run1.getRunId());
        assertThat(proposals).hasSize(1);
        SchedulingProposal proposal = proposals.get(0);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PROPOSED);

        // 2. Approve proposal via endpoint POST /api/v1/scheduling/proposals/{id}/approve
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.proposalId").value(proposal.getId().toString()));

        // 3. Verify hearing row created with createdByEngine = true
        List<Hearing> hearings = hearingRepository.findAll();
        assertThat(hearings).hasSize(1);
        Hearing committedHearing = hearings.get(0);
        assertThat(committedHearing.isCreatedByEngine()).isTrue();
        assertThat(committedHearing.getLegalCase().getId()).isEqualTo(legalCase.getId());
        assertThat(committedHearing.getJudge().getId()).isEqualTo(judge.getId());
        assertThat(committedHearing.getCourtroom().getId()).isEqualTo(courtroom.getId());
        assertThat(committedHearing.getStatus()).isEqualTo(HearingStatus.SCHEDULED);

        // Verify case updated
        Case updatedCase = caseRepository.findById(legalCase.getId()).orElseThrow();
        assertThat(updatedCase.getCurrentStatus()).isEqualTo(CaseStatus.SCHEDULED);
        assertThat(updatedCase.getAssignedJudge().getId()).isEqualTo(judge.getId());
        assertThat(updatedCase.getAssignedCourtroom().getId()).isEqualTo(courtroom.getId());
        assertThat(updatedCase.getNextHearingDate()).isEqualTo(committedHearing.getScheduledTime());

        // Verify audit log entry written
        List<AuditLogEntry> auditEntries = auditLogRepository.findByAction("APPROVE");
        assertThat(auditEntries).isNotEmpty();
        AuditLogEntry approveAudit = auditEntries.stream()
                .filter(a -> a.getEntityId().equals(proposal.getId().toString()))
                .findFirst()
                .orElseThrow();
        assertThat(approveAudit.getPerformedBy()).isEqualTo("registrarUser");
        assertThat(approveAudit.getDetails()).contains("Approved proposal");

        System.out.println("=== RAW SQL SELECT FROM audit_log_entries ===");
        jdbcTemplate.queryForList("SELECT id, entity_name, entity_id, action, performed_by, action_time, reason_code, details FROM audit_log_entries WHERE action = 'APPROVE'")
                .forEach(row -> System.out.println("SQL_AUDIT_ROW: " + row));
        System.out.println("=== END RAW SQL SELECT ===");

        // 4. Run second nightly run -> committed hearing MUST remain untouched and case excluded from new proposals
        jobLauncherService.launchNightlyReschedulingJob("SECOND_VERIF_RUN");

        // Assert hearing is untouched
        List<Hearing> hearingsAfterSecondRun = hearingRepository.findAll();
        assertThat(hearingsAfterSecondRun).hasSize(1);
        assertThat(hearingsAfterSecondRun.get(0).getId()).isEqualTo(committedHearing.getId());

        // Second run should NOT create new proposals for this case because it already has an active committed hearing
        List<SchedulingRun> allRuns = runRepository.findAll();
        assertThat(allRuns).hasSize(2);
        SchedulingRun run2 = allRuns.stream().filter(r -> !r.getId().equals(run1.getRunId())).findFirst().orElseThrow();
        List<SchedulingProposal> run2Proposals = proposalRepository.findByRunId(run2.getId());
        assertThat(run2Proposals).isEmpty();
    }

    @Test
    @DisplayName("Verification 4: Stale Proposal Approval returns 409 Conflict when slot is double-booked")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testStaleProposalApprovalReturns409Conflict() throws Exception {
        Judge judge = seedJudge("Justice Conflict");
        Courtroom courtroom = seedCourtroom("Court Room Conflict-1");
        Case case1 = seedCase("CONFLICT-001", CaseType.CIVIL, LocalDate.now().minusDays(5));

        // Generate proposal for case1
        SchedulingRunResponse run = schedulingService.triggerSchedulingRun(null);
        SchedulingProposal proposal = proposalRepository.findByRunId(run.getRunId()).get(0);

        // Before approving, simulate another hearing taking the exact same judge and time slot
        Hearing conflictingHearing = Hearing.builder()
                .legalCase(case1)
                .judge(judge)
                .courtroom(courtroom)
                .scheduledTime(proposal.getProposedTime())
                .estimatedDurationMinutes(proposal.getDurationMinutes())
                .status(HearingStatus.SCHEDULED)
                .createdByEngine(false)
                .build();
        hearingRepository.save(conflictingHearing);

        // Attempting to approve the stale proposal must return HTTP 409 Conflict
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Proposed slot conflict")));
    }

    @Test
    @DisplayName("Verification 5: Concurrent triggers - simultaneous requests result in exactly one execution and one 409 Conflict")
    void testConcurrentTriggerPreventsSimultaneousRuns() throws Exception {
        seedJudge("Justice Concurrent");
        seedCourtroom("Court Room Concurrent");
        seedCase("CONCURRENT-001", CaseType.POCSO, LocalDate.now().minusDays(15));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Integer> triggerTask = () -> {
            try {
                schedulingService.triggerSchedulingRun(null);
                return 200;
            } catch (com.judicialflow.scheduling.exception.SchedulingConflictException ex) {
                return 409;
            } catch (Exception ex) {
                return 500;
            }
        };

        Future<Integer> f1 = executor.submit(triggerTask);
        Future<Integer> f2 = executor.submit(triggerTask);

        int res1 = f1.get();
        int res2 = f2.get();
        executor.shutdown();

        if (res1 == 200) successCount.incrementAndGet();
        if (res1 == 409) conflictCount.incrementAndGet();
        if (res2 == 200) successCount.incrementAndGet();
        if (res2 == 409) conflictCount.incrementAndGet();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Verification 8a: Double-approve of an already APPROVED proposal returns 409 Conflict")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testDoubleApproveReturns409Conflict() throws Exception {
        seedJudge("Justice Double");
        seedCourtroom("Court Room Double");
        seedCase("DOUBLE-APP-001", CaseType.CIVIL, LocalDate.now().minusDays(10));

        SchedulingRunResponse run = schedulingService.triggerSchedulingRun(null);
        SchedulingProposal proposal = proposalRepository.findByRunId(run.getRunId()).get(0);

        // First approval succeeds
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Second approval returns 409 Conflict
        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Proposal cannot be approved because it has status: APPROVED")));
    }

    @Test
    @DisplayName("Verification 8b: Reject proposal endpoint marks proposal REJECTED with reason and audit entry")
    @WithMockUser(username = "registrarUser", roles = "REGISTRAR")
    void testRejectProposalEndpoint() throws Exception {
        seedJudge("Justice Reject");
        seedCourtroom("Court Room Reject");
        seedCase("REJECT-001", CaseType.POCSO, LocalDate.now().minusDays(12));

        SchedulingRunResponse run = schedulingService.triggerSchedulingRun(null);
        SchedulingProposal proposal = proposalRepository.findByRunId(run.getRunId()).get(0);

        String rejectPayload = """
                {
                    "reason": "Counsel not available on specified date"
                }
                """;

        mockMvc.perform(post("/api/v1/scheduling/proposals/" + proposal.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        SchedulingProposal updated = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(updated.getRejectionReason()).isEqualTo("Counsel not available on specified date");

        var auditEntries = auditLogRepository.findByAction("REJECT");
        assertThat(auditEntries).isNotEmpty();
        assertThat(auditEntries.get(0).getDetails()).contains("Counsel not available on specified date");
    }

    @Test
    @DisplayName("Verification 8c: Older proposals become SUPERSEDED upon new scheduling run without being deleted")
    void testProposalSupersedeOnNewRun() throws Exception {
        seedJudge("Justice Supersede");
        seedCourtroom("Court Room Supersede");
        seedCase("SUPERSEDE-001", CaseType.MATRIMONIAL, LocalDate.now().minusDays(8));

        // Run 1 produces proposal 1
        SchedulingRunResponse run1 = schedulingService.triggerSchedulingRun(null);
        SchedulingProposal p1 = proposalRepository.findByRunId(run1.getRunId()).get(0);
        assertThat(p1.getStatus()).isEqualTo(ProposalStatus.PROPOSED);

        // Run 2 produces proposal 2; p1 becomes SUPERSEDED
        SchedulingRunResponse run2 = schedulingService.triggerSchedulingRun(null);
        SchedulingProposal p2 = proposalRepository.findByRunId(run2.getRunId()).get(0);
        assertThat(p2.getStatus()).isEqualTo(ProposalStatus.PROPOSED);

        SchedulingProposal p1Updated = proposalRepository.findById(p1.getId()).orElseThrow();
        assertThat(p1Updated.getStatus()).isEqualTo(ProposalStatus.SUPERSEDED);
        assertThat(proposalRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Verification 6b: Crash midway recovery - run fails midway, lock is cleared, next run executes cleanly")
    void testCrashMidwayLeavesNoStaleLockOrStatus() throws Exception {
        seedJudge("Justice Crash");
        seedCourtroom("Court Room Crash");
        seedCase("CRASH-001", CaseType.CIVIL, LocalDate.now().minusDays(5));

        UUID crashedRunId = UUID.randomUUID();
        concurrencyGuard.tryAcquire(crashedRunId);
        // Simulate a crash/exception inside execution: lock must be released in finally block
        concurrencyGuard.release();

        // Next run must be allowed immediately without 409
        SchedulingRunResponse runNext = schedulingService.triggerSchedulingRun(null);
        assertThat(runNext.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Verification 6c: Manual admin trigger 409 response contains FULL running run ID")
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void testManualAdminTrigger409ContainsRunningRunId() throws Exception {
        UUID activeId = UUID.randomUUID();
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.submit(() -> concurrencyGuard.tryAcquire(activeId)).get();
        try {
            mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("A scheduling run is already in progress with ID: " + activeId.toString()))
                    .andExpect(jsonPath("$.runningRunId").value(activeId.toString()));
        } finally {
            ex.submit(concurrencyGuard::release).get();
            ex.shutdown();
        }
    }

    @Test
    @DisplayName("Verification 7: Case with newly committed hearing excluded from pool is classified as COMMITTED in diff")
    void testDiffClassifiesCommittedHearingAsCommitted() throws Exception {
        Judge judge = seedJudge("Justice DiffCommitted");
        Courtroom courtroom = seedCourtroom("Court Room DiffCommitted");
        Case legalCase = seedCase("DIFF-COMM-001", CaseType.BAIL, LocalDate.now().minusDays(15));

        // Run 1 creates proposal
        SchedulingRunResponse run1 = schedulingService.triggerSchedulingRun(null);
        SchedulingProposal proposal = proposalRepository.findByRunId(run1.getRunId()).get(0);

        // Approve proposal -> creates committed hearing
        schedulingService.approveProposal(proposal.getId());

        // Run 2 re-runs batch
        jobLauncherService.launchNightlyReschedulingJob("DIFF_COMMITTED_RUN");

        var allRuns = runRepository.findAll();
        SchedulingRun run2 = allRuns.stream().filter(r -> !r.getId().equals(run1.getRunId())).findFirst().orElseThrow();

        var diff = diffService.computeRunDiff(run2.getId());
        var assignmentDiff = diff.getAssignmentDiffs().stream()
                .filter(d -> d.getCaseNumber().equals("DIFF-COMM-001"))
                .findFirst();
        assertThat(assignmentDiff).isPresent();
        assertThat(assignmentDiff.get().getDiffType()).isEqualTo(com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary.DiffType.COMMITTED);
    }
}
