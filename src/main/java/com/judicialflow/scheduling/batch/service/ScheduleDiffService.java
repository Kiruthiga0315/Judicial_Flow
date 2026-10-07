package com.judicialflow.scheduling.batch.service;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.PriorityScore;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import com.judicialflow.scheduling.model.RunStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingProposalRepository;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleDiffService {

    private final SchedulingRunRepository runRepository;
    private final SchedulingProposalRepository proposalRepository;
    private final PriorityScoreRepository priorityScoreRepository;
    private final CaseRepository caseRepository;
    private final com.judicialflow.common.HearingRepository hearingRepository;

    /**
     * Compute the differences between the current scheduling run and the run immediately preceding it.
     */
    @Transactional(readOnly = true)
    public ScheduleDiffSummary computeRunDiff(UUID currentRunId) {
        SchedulingRun currentRun = runRepository.findById(currentRunId)
                .orElseThrow(() -> new IllegalArgumentException("Current run not found: " + currentRunId));

        // Find the previous completed run before currentRun
        List<SchedulingRun> recentRuns = runRepository.findTop2ByStatusOrderByTriggeredAtDesc(RunStatus.COMPLETED);
        SchedulingRun previousRun = null;
        for (SchedulingRun r : recentRuns) {
            if (!r.getId().equals(currentRunId)) {
                previousRun = r;
                break;
            }
        }

        List<SchedulingProposal> currentProposals = proposalRepository.findByRunId(currentRunId);
        List<SchedulingProposal> prevProposals = previousRun != null
                ? proposalRepository.findByRunId(previousRun.getId())
                : Collections.emptyList();

        Map<UUID, SchedulingProposal> prevByCase = new HashMap<>();
        for (SchedulingProposal p : prevProposals) {
            prevByCase.put(p.getLegalCase().getId(), p);
        }

        List<ScheduleDiffSummary.AssignmentDiff> assignmentDiffs = new ArrayList<>();
        int newCount = 0;
        int modifiedCount = 0;
        int unchangedCount = 0;

        for (SchedulingProposal curr : currentProposals) {
            UUID caseId = curr.getLegalCase().getId();
            String caseNumber = curr.getLegalCase().getCaseNumber();
            SchedulingProposal prev = prevByCase.remove(caseId);

            if (prev == null) {
                newCount++;
                assignmentDiffs.add(ScheduleDiffSummary.AssignmentDiff.builder()
                        .caseId(caseId)
                        .caseNumber(caseNumber)
                        .diffType(ScheduleDiffSummary.DiffType.NEW)
                        .currentJudge(curr.getJudge().getName())
                        .currentCourtroom(curr.getCourtroom().getName())
                        .currentProposedTime(curr.getProposedTime())
                        .details(String.format("Newly scheduled: Judge %s in Room %s at %s",
                                curr.getJudge().getName(), curr.getCourtroom().getName(), curr.getProposedTime()))
                        .build());
            } else {
                boolean judgeChanged = !Objects.equals(prev.getJudge().getId(), curr.getJudge().getId());
                boolean courtroomChanged = !Objects.equals(prev.getCourtroom().getId(), curr.getCourtroom().getId());
                boolean timeChanged = !Objects.equals(prev.getProposedTime(), curr.getProposedTime());

                if (judgeChanged || courtroomChanged || timeChanged) {
                    modifiedCount++;
                    List<String> changes = new ArrayList<>();
                    if (judgeChanged) changes.add(String.format("Judge: %s -> %s", prev.getJudge().getName(), curr.getJudge().getName()));
                    if (courtroomChanged) changes.add(String.format("Room: %s -> %s", prev.getCourtroom().getName(), curr.getCourtroom().getName()));
                    if (timeChanged) changes.add(String.format("Time: %s -> %s", prev.getProposedTime(), curr.getProposedTime()));

                    assignmentDiffs.add(ScheduleDiffSummary.AssignmentDiff.builder()
                            .caseId(caseId)
                            .caseNumber(caseNumber)
                            .diffType(ScheduleDiffSummary.DiffType.MODIFIED)
                            .previousJudge(prev.getJudge().getName())
                            .currentJudge(curr.getJudge().getName())
                            .previousCourtroom(prev.getCourtroom().getName())
                            .currentCourtroom(curr.getCourtroom().getName())
                            .previousProposedTime(prev.getProposedTime())
                            .currentProposedTime(curr.getProposedTime())
                            .details("Assignment modified: " + String.join(", ", changes))
                            .build());
                } else {
                    unchangedCount++;
                    assignmentDiffs.add(ScheduleDiffSummary.AssignmentDiff.builder()
                            .caseId(caseId)
                            .caseNumber(caseNumber)
                            .diffType(ScheduleDiffSummary.DiffType.UNCHANGED)
                            .previousJudge(prev.getJudge().getName())
                            .currentJudge(curr.getJudge().getName())
                            .previousCourtroom(prev.getCourtroom().getName())
                            .currentCourtroom(curr.getCourtroom().getName())
                            .previousProposedTime(prev.getProposedTime())
                            .currentProposedTime(curr.getProposedTime())
                            .details("Assignment unchanged")
                            .build());
                }
            }
        }

        // Cases that were in prev run but dropped in curr run
        for (SchedulingProposal prev : prevByCase.values()) {
            UUID caseId = prev.getLegalCase().getId();
            boolean hasCommittedHearing = hearingRepository.findAllByStatus(com.judicialflow.common.enums.HearingStatus.SCHEDULED)
                    .stream()
                    .anyMatch(h -> h.getLegalCase().getId().equals(caseId));

            if (hasCommittedHearing) {
                assignmentDiffs.add(ScheduleDiffSummary.AssignmentDiff.builder()
                        .caseId(caseId)
                        .caseNumber(prev.getLegalCase().getCaseNumber())
                        .diffType(ScheduleDiffSummary.DiffType.COMMITTED)
                        .previousJudge(prev.getJudge().getName())
                        .previousCourtroom(prev.getCourtroom().getName())
                        .previousProposedTime(prev.getProposedTime())
                        .details(String.format("Case officially scheduled; excluded from future proposals (was Judge %s, Room %s, %s)",
                                prev.getJudge().getName(), prev.getCourtroom().getName(), prev.getProposedTime()))
                        .build());
            } else {
                assignmentDiffs.add(ScheduleDiffSummary.AssignmentDiff.builder()
                        .caseId(caseId)
                        .caseNumber(prev.getLegalCase().getCaseNumber())
                        .diffType(ScheduleDiffSummary.DiffType.REMOVED)
                        .previousJudge(prev.getJudge().getName())
                        .previousCourtroom(prev.getCourtroom().getName())
                        .previousProposedTime(prev.getProposedTime())
                        .details(String.format("No longer scheduled in current run (was Judge %s, Room %s, %s)",
                                prev.getJudge().getName(), prev.getCourtroom().getName(), prev.getProposedTime()))
                        .build());
            }
        }

        // Reprioritization diffs: compare latest 2 score history entries for open cases
        List<ScheduleDiffSummary.ReprioritizationDiff> reprioritizations = computeReprioritizationDiffs();

        ScheduleDiffSummary summary = ScheduleDiffSummary.builder()
                .currentRunId(currentRunId)
                .previousRunId(previousRun != null ? previousRun.getId() : null)
                .totalCurrentProposals(currentProposals.size())
                .totalPreviousProposals(prevProposals.size())
                .newAssignmentsCount(newCount)
                .modifiedAssignmentsCount(modifiedCount)
                .unchangedAssignmentsCount(unchangedCount)
                .reprioritizedCasesCount(reprioritizations.size())
                .assignmentDiffs(assignmentDiffs)
                .reprioritizations(reprioritizations)
                .build();

        logRunDiff(summary);
        return summary;
    }

    private List<ScheduleDiffSummary.ReprioritizationDiff> computeReprioritizationDiffs() {
        List<ScheduleDiffSummary.ReprioritizationDiff> diffs = new ArrayList<>();
        List<Case> openCases = caseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> c.getCurrentStatus() != CaseStatus.DISPOSED)
                .toList();

        for (Case c : openCases) {
            List<PriorityScore> history = priorityScoreRepository.findAllByCaseIdOrderByComputedAtDesc(c.getId());
            if (history.size() >= 2) {
                PriorityScore latest = history.get(0);
                PriorityScore previous = history.get(1);
                if (latest.getTotalScore().compareTo(previous.getTotalScore()) != 0) {
                    BigDecimal delta = latest.getTotalScore().subtract(previous.getTotalScore());
                    String sign = delta.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                    diffs.add(ScheduleDiffSummary.ReprioritizationDiff.builder()
                            .caseId(c.getId())
                            .caseNumber(c.getCaseNumber())
                            .previousScore(previous.getTotalScore())
                            .currentScore(latest.getTotalScore())
                            .scoreDelta(delta)
                            .details(String.format("Score changed from %s to %s (delta: %s%s)",
                                    previous.getTotalScore(), latest.getTotalScore(), sign, delta))
                            .build());
                }
            }
        }
        return diffs;
    }

    private void logRunDiff(ScheduleDiffSummary summary) {
        log.info("==================== SCHEDULING RUN DIFF ====================");
        log.info("Current Run ID : {}", summary.getCurrentRunId());
        log.info("Previous Run ID: {}", summary.getPreviousRunId() != null ? summary.getPreviousRunId() : "NONE (initial run)");
        log.info("Summary: Current Proposals={}, Previous Proposals={}, New={}, Modified={}, Unchanged={}, Reprioritized Cases={}",
                summary.getTotalCurrentProposals(),
                summary.getTotalPreviousProposals(),
                summary.getNewAssignmentsCount(),
                summary.getModifiedAssignmentsCount(),
                summary.getUnchangedAssignmentsCount(),
                summary.getReprioritizedCasesCount());

        for (ScheduleDiffSummary.AssignmentDiff aDiff : summary.getAssignmentDiffs()) {
            if (aDiff.getDiffType() != ScheduleDiffSummary.DiffType.UNCHANGED) {
                log.info("[ASSIGNMENT-CHANGE] Case: {} | Type: {} | Details: {}",
                        aDiff.getCaseNumber(), aDiff.getDiffType(), aDiff.getDetails());
            }
        }

        for (ScheduleDiffSummary.ReprioritizationDiff rDiff : summary.getReprioritizations()) {
            log.info("[REPRIORITIZED] Case: {} | Prev: {} | Curr: {} | Delta: {}",
                    rDiff.getCaseNumber(), rDiff.getPreviousScore(), rDiff.getCurrentScore(), rDiff.getScoreDelta());
        }
        log.info("=============================================================");
    }
}
