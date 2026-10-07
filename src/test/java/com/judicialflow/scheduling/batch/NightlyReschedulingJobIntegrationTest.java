package com.judicialflow.scheduling.batch;

import com.judicialflow.AbstractIntegrationTest;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.Judge;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.scheduling.batch.config.NightlyReschedulingBatchConfig;
import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import com.judicialflow.scheduling.batch.service.NightlyJobLauncherService;
import com.judicialflow.scheduling.batch.service.ScheduleDiffService;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Nightly Rescheduling Batch Job Integration Tests")
class NightlyReschedulingJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NightlyJobLauncherService jobLauncherService;

    @Autowired
    private ScheduleDiffService diffService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private CourtroomRepository courtroomRepository;

    @Autowired
    private PriorityScoreRepository priorityScoreRepository;

    @Autowired
    private SchedulingRunRepository runRepository;

    @Autowired
    private SchedulingProposalRepository proposalRepository;

    @Autowired
    private com.judicialflow.common.HearingRepository hearingRepository;

    @Autowired
    private com.judicialflow.scheduling.service.SchedulingConcurrencyGuard concurrencyGuard;

    @BeforeEach
    void setupDatabase() {
        cleanupAll();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownDatabase() {
        cleanupAll();
    }

    private void cleanupAll() {
        hearingRepository.deleteAll();
        proposalRepository.deleteAll();
        runRepository.deleteAll();
        priorityScoreRepository.deleteAll();
        caseRepository.deleteAll();
        judgeRepository.deleteAll();
        courtroomRepository.deleteAll();
    }

    private void seedJudgesAndCourtrooms() {
        // Judge Alpha
        List<com.judicialflow.common.models.JudgeAvailabilityWindow> jWindows = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            jWindows.add(com.judicialflow.common.models.JudgeAvailabilityWindow.builder()
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .build());
        }
        Judge j1 = Judge.builder()
                .name("Honorable Justice A")
                .specialization("CRIMINAL")
                .availabilityWindows(jWindows)
                .build();
        judgeRepository.save(j1);

        // Courtroom 1
        List<com.judicialflow.common.models.CourtroomAvailabilityWindow> crWindows = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            crWindows.add(com.judicialflow.common.models.CourtroomAvailabilityWindow.builder()
                    .dayOfWeek(day)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .build());
        }
        Courtroom cr1 = Courtroom.builder()
                .name("Court Room 101")
                .capacity(100)
                .availability(crWindows)
                .build();
        courtroomRepository.save(cr1);
    }

    private Case seedCase(String caseNumber, CaseType caseType, LocalDate filingDate, int adjournments) {
        return caseRepository.save(Case.builder()
                .caseNumber(caseNumber)
                .caseType(caseType)
                .filingDate(filingDate)
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(adjournments)
                .deleted(false)
                .build());
    }

    @Test
    @DisplayName("Batch job recomputes priority scores and generates proposals for open cases")
    void testNightlyJobExecutesSuccessfullyAndUpdatesScoresAndProposals() throws Exception {
        seedJudgesAndCourtrooms();

        Case case1 = seedCase("BATCH-001", CaseType.BAIL, LocalDate.now().minusDays(30), 1);
        Case case2 = seedCase("BATCH-002", CaseType.CIVIL, LocalDate.now().minusDays(10), 0);
        Case disposedCase = seedCase("BATCH-DISP", CaseType.CIVIL, LocalDate.now().minusDays(50), 0);
        disposedCase.setCurrentStatus(CaseStatus.DISPOSED);
        caseRepository.save(disposedCase);

        // Verify initial state: no priority scores recorded
        assertThat(priorityScoreRepository.count()).isZero();
        assertThat(runRepository.count()).isZero();

        // 1. Run the job via launcher service
        JobExecution jobExecution = jobLauncherService.launchNightlyReschedulingJob("TEST_RUN_1");
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 2. Assert priority scores were computed for open cases, but NOT for disposed case
        assertThat(priorityScoreRepository.findLatestByCaseId(case1.getId())).isPresent();
        assertThat(priorityScoreRepository.findLatestByCaseId(case2.getId())).isPresent();
        assertThat(priorityScoreRepository.findLatestByCaseId(disposedCase.getId())).isEmpty();

        // Bail case should have higher score than civil case
        var bailScore = priorityScoreRepository.findLatestByCaseId(case1.getId()).get().getTotalScore();
        var civilScore = priorityScoreRepository.findLatestByCaseId(case2.getId()).get().getTotalScore();
        assertThat(bailScore).isGreaterThan(civilScore);

        // 3. Assert scheduling run was produced with proposals
        String runIdStr = jobExecution.getExecutionContext().getString("runId");
        assertThat(runIdStr).isNotNull();
        UUID runId = UUID.fromString(runIdStr);

        SchedulingRun run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(run.getTotalAssigned()).isEqualTo(2);

        List<SchedulingProposal> proposals = proposalRepository.findByRunId(runId);
        assertThat(proposals).hasSize(2);
    }

    @Test
    @DisplayName("Batch job is idempotent and safely re-runnable without corruption")
    void testNightlyJobIdempotencyAndRerun() throws Exception {
        seedJudgesAndCourtrooms();

        Case case1 = seedCase("IDEM-001", CaseType.POCSO, LocalDate.now().minusDays(40), 2);
        Case case2 = seedCase("IDEM-002", CaseType.MATRIMONIAL, LocalDate.now().minusDays(20), 0);

        // First run
        JobExecution run1 = jobLauncherService.launchNightlyReschedulingJob("RUN_1");
        assertThat(run1.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        long scoresAfterRun1 = priorityScoreRepository.count();
        long runsAfterRun1 = runRepository.count();
        assertThat(scoresAfterRun1).isEqualTo(2);
        assertThat(runsAfterRun1).isEqualTo(1);

        // Second run immediately following (simulate consecutive or retry execution)
        JobExecution run2 = jobLauncherService.launchNightlyReschedulingJob("RUN_2");
        assertThat(run2.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        long runsAfterRun2 = runRepository.count();
        assertThat(runsAfterRun2).isEqualTo(2);

        // Both runs produced valid proposals without corrupting existing schedules
        String run1Id = run1.getExecutionContext().getString("runId");
        String run2Id = run2.getExecutionContext().getString("runId");
        assertThat(run1Id).isNotEqualTo(run2Id);

        assertThat(proposalRepository.findByRunId(UUID.fromString(run1Id))).hasSize(2);
        assertThat(proposalRepository.findByRunId(UUID.fromString(run2Id))).hasSize(2);

        // Diff between run 2 and run 1 should detect unchanged or stable assignments
        ScheduleDiffSummary diff = diffService.computeRunDiff(UUID.fromString(run2Id));
        assertThat(diff.getCurrentRunId()).isEqualTo(UUID.fromString(run2Id));
        assertThat(diff.getPreviousRunId()).isEqualTo(UUID.fromString(run1Id));
        assertThat(diff.getTotalCurrentProposals()).isEqualTo(2);
        assertThat(diff.getTotalPreviousProposals()).isEqualTo(2);
    }

    @Test
    @DisplayName("Diff detection captures newly added cases and reprioritized cases across runs")
    void testDiffDetectionBetweenConsecutiveRuns() throws Exception {
        seedJudgesAndCourtrooms();

        Case case1 = seedCase("DIFF-001", CaseType.CIVIL, LocalDate.now().minusDays(15), 0);

        // Run 1
        JobExecution run1 = jobLauncherService.launchNightlyReschedulingJob("DIFF_RUN_1");
        assertThat(run1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        String run1Id = run1.getExecutionContext().getString("runId");

        // Now introduce a change: add a new high-priority case and adjourn case1
        case1.setPriorAdjournments(3); // increases its score
        caseRepository.save(case1);

        Case case2 = seedCase("DIFF-002-NEW", CaseType.BAIL, LocalDate.now().minusDays(5), 1);

        // Run 2
        JobExecution run2 = jobLauncherService.launchNightlyReschedulingJob("DIFF_RUN_2");
        assertThat(run2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        String run2Id = run2.getExecutionContext().getString("runId");

        ScheduleDiffSummary diff = diffService.computeRunDiff(UUID.fromString(run2Id));
        assertThat(diff.getPreviousRunId()).isEqualTo(UUID.fromString(run1Id));
        assertThat(diff.getTotalCurrentProposals()).isEqualTo(2);
        assertThat(diff.getTotalPreviousProposals()).isEqualTo(1);
        assertThat(diff.getNewAssignmentsCount()).isEqualTo(1); // case2 is new
        assertThat(diff.getReprioritizedCasesCount()).isGreaterThanOrEqualTo(1); // case1 had adjournment increase

        // Verify that case2 is labeled NEW in assignment diffs
        var newAssignment = diff.getAssignmentDiffs().stream()
                .filter(d -> d.getCaseNumber().equals("DIFF-002-NEW"))
                .findFirst();
        assertThat(newAssignment).isPresent();
        assertThat(newAssignment.get().getDiffType()).isEqualTo(ScheduleDiffSummary.DiffType.NEW);

        // Verify case1 reprioritization record
        var reprioritized = diff.getReprioritizations().stream()
                .filter(r -> r.getCaseNumber().equals("DIFF-001"))
                .findFirst();
        assertThat(reprioritized).isPresent();
        assertThat(reprioritized.get().getScoreDelta()).isPositive();
    }

    @Test
    @DisplayName("Manual admin trigger endpoint POST /api/v1/admin/batch/reschedule executes job with admin role")
    @org.springframework.security.test.context.support.WithMockUser(username = "adminUser", roles = "ADMIN")
    void testManualAdminTriggerEndpoint() throws Exception {
        seedJudgesAndCourtrooms();
        seedCase("ADMIN-001", CaseType.BAIL, LocalDate.now().minusDays(20), 0);

        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .param("triggerSource", "ADMIN_UI_TEST")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value(NightlyReschedulingBatchConfig.JOB_NAME))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.recomputedCasesCount").value(1))
                .andExpect(jsonPath("$.assignedCount").value(1))
                .andExpect(jsonPath("$.diffSummary").exists());
    }

    @Test
    @DisplayName("Manual admin trigger endpoint is protected: rejected with 401 without credentials")
    void testManualAdminTriggerWithoutCredentialsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Manual admin trigger endpoint is protected: rejected with 403 for non-admin user")
    @org.springframework.security.test.context.support.WithMockUser(username = "regUser", roles = "REGISTRAR")
    void testManualAdminTriggerWithNonAdminRoleForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/batch/diff/{runId} returns valid diff response with admin role")
    @org.springframework.security.test.context.support.WithMockUser(username = "adminUser", roles = "ADMIN")
    void testGetRunDiffEndpoint() throws Exception {
        seedJudgesAndCourtrooms();
        seedCase("DIFF-ENDPOINT-001", CaseType.CIVIL, LocalDate.now().minusDays(10), 0);

        JobExecution run1 = jobLauncherService.launchNightlyReschedulingJob("ENDPOINT_TEST");
        String runId = run1.getExecutionContext().getString("runId");

        mockMvc.perform(get("/api/v1/admin/batch/diff/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRunId").value(runId))
                .andExpect(jsonPath("$.totalCurrentProposals").value(1))
                .andExpect(jsonPath("$.assignmentDiffs").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/admin/batch/diff/{runId} is protected: rejected with 401 without credentials")
    void testGetRunDiffWithoutCredentialsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/batch/diff/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Async manual admin trigger POST /api/v1/admin/batch/reschedule?async=true returns 202 ACCEPTED with runId, then GET /status/{runId} returns status")
    @org.springframework.security.test.context.support.WithMockUser(username = "adminUser", roles = "ADMIN")
    void testAsyncAdminTriggerEndpointAndStatusPolling() throws Exception {
        seedJudgesAndCourtrooms();
        seedCase("ASYNC-001", CaseType.POCSO, LocalDate.now().minusDays(15), 0);

        var result = mockMvc.perform(post("/api/v1/admin/batch/reschedule")
                        .param("triggerSource", "ASYNC_ADMIN_TEST")
                        .param("async", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String runId = com.jayway.jsonpath.JsonPath.read(responseJson, "$.runId");

        // Polling GET /api/v1/admin/batch/status/{runId}
        mockMvc.perform(get("/api/v1/admin/batch/status/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId));

        // Await background execution completion to avoid leaking into subsequent tests
        long deadline = System.currentTimeMillis() + 10000;
        while (concurrencyGuard.getActiveRunId() != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }
}
