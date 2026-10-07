package com.judicialflow.ingestion.services;

import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.models.Judge;
import com.judicialflow.common.models.JudgeAvailabilityWindow;
import com.judicialflow.ingestion.dto.*;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.ingestion.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeService {

    private final JudgeRepository judgeRepository;
    private final com.judicialflow.common.JudgeLeaveRepository judgeLeaveRepository;
    private final com.judicialflow.common.HearingRepository hearingRepository;
    private final com.judicialflow.common.CaseRepository caseRepository;
    private final com.judicialflow.audit.AuditService auditService;

    private java.util.Map<String, Object> judgeToState(Judge j) {
        if (j == null) return null;
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", j.getId().toString());
        map.put("name", j.getName());
        map.put("specialization", j.getSpecialization());
        map.put("availabilityWindows", j.getAvailabilityWindows());
        return map;
    }

    @Transactional
    public JudgeResponse createJudge(CreateJudgeRequest request) {
        log.info("Creating new judge: {}", request.getName());
        validateAvailabilityWindows(request.getAvailabilityWindows());

        Judge judge = Judge.builder()
                .name(request.getName().trim())
                .specialization(request.getSpecialization())
                .availabilityWindows(request.getAvailabilityWindows() != null ? request.getAvailabilityWindows() : List.of())
                .build();

        Judge saved = judgeRepository.save(judge);
        auditService.log("Judge", saved.getId().toString(), "CREATE", "JUDGE_CREATED",
                null, judgeToState(saved), "Created judge " + saved.getName());
        return JudgeResponse.fromEntity(saved);
    }

    @Transactional
    public JudgeResponse updateJudge(UUID id, UpdateJudgeRequest request) {
        log.info("Updating judge with id: {}", id);
        Judge judge = judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + id));

        java.util.Map<String, Object> beforeState = judgeToState(judge);

        judge.setName(request.getName().trim());
        judge.setSpecialization(request.getSpecialization());

        Judge saved = judgeRepository.save(judge);
        auditService.log("Judge", saved.getId().toString(), "UPDATE", "JUDGE_UPDATED",
                beforeState, judgeToState(saved), "Updated judge " + saved.getName());
        return JudgeResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public JudgeResponse getJudgeById(UUID id) {
        Judge judge = judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + id));
        return JudgeResponse.fromEntity(judge);
    }

    @Transactional(readOnly = true)
    public PageResponse<JudgeResponse> listJudges(Pageable pageable) {
        Page<JudgeResponse> page = judgeRepository.findAll(pageable)
                .map(JudgeResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Transactional
    public JudgeResponse setAvailabilityWindows(UUID id, SetJudgeAvailabilityRequest request) {
        log.info("Setting availability windows for judge id: {}", id);
        Judge judge = judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + id));

        java.util.Map<String, Object> beforeState = judgeToState(judge);

        validateAvailabilityWindows(request.getAvailabilityWindows());
        judge.setAvailabilityWindows(request.getAvailabilityWindows() != null ? request.getAvailabilityWindows() : List.of());

        Judge saved = judgeRepository.save(judge);
        auditService.log("Judge", saved.getId().toString(), "UPDATE", "JUDGE_AVAILABILITY_UPDATED",
                beforeState, judgeToState(saved), "Updated availability windows for judge " + saved.getName());
        return JudgeResponse.fromEntity(saved);
    }

    private void validateAvailabilityWindows(List<JudgeAvailabilityWindow> windows) {
        if (windows == null || windows.isEmpty()) return;
        for (JudgeAvailabilityWindow w : windows) {
            if (w.getStartTime() != null && w.getEndTime() != null) {
                if (!w.getStartTime().isBefore(w.getEndTime())) {
                    throw new ValidationException(String.format(
                            "Invalid availability window on %s: startTime (%s) must be before endTime (%s)",
                            w.getDayOfWeek(), w.getStartTime(), w.getEndTime()));
                }
            }
        }
    }

    @Transactional
    public JudgeLeaveResponse recordJudgeLeave(UUID judgeId, JudgeLeaveRequest request) {
        log.info("Recording leave for judge id: {} from {} to {}", judgeId, request.getStartDate(), request.getEndDate());
        Judge judge = judgeRepository.findById(judgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + judgeId));

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new ValidationException("Start date cannot be after end date");
        }

        com.judicialflow.common.models.JudgeLeave leave = com.judicialflow.common.models.JudgeLeave.builder()
                .judge(judge)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason() != null ? request.getReason().trim() : "Judicial Leave")
                .build();
        com.judicialflow.common.models.JudgeLeave savedLeave = judgeLeaveRepository.save(leave);

        // Find all committed hearings for this judge overlapping the leave period
        java.time.LocalDateTime startDt = request.getStartDate().atStartOfDay();
        java.time.LocalDateTime endDt = request.getEndDate().atTime(23, 59, 59);

        java.util.List<com.judicialflow.common.models.Hearing> conflictingHearings = hearingRepository.findAllByStatusBetween(
                com.judicialflow.common.enums.HearingStatus.SCHEDULED, startDt, endDt).stream()
                .filter(h -> h.getJudge() != null && h.getJudge().getId().equals(judgeId))
                .toList();

        java.util.List<String> affectedCaseNumbers = new java.util.ArrayList<>();
        for (com.judicialflow.common.models.Hearing h : conflictingHearings) {
            h.setStatus(com.judicialflow.common.enums.HearingStatus.ADJOURNED);
            hearingRepository.save(h);

            com.judicialflow.common.models.Case c = h.getLegalCase();
            if (c != null) {
                c.setCurrentStatus(com.judicialflow.common.enums.CaseStatus.ADJOURNED);
                c.setNextHearingDate(null);
                c.setPriorAdjournments(c.getPriorAdjournments() + 1);
                caseRepository.save(c);
                affectedCaseNumbers.add(c.getCaseNumber());
            }

            auditService.log("Hearing", h.getId().toString(), "ADJOURN", "JUDGE_LEAVE_RESCHEDULE_NEEDED",
                    java.util.Map.of("hearingId", h.getId().toString(), "status", "SCHEDULED", "judgeId", judgeId.toString()),
                    java.util.Map.of("hearingId", h.getId().toString(), "status", "ADJOURNED", "reason", leave.getReason()),
                    String.format("Hearing for case %s adjourned due to Judge %s on leave (%s to %s). Queued for rescheduling.",
                            c != null ? c.getCaseNumber() : "N/A", judge.getName(), request.getStartDate(), request.getEndDate()));
        }

        auditService.log("JudgeLeave", savedLeave.getId().toString(), "CREATE", "JUDGE_LEAVE_REGISTERED",
                null,
                java.util.Map.of("judgeId", judgeId.toString(), "judgeName", judge.getName(), "startDate", request.getStartDate().toString(), "endDate", request.getEndDate().toString(), "adjournedHearings", conflictingHearings.size()),
                String.format("Registered leave for Judge %s from %s to %s. %d hearings adjourned for rescheduling.",
                        judge.getName(), request.getStartDate(), request.getEndDate(), conflictingHearings.size()));

        return JudgeLeaveResponse.builder()
                .id(savedLeave.getId())
                .judgeId(judgeId)
                .judgeName(judge.getName())
                .startDate(savedLeave.getStartDate())
                .endDate(savedLeave.getEndDate())
                .reason(savedLeave.getReason())
                .affectedHearingsCount(conflictingHearings.size())
                .affectedCaseNumbers(affectedCaseNumbers)
                .message(String.format("Leave recorded for Judge %s. %d hearing(s) adjourned and placed back in pool for rescheduling.", judge.getName(), conflictingHearings.size()))
                .createdAt(savedLeave.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<JudgeLeaveResponse> getJudgeLeaves(UUID judgeId) {
        Judge judge = judgeRepository.findById(judgeId)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + judgeId));
        return judgeLeaveRepository.findByJudgeIdOrderByStartDateAsc(judgeId).stream()
                .map(l -> JudgeLeaveResponse.builder()
                        .id(l.getId())
                        .judgeId(judge.getId())
                        .judgeName(judge.getName())
                        .startDate(l.getStartDate())
                        .endDate(l.getEndDate())
                        .reason(l.getReason())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void deleteJudgeLeave(UUID judgeId, UUID leaveId) {
        com.judicialflow.common.models.JudgeLeave leave = judgeLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Judge leave not found with id: " + leaveId));
        if (!leave.getJudge().getId().equals(judgeId)) {
            throw new ValidationException("Leave does not belong to judge: " + judgeId);
        }
        judgeLeaveRepository.delete(leave);
        auditService.log("JudgeLeave", leaveId.toString(), "DELETE", "JUDGE_LEAVE_CANCELLED",
                java.util.Map.of("judgeId", judgeId.toString(), "leaveId", leaveId.toString()),
                null,
                "Cancelled leave " + leaveId + " for judge " + judgeId);
    }
}
