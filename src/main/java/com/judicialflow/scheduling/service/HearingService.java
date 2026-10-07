package com.judicialflow.scheduling.service;

import com.judicialflow.audit.AuditService;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.HearingRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.Hearing;
import com.judicialflow.common.models.Judge;
import com.judicialflow.ingestion.exceptions.ConflictException;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.scheduling.dto.HearingResponse;
import com.judicialflow.scheduling.dto.ReassignHearingRequest;
import com.judicialflow.scheduling.event.HearingRescheduledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HearingService {

    private final HearingRepository hearingRepository;
    private final JudgeRepository judgeRepository;
    private final CourtroomRepository courtroomRepository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.judicialflow.security.UserRepository userRepository;

    @Transactional
    public HearingResponse reassignHearing(UUID hearingId, ReassignHearingRequest request) {
        log.info("Reassigning hearing id={} with reason: {}", hearingId, request.getReason());

        Hearing hearing = hearingRepository.findById(hearingId)
                .orElseThrow(() -> new ResourceNotFoundException("Hearing not found with id: " + hearingId));

        Judge newJudge = judgeRepository.findById(request.getJudgeId())
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + request.getJudgeId()));

        Courtroom newCourtroom = courtroomRepository.findById(request.getCourtroomId())
                .orElseThrow(() -> new ResourceNotFoundException("Courtroom not found with id: " + request.getCourtroomId()));

        int duration = (request.getDurationMinutes() != null && request.getDurationMinutes() > 0)
                ? request.getDurationMinutes()
                : hearing.getEstimatedDurationMinutes();

        LocalDateTime newStart = request.getScheduledTime();
        LocalDateTime newEnd = newStart.plusMinutes(duration);

        // 1. Conflict checking: Judge double-booking
        List<Hearing> judgeHearings = hearingRepository.findByJudgeAndStatusBetween(
                newJudge.getId(), HearingStatus.SCHEDULED,
                newStart.minusHours(8), newEnd.plusHours(8));
        for (Hearing h : judgeHearings) {
            if (!h.getId().equals(hearingId)) {
                LocalDateTime hStart = h.getScheduledTime();
                LocalDateTime hEnd = hStart.plusMinutes(h.getEstimatedDurationMinutes());
                if (newStart.isBefore(hEnd) && newEnd.isAfter(hStart)) {
                    throw new ConflictException(String.format(
                            "Judge %s is already booked from %s to %s for hearing %s",
                            newJudge.getName(), hStart, hEnd, h.getId()));
                }
            }
        }

        // 2. Conflict checking: Courtroom double-booking
        List<Hearing> courtroomHearings = hearingRepository.findByCourtroomAndStatusBetween(
                newCourtroom.getId(), HearingStatus.SCHEDULED,
                newStart.minusHours(8), newEnd.plusHours(8));
        for (Hearing h : courtroomHearings) {
            if (!h.getId().equals(hearingId)) {
                LocalDateTime hStart = h.getScheduledTime();
                LocalDateTime hEnd = hStart.plusMinutes(h.getEstimatedDurationMinutes());
                if (newStart.isBefore(hEnd) && newEnd.isAfter(hStart)) {
                    throw new ConflictException(String.format(
                            "Courtroom %s is already booked from %s to %s for hearing %s",
                            newCourtroom.getName(), hStart, hEnd, h.getId()));
                }
            }
        }

        // 3. Capture before state
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("hearingId", hearing.getId().toString());
        beforeState.put("caseId", hearing.getLegalCase().getId().toString());
        beforeState.put("judgeId", hearing.getJudge() != null ? hearing.getJudge().getId().toString() : null);
        beforeState.put("judgeName", hearing.getJudge() != null ? hearing.getJudge().getName() : null);
        beforeState.put("courtroomId", hearing.getCourtroom() != null ? hearing.getCourtroom().getId().toString() : null);
        beforeState.put("courtroomName", hearing.getCourtroom() != null ? hearing.getCourtroom().getName() : null);
        beforeState.put("scheduledTime", hearing.getScheduledTime().toString());
        beforeState.put("durationMinutes", hearing.getEstimatedDurationMinutes());
        beforeState.put("status", hearing.getStatus().name());

        UUID oldJudgeId = hearing.getJudge() != null ? hearing.getJudge().getId() : null;
        UUID oldCourtroomId = hearing.getCourtroom() != null ? hearing.getCourtroom().getId() : null;
        LocalDateTime oldScheduledTime = hearing.getScheduledTime();

        // 4. Update hearing entity
        hearing.setJudge(newJudge);
        hearing.setCourtroom(newCourtroom);
        hearing.setScheduledTime(newStart);
        hearing.setEstimatedDurationMinutes(duration);
        Hearing updatedHearing = hearingRepository.save(hearing);

        // Update associated case details
        Case legalCase = hearing.getLegalCase();
        if (legalCase != null) {
            legalCase.setAssignedJudge(newJudge);
            legalCase.setAssignedCourtroom(newCourtroom);
            legalCase.setNextHearingDate(newStart);
            caseRepository.save(legalCase);
        }

        // 5. Capture after state
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("hearingId", updatedHearing.getId().toString());
        afterState.put("caseId", updatedHearing.getLegalCase().getId().toString());
        afterState.put("judgeId", newJudge.getId().toString());
        afterState.put("judgeName", newJudge.getName());
        afterState.put("courtroomId", newCourtroom.getId().toString());
        afterState.put("courtroomName", newCourtroom.getName());
        afterState.put("scheduledTime", newStart.toString());
        afterState.put("durationMinutes", duration);
        afterState.put("status", updatedHearing.getStatus().name());

        // 6. Audit log
        auditService.log(
                "Hearing",
                updatedHearing.getId().toString(),
                "REASSIGN",
                "HEARING_REASSIGNED",
                beforeState,
                afterState,
                String.format("Reassigned hearing %s to Judge %s in %s at %s. Reason: %s",
                        updatedHearing.getId(), newJudge.getName(), newCourtroom.getName(), newStart, request.getReason())
        );

        // 7. Publish domain event
        eventPublisher.publishEvent(new HearingRescheduledEvent(
                this,
                updatedHearing.getId(),
                legalCase != null ? legalCase.getId() : null,
                newJudge.getId(),
                newCourtroom.getId(),
                oldScheduledTime,
                newStart,
                duration,
                oldJudgeId,
                oldCourtroomId,
                request.getReason(),
                request.getLitigantMessage()
        ));

        return HearingResponse.fromEntity(updatedHearing);
    }

    @Transactional(readOnly = true)
    public List<HearingResponse> listHearings(LocalDateTime from, LocalDateTime to, UUID judgeId, UUID courtroomId) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_JUDGE"))) {
            com.judicialflow.security.User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user == null || user.getJudgeId() == null) {
                // Fail-closed: JUDGE with no linked judge_id or lookup failure gets empty list
                return java.util.Collections.emptyList();
            }
            if (judgeId != null && !judgeId.equals(user.getJudgeId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: Judge can only view their own hearings");
            }
            judgeId = user.getJudgeId();
        }

        final UUID effectiveJudgeId = judgeId;
        org.springframework.data.jpa.domain.Specification<Hearing> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledTime"), to));
            }
            if (effectiveJudgeId != null) {
                predicates.add(cb.equal(root.get("judge").get("id"), effectiveJudgeId));
            }
            if (courtroomId != null) {
                predicates.add(cb.equal(root.get("courtroom").get("id"), courtroomId));
            }
            if (query != null) {
                query.orderBy(cb.asc(root.get("scheduledTime")));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return hearingRepository.findAll(spec).stream()
                .map(HearingResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public HearingResponse getCurrentHearingForCase(UUID caseId) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_JUDGE"))) {
            com.judicialflow.security.User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user == null || user.getJudgeId() == null) {
                // Fail-closed: JUDGE with no linked judge_id or lookup failure gets 403
                throw new org.springframework.security.access.AccessDeniedException("Access denied: Judge has no linked judicial profile");
            }
            Hearing hearing = hearingRepository.findByCaseIdOrderByScheduledTimeDesc(caseId).stream()
                    .findFirst()
                    .orElse(null);
            if (hearing == null) {
                return null;
            }
            if (hearing.getJudge() == null || !user.getJudgeId().equals(hearing.getJudge().getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: Hearing is assigned to another judge");
            }
            return HearingResponse.fromEntity(hearing);
        }

        return hearingRepository.findByCaseIdOrderByScheduledTimeDesc(caseId).stream()
                .findFirst()
                .map(HearingResponse::fromEntity)
                .orElse(null);
    }
}
