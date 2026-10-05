package com.judicialflow.scheduling.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.judicialflow.common.AuditLogRepository;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.HearingRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.*;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.scheduling.config.SchedulingConfig;
import com.judicialflow.scheduling.dto.*;
import com.judicialflow.scheduling.engine.SchedulingEngine;
import com.judicialflow.scheduling.engine.SchedulingInput;
import com.judicialflow.scheduling.engine.SchedulingResult;
import com.judicialflow.scheduling.model.*;
import com.judicialflow.scheduling.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator service for the scheduling engine.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Load unscheduled cases (PENDING, not deleted) with their latest priority scores.</li>
 *   <li>Load all judges and courtrooms with availability windows.</li>
 *   <li>Load existing committed hearings for conflict detection.</li>
 *   <li>Assemble {@link SchedulingInput} and call {@link SchedulingEngine#solve}.</li>
 *   <li>Persist the run, proposals, and decision logs.</li>
 *   <li>Return the result as a <em>proposal</em> (NOT auto-committed to hearings).</li>
 *   <li>Handle manual overrides with audit trail.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {

    private final CaseRepository caseRepository;
    private final JudgeRepository judgeRepository;
    private final CourtroomRepository courtroomRepository;
    private final HearingRepository hearingRepository;
    private final PriorityScoreRepository priorityScoreRepository;
    private final SchedulingRunRepository runRepository;
    private final SchedulingProposalRepository proposalRepository;
    private final SchedulingDecisionRepository decisionRepository;
    private final SchedulingOverrideRepository overrideRepository;
    private final AuditLogRepository auditLogRepository;
    private final SchedulingConfig config;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Trigger a full scheduling run and return the proposed schedule with decision log.
     *
     * <p>This does NOT auto-commit the schedule — it returns proposals that the
     * registrar can accept in Phase 9.
     *
     * @param configDto optional overrides for horizon and duration (null = use defaults)
     * @return complete run response with proposals and decision logs
     */
    @Transactional
    public SchedulingRunResponse triggerSchedulingRun(SchedulingConfigDto configDto) {
        int horizonDays = configDto != null && configDto.getHorizonDays() != null
                ? configDto.getHorizonDays() : config.getHorizonDays();
        int defaultDuration = configDto != null && configDto.getDefaultDurationMinutes() != null
                ? configDto.getDefaultDurationMinutes() : config.getDefaultHearingDurationMinutes();

        log.info("Triggering scheduling run: horizonDays={}, defaultDuration={}", horizonDays, defaultDuration);

        // 1. Create the run record
        SchedulingRun run = SchedulingRun.builder()
                .status(RunStatus.RUNNING)
                .horizonDays(horizonDays)
                .defaultDurationMinutes(defaultDuration)
                .build();
        run = runRepository.save(run);

        try {
            // 2. Assemble engine input
            SchedulingInput input = assembleInput(horizonDays, defaultDuration);
            run.setTotalCasesInput(input.getCases().size());
            run = runRepository.save(run);

            // 3. Run the engine
            SchedulingEngine engine = new SchedulingEngine();
            SchedulingResult result = engine.solve(input);

            // 4. Persist proposals and decisions
            List<ProposalResponse> proposalResponses = persistResults(run, result);

            // 5. Update run status
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            run.setTotalAssigned(result.getTotalAssigned());
            run.setTotalUnschedulable(result.getTotalUnschedulable());
            run = runRepository.save(run);

            // 6. Build response
            Map<String, Integer> workloadDist = new HashMap<>();
            for (var entry : result.getHearingsPerJudge().entrySet()) {
                // Resolve judge name
                String judgeName = input.getJudges().stream()
                        .filter(j -> j.getJudgeId().equals(entry.getKey()))
                        .map(SchedulingInput.JudgeInfo::getJudgeName)
                        .findFirst().orElse(entry.getKey().toString());
                workloadDist.put(judgeName, entry.getValue());
            }

            List<SchedulingRunResponse.UnschedulableCaseResponse> unschedulableResponses =
                    result.getUnschedulableCases().stream()
                            .map(u -> SchedulingRunResponse.UnschedulableCaseResponse.builder()
                                    .caseId(u.getCaseId())
                                    .caseNumber(u.getCaseNumber())
                                    .reason(u.getReason())
                                    .build())
                            .toList();

            return SchedulingRunResponse.builder()
                    .runId(run.getId())
                    .status(run.getStatus().name())
                    .triggeredAt(run.getTriggeredAt())
                    .completedAt(run.getCompletedAt())
                    .totalCasesInput(run.getTotalCasesInput())
                    .totalAssigned(run.getTotalAssigned())
                    .totalUnschedulable(run.getTotalUnschedulable())
                    .horizonDays(horizonDays)
                    .defaultDurationMinutes(defaultDuration)
                    .proposals(proposalResponses)
                    .unschedulableCases(unschedulableResponses)
                    .workloadDistribution(workloadDist)
                    .build();

        } catch (Exception e) {
            log.error("Scheduling run failed", e);
            run.setStatus(RunStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setErrorMessage(e.getMessage());
            runRepository.save(run);
            throw new RuntimeException("Scheduling run failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve a past scheduling run by ID.
     */
    @Transactional(readOnly = true)
    public SchedulingRunResponse getSchedulingRun(UUID runId) {
        SchedulingRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduling run not found: " + runId));

        List<SchedulingProposal> proposals = proposalRepository.findByRunId(runId);
        List<com.judicialflow.scheduling.model.SchedulingDecision> decisions =
                decisionRepository.findByProposalRunId(runId);

        Map<UUID, com.judicialflow.scheduling.model.SchedulingDecision> decisionByProposal =
                decisions.stream().collect(Collectors.toMap(
                        d -> d.getProposal().getId(), d -> d, (a, b) -> a));

        List<ProposalResponse> proposalResponses = proposals.stream()
                .map(p -> {
                    com.judicialflow.scheduling.model.SchedulingDecision dec = decisionByProposal.get(p.getId());
                    return mapToProposalResponse(p, dec);
                })
                .toList();

        return SchedulingRunResponse.builder()
                .runId(run.getId())
                .status(run.getStatus().name())
                .triggeredAt(run.getTriggeredAt())
                .completedAt(run.getCompletedAt())
                .totalCasesInput(run.getTotalCasesInput())
                .totalAssigned(run.getTotalAssigned())
                .totalUnschedulable(run.getTotalUnschedulable())
                .horizonDays(run.getHorizonDays())
                .defaultDurationMinutes(run.getDefaultDurationMinutes())
                .proposals(proposalResponses)
                .build();
    }

    /**
     * Manual override: force a specific judge/courtroom/time for a case.
     * Records the override and creates a hearing. Logs to audit trail.
     */
    @Transactional
    public ProposalResponse applyManualOverride(ManualOverrideRequest request) {
        Case legalCase = caseRepository.findByIdAndDeletedFalse(request.getCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + request.getCaseId()));
        Judge judge = judgeRepository.findById(request.getJudgeId())
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found: " + request.getJudgeId()));
        Courtroom courtroom = courtroomRepository.findById(request.getCourtroomId())
                .orElseThrow(() -> new ResourceNotFoundException("Courtroom not found: " + request.getCourtroomId()));

        int duration = request.getDurationMinutes() > 0 ? request.getDurationMinutes()
                : config.getDefaultHearingDurationMinutes();

        // Create the override record
        SchedulingOverride override = SchedulingOverride.builder()
                .legalCase(legalCase)
                .judge(judge)
                .courtroom(courtroom)
                .scheduledTime(request.getScheduledTime())
                .durationMinutes(duration)
                .reason(request.getReason())
                .overriddenBy(request.getOverriddenBy())
                .build();
        overrideRepository.save(override);

        // Create the actual hearing
        Hearing hearing = Hearing.builder()
                .legalCase(legalCase)
                .judge(judge)
                .courtroom(courtroom)
                .scheduledTime(request.getScheduledTime())
                .estimatedDurationMinutes(duration)
                .status(HearingStatus.SCHEDULED)
                .build();
        hearingRepository.save(hearing);

        // Update case status
        legalCase.setCurrentStatus(CaseStatus.SCHEDULED);
        legalCase.setAssignedJudge(judge);
        caseRepository.save(legalCase);

        // Audit log
        AuditLogEntry auditEntry = AuditLogEntry.builder()
                .entityName("Hearing")
                .entityId(hearing.getId().toString())
                .action("MANUAL_OVERRIDE")
                .performedBy(request.getOverriddenBy())
                .reasonCode("REGISTRAR_OVERRIDE")
                .details(String.format(
                        "Manual override for case %s: assigned to Judge %s in %s at %s. Reason: %s",
                        legalCase.getCaseNumber(), judge.getName(), courtroom.getName(),
                        request.getScheduledTime(), request.getReason()))
                .build();
        // We need an audit log repository — use entityManager or a simple save
        auditLogRepository.save(auditEntry);
        log.info("AUDIT: {}", auditEntry.getDetails());

        return ProposalResponse.builder()
                .caseId(legalCase.getId())
                .caseNumber(legalCase.getCaseNumber())
                .judgeId(judge.getId())
                .judgeName(judge.getName())
                .courtroomId(courtroom.getId())
                .courtroomName(courtroom.getName())
                .proposedTime(request.getScheduledTime())
                .durationMinutes(duration)
                .status("OVERRIDDEN")
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private SchedulingInput assembleInput(int horizonDays, int defaultDuration) {
        // Load unscheduled cases
        List<Case> pendingCases = caseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> c.getCurrentStatus() == CaseStatus.PENDING)
                .toList();

        List<SchedulingInput.CaseInfo> caseInfos = pendingCases.stream()
                .map(c -> {
                    BigDecimal priority = priorityScoreRepository.findLatestByCaseId(c.getId())
                            .map(PriorityScore::getTotalScore)
                            .orElse(BigDecimal.ONE); // Default priority if never scored
                    return SchedulingInput.CaseInfo.builder()
                            .caseId(c.getId())
                            .caseNumber(c.getCaseNumber())
                            .priorityScore(priority)
                            .linkedCaseId(c.getLinkedCase() != null ? c.getLinkedCase().getId() : null)
                            .estimatedDurationMinutes(0) // use default
                            .build();
                })
                .toList();

        // Load judges with availability
        List<Judge> judges = judgeRepository.findAll();
        List<SchedulingInput.JudgeInfo> judgeInfos = judges.stream()
                .map(j -> SchedulingInput.JudgeInfo.builder()
                        .judgeId(j.getId())
                        .judgeName(j.getName())
                        .availabilityWindows(j.getAvailabilityWindows().stream()
                                .map(w -> SchedulingInput.AvailabilityWindow.builder()
                                        .dayOfWeek(w.getDayOfWeek())
                                        .startTime(w.getStartTime())
                                        .endTime(w.getEndTime())
                                        .build())
                                .toList())
                        .build())
                .toList();

        // Load courtrooms with availability
        List<Courtroom> courtrooms = courtroomRepository.findAll();
        List<SchedulingInput.CourtroomInfo> courtroomInfos = courtrooms.stream()
                .map(cr -> SchedulingInput.CourtroomInfo.builder()
                        .courtroomId(cr.getId())
                        .courtroomName(cr.getName())
                        .availabilityWindows(cr.getAvailability().stream()
                                .map(w -> SchedulingInput.AvailabilityWindow.builder()
                                        .dayOfWeek(w.getDayOfWeek())
                                        .startTime(w.getStartTime())
                                        .endTime(w.getEndTime())
                                        .build())
                                .toList())
                        .build())
                .toList();

        // Load existing scheduled hearings in the horizon
        LocalDate horizonStart = LocalDate.now().plusDays(1); // Start scheduling from tomorrow
        LocalDateTime from = horizonStart.atStartOfDay();
        LocalDateTime to = from.plusDays(horizonDays + 4); // Extra buffer for weekends
        List<Hearing> existingHearings = hearingRepository.findAllByStatusBetween(
                HearingStatus.SCHEDULED, from, to);

        List<SchedulingInput.ExistingHearing> existingHearingInfos = existingHearings.stream()
                .map(h -> SchedulingInput.ExistingHearing.builder()
                        .hearingId(h.getId())
                        .caseId(h.getLegalCase().getId())
                        .judgeId(h.getJudge().getId())
                        .courtroomId(h.getCourtroom().getId())
                        .startTime(h.getScheduledTime())
                        .durationMinutes(h.getEstimatedDurationMinutes())
                        .build())
                .toList();

        // Load previous run's proposals for churn detection
        Map<UUID, SchedulingInput.PreviousAssignment> previousAssignments = new HashMap<>();
        runRepository.findTopByStatusOrderByTriggeredAtDesc(RunStatus.COMPLETED)
                .ifPresent(prevRun -> {
                    List<SchedulingProposal> prevProposals = proposalRepository.findByRunId(prevRun.getId());
                    for (SchedulingProposal pp : prevProposals) {
                        previousAssignments.put(pp.getLegalCase().getId(),
                                SchedulingInput.PreviousAssignment.builder()
                                        .caseId(pp.getLegalCase().getId())
                                        .judgeId(pp.getJudge().getId())
                                        .courtroomId(pp.getCourtroom().getId())
                                        .startTime(pp.getProposedTime())
                                        .build());
                    }
                });

        SchedulingInput.SoftWeights softWeights = new SchedulingInput.SoftWeights(
                config.getSoftWeights().getPriorityOrdering(),
                config.getSoftWeights().getWorkloadBalance(),
                config.getSoftWeights().getScheduleChurn()
        );

        return SchedulingInput.builder()
                .cases(new ArrayList<>(caseInfos))
                .judges(new ArrayList<>(judgeInfos))
                .courtrooms(new ArrayList<>(courtroomInfos))
                .existingHearings(new ArrayList<>(existingHearingInfos))
                .previousAssignments(previousAssignments)
                .horizonStart(horizonStart)
                .horizonDays(horizonDays)
                .defaultDurationMinutes(defaultDuration)
                .softWeights(softWeights)
                .repairMaxIterations(config.getRepairMaxIterations())
                .build();
    }

    private List<ProposalResponse> persistResults(SchedulingRun run, SchedulingResult result) {
        List<ProposalResponse> responses = new ArrayList<>();

        for (SchedulingResult.ProposedAssignment assignment : result.getAssignments()) {
            // Resolve entities
            Case legalCase = caseRepository.findById(assignment.getCaseId()).orElse(null);
            Judge judge = judgeRepository.findById(assignment.getJudgeId()).orElse(null);
            Courtroom courtroom = courtroomRepository.findById(assignment.getCourtroomId()).orElse(null);

            if (legalCase == null || judge == null || courtroom == null) {
                log.warn("Skipping proposal for case {} — entity not found", assignment.getCaseNumber());
                continue;
            }

            // Persist proposal
            SchedulingProposal proposal = SchedulingProposal.builder()
                    .run(run)
                    .legalCase(legalCase)
                    .judge(judge)
                    .courtroom(courtroom)
                    .proposedTime(assignment.getProposedTime())
                    .durationMinutes(assignment.getDurationMinutes())
                    .status(ProposalStatus.PROPOSED)
                    .build();
            proposal = proposalRepository.save(proposal);

            // Persist decision
            SchedulingResult.DecisionRecord dec = assignment.getDecision();
            String constraintsJson;
            try {
                constraintsJson = objectMapper.writeValueAsString(
                        dec != null ? dec.getConstraintsSatisfied() : List.of());
            } catch (JsonProcessingException e) {
                constraintsJson = "[]";
            }

            com.judicialflow.scheduling.model.SchedulingDecision decision =
                    com.judicialflow.scheduling.model.SchedulingDecision.builder()
                            .proposal(proposal)
                            .caseNumber(assignment.getCaseNumber())
                            .chosenJudgeName(assignment.getJudgeName())
                            .chosenCourtroomName(assignment.getCourtroomName())
                            .chosenTime(assignment.getProposedTime())
                            .constraintsSatisfied(constraintsJson)
                            .softScoreChosen(dec != null ? dec.getChosenSoftScore() : BigDecimal.ZERO)
                            .explanation(dec != null ? dec.getExplanation() : "No decision record")
                            .build();

            if (dec != null && dec.getRunnerUpJudgeName() != null) {
                decision.setRunnerUpJudgeName(dec.getRunnerUpJudgeName());
                decision.setRunnerUpCourtroomName(dec.getRunnerUpCourtroomName());
                decision.setRunnerUpTime(dec.getRunnerUpTime());
                decision.setSoftScoreRunnerUp(dec.getRunnerUpSoftScore());
                decision.setRunnerUpRejectionReason(dec.getRunnerUpRejectionReason());
            }

            decision = decisionRepository.save(decision);

            // Build response
            responses.add(mapToProposalResponse(proposal, decision));
        }

        return responses;
    }

    private ProposalResponse mapToProposalResponse(SchedulingProposal proposal,
                                                    com.judicialflow.scheduling.model.SchedulingDecision decision) {
        DecisionLogResponse decisionLog = null;
        if (decision != null) {
            List<String> constraints;
            try {
                constraints = objectMapper.readValue(decision.getConstraintsSatisfied(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                constraints = List.of();
            }

            decisionLog = DecisionLogResponse.builder()
                    .caseNumber(decision.getCaseNumber())
                    .chosenJudgeName(decision.getChosenJudgeName())
                    .chosenCourtroomName(decision.getChosenCourtroomName())
                    .chosenTime(decision.getChosenTime())
                    .chosenSoftScore(decision.getSoftScoreChosen())
                    .runnerUpJudgeName(decision.getRunnerUpJudgeName())
                    .runnerUpCourtroomName(decision.getRunnerUpCourtroomName())
                    .runnerUpTime(decision.getRunnerUpTime())
                    .runnerUpSoftScore(decision.getSoftScoreRunnerUp())
                    .runnerUpRejectionReason(decision.getRunnerUpRejectionReason())
                    .constraintsSatisfied(constraints)
                    .explanation(decision.getExplanation())
                    .build();
        }

        BigDecimal priority = priorityScoreRepository.findLatestByCaseId(proposal.getLegalCase().getId())
                .map(PriorityScore::getTotalScore)
                .orElse(null);

        return ProposalResponse.builder()
                .proposalId(proposal.getId())
                .caseId(proposal.getLegalCase().getId())
                .caseNumber(proposal.getLegalCase().getCaseNumber())
                .casePriorityScore(priority)
                .judgeId(proposal.getJudge().getId())
                .judgeName(proposal.getJudge().getName())
                .courtroomId(proposal.getCourtroom().getId())
                .courtroomName(proposal.getCourtroom().getName())
                .proposedTime(proposal.getProposedTime())
                .durationMinutes(proposal.getDurationMinutes())
                .status(proposal.getStatus().name())
                .decisionLog(decisionLog)
                .build();
    }
}
