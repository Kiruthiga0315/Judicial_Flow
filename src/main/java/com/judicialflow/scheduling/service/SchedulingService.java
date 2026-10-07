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
import com.judicialflow.ingestion.exceptions.ConflictException;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.scheduling.config.SchedulingConfig;
import com.judicialflow.scheduling.dto.*;
import com.judicialflow.scheduling.engine.SchedulingEngine;
import com.judicialflow.scheduling.engine.SchedulingInput;
import com.judicialflow.scheduling.engine.SchedulingResult;
import com.judicialflow.scheduling.event.HearingCommittedEvent;
import com.judicialflow.scheduling.exception.SchedulingConflictException;
import com.judicialflow.scheduling.model.*;
import com.judicialflow.scheduling.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *   <li>Load unscheduled cases (FILED, not deleted) with their latest priority scores.</li>
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
    private final com.judicialflow.audit.AuditService auditService;
    private final SchedulingConfig config;
    private final ObjectMapper objectMapper;
    private final SchedulingConcurrencyGuard concurrencyGuard;
    private final ApplicationEventPublisher eventPublisher;

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
        UUID prospectiveRunId = UUID.randomUUID();
        Optional<UUID> runningId = concurrencyGuard.tryAcquire(prospectiveRunId);
        if (runningId.isPresent()) {
            throw new SchedulingConflictException(runningId.get());
        }

        int horizonDays = configDto != null && configDto.getHorizonDays() != null
                ? configDto.getHorizonDays() : config.getHorizonDays();
        int defaultDuration = configDto != null && configDto.getDefaultDurationMinutes() != null
                ? configDto.getDefaultDurationMinutes() : config.getDefaultHearingDurationMinutes();
        long seed = configDto != null && configDto.getSeed() != null ? configDto.getSeed() : 42L;

        log.info("Triggering scheduling run: horizonDays={}, defaultDuration={}", horizonDays, defaultDuration);

        // 1. Create the run record
        SchedulingRun run = SchedulingRun.builder()
                .id(prospectiveRunId)
                .status(RunStatus.RUNNING)
                .horizonDays(horizonDays)
                .defaultDurationMinutes(defaultDuration)
                .seed(seed)
                .build();
        run = runRepository.save(run);

        try {
            // 2. Assemble engine input
            SchedulingInput input = assembleInput(horizonDays, defaultDuration);
            run.setTotalCasesInput(input.getCases().size());
            run = runRepository.save(run);

            // 3. Run the engine
            SchedulingEngine engine = new SchedulingEngine();
            SchedulingResult result = engine.solve(input, seed);

            // 4. Persist proposals and decisions
            List<ProposalResponse> proposalResponses = persistResults(run, result);

            // 5. Update run status
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            run.setTotalAssigned(result.getTotalAssigned());
            run.setTotalUnschedulable(result.getTotalUnschedulable());
            run.setTotalWeightedSoftCost(result.getTotalWeightedSoftCost());
            
            try {
                run.setCostBreakdown(objectMapper.writeValueAsString(result.getCostBreakdown()));
            } catch (Exception e) {
                log.warn("Could not serialize cost breakdown", e);
            }

            run = runRepository.save(run);

            // Audit the scheduling run with summary details
            Map<String, Object> runSummary = new LinkedHashMap<>();
            runSummary.put("runId", run.getId().toString());
            runSummary.put("status", run.getStatus().name());
            runSummary.put("totalInput", run.getTotalCasesInput());
            runSummary.put("totalAssigned", run.getTotalAssigned());
            runSummary.put("totalUnschedulable", run.getTotalUnschedulable());
            runSummary.put("seed", run.getSeed());
            runSummary.put("horizonDays", run.getHorizonDays());
            runSummary.put("defaultDurationMinutes", run.getDefaultDurationMinutes());
            runSummary.put("weightedCost", run.getTotalWeightedSoftCost());

            auditService.logSystem(
                    "SchedulingRun",
                    run.getId().toString(),
                    "SCHEDULING_RUN",
                    "ENGINE_RUN_COMPLETED",
                    null,
                    runSummary,
                    String.format("Scheduling run completed: runId=%s, assigned=%d, unschedulable=%d, seed=%d",
                            run.getId(), run.getTotalAssigned(), run.getTotalUnschedulable(), run.getSeed())
            );

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
                    .seed(seed)
                    .totalWeightedSoftCost(result.getTotalWeightedSoftCost())
                    .costBreakdown(result.getCostBreakdown())
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
        } finally {
            concurrencyGuard.release();
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
     * Retrieve the latest completed scheduling run.
     */
    @Transactional(readOnly = true)
    public SchedulingRunResponse getLatestRun() {
        SchedulingRun run = runRepository.findTopByStatusOrderByTriggeredAtDesc(RunStatus.COMPLETED)
                .orElseThrow(() -> new ResourceNotFoundException("No completed scheduling run found"));
        return getSchedulingRun(run.getId());
    }

    /**
     * Retrieve proposals from the latest completed scheduling run.
     */
    @Transactional(readOnly = true)
    public List<ProposalResponse> getLatestProposals() {
        return getLatestRun().getProposals();
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
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("caseId", legalCase.getId().toString());
        beforeState.put("status", legalCase.getCurrentStatus() != null ? legalCase.getCurrentStatus().name() : null);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("hearingId", hearing.getId().toString());
        afterState.put("caseId", legalCase.getId().toString());
        afterState.put("judgeId", judge.getId().toString());
        afterState.put("judgeName", judge.getName());
        afterState.put("courtroomId", courtroom.getId().toString());
        afterState.put("courtroomName", courtroom.getName());
        afterState.put("scheduledTime", request.getScheduledTime().toString());
        afterState.put("durationMinutes", duration);

        auditService.log(
                "Hearing",
                hearing.getId().toString(),
                "MANUAL_OVERRIDE",
                "REGISTRAR_OVERRIDE",
                beforeState,
                afterState,
                String.format(
                        "Manual override for case %s: assigned to Judge %s in %s at %s. Reason: %s",
                        legalCase.getCaseNumber(), judge.getName(), courtroom.getName(),
                        request.getScheduledTime(), request.getReason())
        );

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

    /**
     * Approve a proposal: commits a Hearing, updates Case status and assignments,
     * audits the change, and publishes HearingCommittedEvent.
     */
    @Transactional
    public ProposalResponse approveProposal(UUID proposalId) {
        SchedulingProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduling proposal not found: " + proposalId));

        if (proposal.getStatus() != ProposalStatus.PROPOSED) {
            throw new ConflictException("Proposal cannot be approved because it has status: " + proposal.getStatus());
        }

        // Re-validate conflict-free hard constraints:
        // Judge and Courtroom must not be already booked for another committed hearing in overlapping time slot
        LocalDateTime slotStart = proposal.getProposedTime();
        LocalDateTime slotEnd = slotStart.plusMinutes(proposal.getDurationMinutes());

        List<Hearing> potentialConflicts = hearingRepository.findPotentialConflicts(
                proposal.getJudge().getId(),
                proposal.getCourtroom().getId(),
                HearingStatus.SCHEDULED,
                slotStart.minusHours(24),
                slotEnd.plusHours(24)
        );

        for (Hearing h : potentialConflicts) {
            LocalDateTime hStart = h.getScheduledTime();
            LocalDateTime hEnd = hStart.plusMinutes(h.getEstimatedDurationMinutes());
            if (slotStart.isBefore(hEnd) && hStart.isBefore(slotEnd)) {
                if (h.getJudge().getId().equals(proposal.getJudge().getId())) {
                    throw new ConflictException(String.format(
                            "Proposed slot conflict: Judge %s is already scheduled for another hearing at %s",
                            proposal.getJudge().getName(), hStart));
                }
                if (h.getCourtroom().getId().equals(proposal.getCourtroom().getId())) {
                    throw new ConflictException(String.format(
                            "Proposed slot conflict: Courtroom %s is already scheduled for another hearing at %s",
                            proposal.getCourtroom().getName(), hStart));
                }
            }
        }

        // 1. Create committed Hearing
        Hearing hearing = Hearing.builder()
                .legalCase(proposal.getLegalCase())
                .judge(proposal.getJudge())
                .courtroom(proposal.getCourtroom())
                .scheduledTime(proposal.getProposedTime())
                .estimatedDurationMinutes(proposal.getDurationMinutes())
                .status(HearingStatus.SCHEDULED)
                .createdByEngine(true)
                .build();
        hearing = hearingRepository.save(hearing);

        // 2. Update Case
        Case legalCase = proposal.getLegalCase();
        Map<String, Object> beforeMap = new LinkedHashMap<>();
        beforeMap.put("proposalId", proposal.getId().toString());
        beforeMap.put("caseId", legalCase.getId().toString());
        beforeMap.put("caseStatus", legalCase.getCurrentStatus() != null ? legalCase.getCurrentStatus().name() : null);
        beforeMap.put("assignedJudgeId", legalCase.getAssignedJudge() != null ? legalCase.getAssignedJudge().getId().toString() : null);
        beforeMap.put("assignedCourtroomId", legalCase.getAssignedCourtroom() != null ? legalCase.getAssignedCourtroom().getId().toString() : null);
        beforeMap.put("nextHearingDate", legalCase.getNextHearingDate() != null ? legalCase.getNextHearingDate().toString() : null);
        beforeMap.put("proposalStatus", ProposalStatus.PROPOSED.name());

        legalCase.setCurrentStatus(CaseStatus.SCHEDULED);
        legalCase.setAssignedJudge(proposal.getJudge());
        legalCase.setAssignedCourtroom(proposal.getCourtroom());
        legalCase.setNextHearingDate(proposal.getProposedTime());
        caseRepository.save(legalCase);

        // 3. Update proposal status
        proposal.setStatus(ProposalStatus.APPROVED);
        proposal = proposalRepository.save(proposal);

        // 4. Audit Log
        Map<String, Object> afterMap = new LinkedHashMap<>();
        afterMap.put("proposalId", proposal.getId().toString());
        afterMap.put("hearingId", hearing.getId().toString());
        afterMap.put("caseId", legalCase.getId().toString());
        afterMap.put("caseStatus", legalCase.getCurrentStatus().name());
        afterMap.put("assignedJudgeId", proposal.getJudge().getId().toString());
        afterMap.put("assignedCourtroomId", proposal.getCourtroom().getId().toString());
        afterMap.put("nextHearingDate", proposal.getProposedTime().toString());
        afterMap.put("proposalStatus", ProposalStatus.APPROVED.name());

        auditService.log(
                "Proposal",
                proposal.getId().toString(),
                "APPROVE",
                "PROPOSAL_APPROVED",
                beforeMap,
                afterMap,
                String.format("Approved proposal %s for case %s. Hearing %s created.",
                        proposal.getId(), legalCase.getCaseNumber(), hearing.getId())
        );

        // 5. Publish domain event
        eventPublisher.publishEvent(new HearingCommittedEvent(
                this, hearing.getId(), legalCase.getId(),
                proposal.getJudge().getId(), proposal.getCourtroom().getId(),
                proposal.getId(), proposal.getProposedTime(), proposal.getDurationMinutes()
        ));

        // 6. Map to ProposalResponse
        com.judicialflow.scheduling.model.SchedulingDecision dec =
                decisionRepository.findById(proposal.getId()).orElse(null);
        return mapToProposalResponse(proposal, dec);
    }

    /**
     * Reject a proposal: marks REJECTED with reason and records audit entry.
     */
    @Transactional
    public ProposalResponse rejectProposal(UUID proposalId, String reason) {
        SchedulingProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduling proposal not found: " + proposalId));

        if (proposal.getStatus() != ProposalStatus.PROPOSED) {
            throw new ConflictException("Proposal cannot be rejected because it has status: " + proposal.getStatus());
        }

        Map<String, Object> beforeMap = new LinkedHashMap<>();
        beforeMap.put("proposalId", proposal.getId().toString());
        beforeMap.put("caseId", proposal.getLegalCase().getId().toString());
        beforeMap.put("status", ProposalStatus.PROPOSED.name());

        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setRejectionReason(reason);
        proposal = proposalRepository.save(proposal);

        Map<String, Object> afterMap = new LinkedHashMap<>();
        afterMap.put("proposalId", proposal.getId().toString());
        afterMap.put("caseId", proposal.getLegalCase().getId().toString());
        afterMap.put("status", ProposalStatus.REJECTED.name());
        afterMap.put("rejectionReason", reason);

        auditService.log(
                "Proposal",
                proposal.getId().toString(),
                "REJECT",
                "PROPOSAL_REJECTED",
                beforeMap,
                afterMap,
                String.format("Rejected proposal %s for case %s. Reason: %s",
                        proposal.getId(), proposal.getLegalCase().getCaseNumber(), reason)
        );

        com.judicialflow.scheduling.model.SchedulingDecision dec =
                decisionRepository.findById(proposal.getId()).orElse(null);
        return mapToProposalResponse(proposal, dec);
    }

    private String getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private SchedulingInput assembleInput(int horizonDays, int defaultDuration) {
        // Find all cases that already have active committed hearings — exclude them unless flagged
        Set<UUID> casesWithCommittedHearings = hearingRepository.findAllByStatus(HearingStatus.SCHEDULED).stream()
                .map(h -> h.getLegalCase().getId())
                .collect(Collectors.toSet());

        // Load unscheduled cases
        List<Case> pendingCases = caseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> c.getCurrentStatus() != null && c.getCurrentStatus().isSchedulable())
                .filter(c -> !casesWithCommittedHearings.contains(c.getId()))
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
        if (result.getAssignments().isEmpty()) {
            return responses;
        }

        // Bulk-supersede previous PROPOSED proposals for assigned cases in one single query
        List<UUID> assignedCaseIds = result.getAssignments().stream()
                .map(SchedulingResult.ProposedAssignment::getCaseId)
                .toList();
        proposalRepository.updateStatusByCaseIdsAndOldStatus(
                assignedCaseIds, ProposalStatus.PROPOSED, ProposalStatus.SUPERSEDED);

        // Preload entities into maps for O(1) resolution
        Map<UUID, Case> caseMap = caseRepository.findAllById(assignedCaseIds).stream()
                .collect(Collectors.toMap(Case::getId, c -> c));
        Map<UUID, Judge> judgeMap = judgeRepository.findAll().stream()
                .collect(Collectors.toMap(Judge::getId, j -> j));
        Map<UUID, Courtroom> courtroomMap = courtroomRepository.findAll().stream()
                .collect(Collectors.toMap(Courtroom::getId, c -> c));

        List<SchedulingProposal> proposalsToSave = new ArrayList<>(result.getAssignments().size());
        List<SchedulingResult.DecisionRecord> decisionsData = new ArrayList<>(result.getAssignments().size());
        List<SchedulingResult.ProposedAssignment> validAssignments = new ArrayList<>(result.getAssignments().size());

        for (SchedulingResult.ProposedAssignment assignment : result.getAssignments()) {
            Case legalCase = caseMap.get(assignment.getCaseId());
            Judge judge = judgeMap.get(assignment.getJudgeId());
            Courtroom courtroom = courtroomMap.get(assignment.getCourtroomId());

            if (legalCase == null || judge == null || courtroom == null) {
                log.warn("Skipping proposal for case {} — entity not found", assignment.getCaseNumber());
                continue;
            }

            SchedulingProposal proposal = SchedulingProposal.builder()
                    .run(run)
                    .legalCase(legalCase)
                    .judge(judge)
                    .courtroom(courtroom)
                    .proposedTime(assignment.getProposedTime())
                    .durationMinutes(assignment.getDurationMinutes())
                    .status(ProposalStatus.PROPOSED)
                    .build();

            proposalsToSave.add(proposal);
            decisionsData.add(assignment.getDecision());
            validAssignments.add(assignment);
        }

        List<SchedulingProposal> savedProposals = proposalRepository.saveAll(proposalsToSave);
        List<com.judicialflow.scheduling.model.SchedulingDecision> decisionsToSave = new ArrayList<>(savedProposals.size());

        for (int i = 0; i < savedProposals.size(); i++) {
            SchedulingProposal proposal = savedProposals.get(i);
            SchedulingResult.DecisionRecord dec = decisionsData.get(i);
            SchedulingResult.ProposedAssignment assignment = validAssignments.get(i);

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

            decisionsToSave.add(decision);
        }

        List<com.judicialflow.scheduling.model.SchedulingDecision> savedDecisions = decisionRepository.saveAll(decisionsToSave);

        for (int i = 0; i < savedProposals.size(); i++) {
            responses.add(mapToProposalResponse(savedProposals.get(i), savedDecisions.get(i)));
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
