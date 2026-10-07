package com.judicialflow.scheduling;

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
import com.judicialflow.scheduling.batch.service.NightlyJobLauncherService;
import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Scheduling Load and Non-Overlap Stress Test (3,000 cases)")
class SchedulingLoadTest extends AbstractIntegrationTest {

    @Autowired
    private NightlyJobLauncherService jobLauncherService;

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

    @Test
    @DisplayName("Load Test: Seed 3,000 cases, 8 judges, 5 courtrooms -> execute batch -> assert no slot overlaps")
    void testLoadScale3000Cases() throws Exception {
        // 1. Cleanup
        hearingRepository.deleteAll();
        proposalRepository.deleteAll();
        runRepository.deleteAll();
        priorityScoreRepository.deleteAll();
        caseRepository.deleteAll();
        judgeRepository.deleteAll();
        courtroomRepository.deleteAll();

        // 2. Seed 8 Judges
        List<DayOfWeek> workDays = List.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        );
        List<Judge> judges = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            List<com.judicialflow.common.models.JudgeAvailabilityWindow> windows = new ArrayList<>();
            for (DayOfWeek day : workDays) {
                windows.add(com.judicialflow.common.models.JudgeAvailabilityWindow.builder()
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .build());
            }
            judges.add(Judge.builder()
                    .name("Judge " + i)
                    .specialization(i % 2 == 0 ? "CIVIL" : "CRIMINAL")
                    .availabilityWindows(windows)
                    .build());
        }
        judgeRepository.saveAll(judges);

        // 3. Seed 5 Courtrooms
        List<Courtroom> courtrooms = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            List<com.judicialflow.common.models.CourtroomAvailabilityWindow> windows = new ArrayList<>();
            for (DayOfWeek day : workDays) {
                windows.add(com.judicialflow.common.models.CourtroomAvailabilityWindow.builder()
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .build());
            }
            courtrooms.add(Courtroom.builder()
                    .name("Court Room " + i)
                    .capacity(50 + i * 10)
                    .availability(windows)
                    .build());
        }
        courtroomRepository.saveAll(courtrooms);

        // 4. Seed 3,000 Cases
        CaseType[] types = CaseType.values();
        List<Case> cases = new ArrayList<>(3000);
        LocalDate now = LocalDate.now();
        for (int i = 1; i <= 3000; i++) {
            cases.add(Case.builder()
                    .caseNumber(String.format("SCALE-%05d", i))
                    .caseType(types[i % types.length])
                    .filingDate(now.minusDays(i % 500))
                    .statutoryDeadline(i % 4 == 0 ? now.plusDays(i % 30) : null)
                    .currentStatus(CaseStatus.FILED)
                    .priorAdjournments(i % 5)
                    .deleted(false)
                    .build());
        }
        caseRepository.saveAll(cases);
        assertThat(caseRepository.count()).isEqualTo(3000);

        // 5. Measure batch run execution with 60-day horizon so most cases are assigned
        long startTime = System.currentTimeMillis();
        JobExecution execution = jobLauncherService.launchNightlyReschedulingJob("SCALE_LOAD_TEST", 60);
        long durationMs = System.currentTimeMillis() - startTime;

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Retrieve generated run
        String runIdStr = execution.getExecutionContext().getString("runId");
        assertThat(runIdStr).isNotNull();
        UUID runId = UUID.fromString(runIdStr);

        var run = runRepository.findById(runId).orElseThrow();
        int assignedCount = run.getTotalAssigned();
        int unschedulableCount = run.getTotalUnschedulable();

        System.out.printf("LOAD TEST RESULTS: Duration=%d ms, Total Cases=%d, Assigned=%d, Unschedulable=%d%n",
                durationMs, run.getTotalCasesInput(), assignedCount, unschedulableCount);

        // Verify that most cases are assigned given the 60-day horizon (capacity = 60 * 40 = 2400)
        assertThat(assignedCount).isGreaterThanOrEqualTo(2000);
        assertThat(assignedCount + unschedulableCount).isEqualTo(3000);

        List<SchedulingProposal> proposals = proposalRepository.findByRunId(runId);
        assertThat(proposals).hasSize(assignedCount);

        // 6. Automated check: verify NO judge or courtroom has overlapping slots in the output
        Map<UUID, List<SchedulingProposal>> judgeProposals = new HashMap<>();
        Map<UUID, List<SchedulingProposal>> courtroomProposals = new HashMap<>();

        for (SchedulingProposal p : proposals) {
            judgeProposals.computeIfAbsent(p.getJudge().getId(), k -> new ArrayList<>()).add(p);
            courtroomProposals.computeIfAbsent(p.getCourtroom().getId(), k -> new ArrayList<>()).add(p);
        }

        // Check Judge Non-Overlap
        for (Map.Entry<UUID, List<SchedulingProposal>> entry : judgeProposals.entrySet()) {
            List<SchedulingProposal> pList = entry.getValue();
            pList.sort(Comparator.comparing(SchedulingProposal::getProposedTime));
            for (int i = 0; i < pList.size() - 1; i++) {
                SchedulingProposal current = pList.get(i);
                SchedulingProposal next = pList.get(i + 1);
                LocalDateTime currentEnd = current.getProposedTime().plusMinutes(current.getDurationMinutes());
                assertThat(currentEnd.isAfter(next.getProposedTime()))
                        .withFailMessage("Judge slot collision detected: Judge %s (%s - %s) overlaps (%s)",
                                current.getJudge().getId(), current.getProposedTime(), currentEnd, next.getProposedTime())
                        .isFalse();
            }
        }

        // Check Courtroom Non-Overlap
        for (Map.Entry<UUID, List<SchedulingProposal>> entry : courtroomProposals.entrySet()) {
            List<SchedulingProposal> pList = entry.getValue();
            pList.sort(Comparator.comparing(SchedulingProposal::getProposedTime));
            for (int i = 0; i < pList.size() - 1; i++) {
                SchedulingProposal current = pList.get(i);
                SchedulingProposal next = pList.get(i + 1);
                LocalDateTime currentEnd = current.getProposedTime().plusMinutes(current.getDurationMinutes());
                assertThat(currentEnd.isAfter(next.getProposedTime()))
                        .withFailMessage("Courtroom slot collision detected: Courtroom %s (%s - %s) overlaps (%s)",
                                current.getCourtroom().getId(), current.getProposedTime(), currentEnd, next.getProposedTime())
                        .isFalse();
            }
        }

        // 7. Priority score analytics: assigned vs unassigned & top 10% assignment rate
        List<com.judicialflow.common.models.PriorityScore> allScores = priorityScoreRepository.findAll();
        Map<UUID, Double> caseScoreMap = new HashMap<>();
        for (var ps : allScores) {
            caseScoreMap.put(ps.getLegalCase().getId(), ps.getTotalScore().doubleValue());
        }

        Set<UUID> assignedCaseIds = proposals.stream()
                .map(p -> p.getLegalCase().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<Double> assignedScores = new ArrayList<>();
        List<Double> unassignedScores = new ArrayList<>();

        for (Case c : cases) {
            Double score = caseScoreMap.getOrDefault(c.getId(), 0.0);
            if (assignedCaseIds.contains(c.getId())) {
                assignedScores.add(score);
            } else {
                unassignedScores.add(score);
            }
        }

        double avgAssignedScore = assignedScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgUnassignedScore = unassignedScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Top 10% highest-scoring cases (300 cases)
        List<Case> sortedCases = new ArrayList<>(cases);
        sortedCases.sort((c1, c2) -> Double.compare(
                caseScoreMap.getOrDefault(c2.getId(), 0.0),
                caseScoreMap.getOrDefault(c1.getId(), 0.0)
        ));
        int top10Count = cases.size() / 10;
        List<Case> top10Cases = sortedCases.subList(0, top10Count);
        long top10Assigned = top10Cases.stream().filter(c -> assignedCaseIds.contains(c.getId())).count();
        double top10AssignedPct = (top10Assigned * 100.0) / top10Count;

        System.out.printf("PRIORITY SCORE METRICS:%n  Avg Assigned Score: %.2f%n  Avg Unassigned Score: %.2f%n  Top 10%% Cases Assigned: %d/%d (%.1f%%)%n",
                avgAssignedScore, avgUnassignedScore, top10Assigned, top10Count, top10AssignedPct);

        assertThat(avgAssignedScore).isGreaterThan(avgUnassignedScore);
        assertThat(top10AssignedPct).isGreaterThanOrEqualTo(95.0);
    }
}
