package com.judicialflow.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judicialflow.audit.AuditService;
import com.judicialflow.common.*;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.*;
import com.judicialflow.ingestion.exceptions.ConflictException;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import com.judicialflow.scheduling.config.SchedulingConfig;
import com.judicialflow.scheduling.dto.ManualOverrideRequest;
import com.judicialflow.scheduling.dto.ProposalResponse;
import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.repository.*;
import com.judicialflow.scheduling.service.SchedulingConcurrencyGuard;
import com.judicialflow.scheduling.service.SchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceUnitTest {

    @Mock private CaseRepository caseRepository;
    @Mock private JudgeRepository judgeRepository;
    @Mock private CourtroomRepository courtroomRepository;
    @Mock private HearingRepository hearingRepository;
    @Mock private JudgeLeaveRepository judgeLeaveRepository;
    @Mock private PriorityScoreRepository priorityScoreRepository;
    @Mock private SchedulingRunRepository runRepository;
    @Mock private SchedulingProposalRepository proposalRepository;
    @Mock private SchedulingDecisionRepository decisionRepository;
    @Mock private SchedulingOverrideRepository overrideRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;
    @Mock private SchedulingConcurrencyGuard concurrencyGuard;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SchedulingService schedulingService;
    private SchedulingConfig config;

    private UUID caseId;
    private UUID judgeId;
    private UUID courtroomId;
    private Case mockCase;
    private Judge mockJudge;
    private Courtroom mockCourtroom;

    @BeforeEach
    void setUp() {
        config = new SchedulingConfig();
        config.setDefaultHearingDurationMinutes(60);

        schedulingService = new SchedulingService(
                caseRepository,
                judgeRepository,
                courtroomRepository,
                hearingRepository,
                judgeLeaveRepository,
                priorityScoreRepository,
                runRepository,
                proposalRepository,
                decisionRepository,
                overrideRepository,
                auditLogRepository,
                auditService,
                config,
                new ObjectMapper(),
                concurrencyGuard,
                eventPublisher
        );

        caseId = UUID.randomUUID();
        judgeId = UUID.randomUUID();
        courtroomId = UUID.randomUUID();

        mockCase = Case.builder()
                .id(caseId)
                .caseNumber("SYN-2026-TEST-01")
                .currentStatus(CaseStatus.FILED)
                .build();

        mockJudge = Judge.builder()
                .id(judgeId)
                .name("Hon. Justice Roy")
                .build();

        mockCourtroom = Courtroom.builder()
                .id(courtroomId)
                .name("Courtroom 301")
                .build();
    }

    @Test
    @DisplayName("applyManualOverride successfully allocates courtroom, judge, and sets NEXT hearing date")
    void testApplyManualOverrideSuccess() {
        LocalDateTime scheduledTime = LocalDateTime.of(2026, 10, 15, 10, 0);

        when(caseRepository.findByIdAndDeletedFalse(caseId)).thenReturn(Optional.of(mockCase));
        when(judgeRepository.findById(judgeId)).thenReturn(Optional.of(mockJudge));
        when(courtroomRepository.findById(courtroomId)).thenReturn(Optional.of(mockCourtroom));
        when(judgeLeaveRepository.existsByJudgeIdAndDate(judgeId, scheduledTime.toLocalDate())).thenReturn(false);
        when(hearingRepository.findPotentialConflicts(eq(judgeId), eq(courtroomId), eq(HearingStatus.SCHEDULED), any(), any()))
                .thenReturn(Collections.emptyList());
        when(hearingRepository.save(any(Hearing.class))).thenAnswer(i -> {
            Hearing h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        ManualOverrideRequest request = ManualOverrideRequest.builder()
                .caseId(caseId)
                .judgeId(judgeId)
                .courtroomId(courtroomId)
                .scheduledTime(scheduledTime)
                .durationMinutes(60)
                .reason("Urgent mention direct allocation")
                .overriddenBy("registrar_admin")
                .build();

        ProposalResponse response = schedulingService.applyManualOverride(request);

        assertThat(response).isNotNull();
        assertThat(response.getCaseId()).isEqualTo(caseId);
        assertThat(response.getJudgeId()).isEqualTo(judgeId);
        assertThat(response.getCourtroomId()).isEqualTo(courtroomId);
        assertThat(response.getProposedTime()).isEqualTo(scheduledTime);
        assertThat(response.getStatus()).isEqualTo("OVERRIDDEN");

        // Verify case was updated
        assertThat(mockCase.getCurrentStatus()).isEqualTo(CaseStatus.SCHEDULED);
        assertThat(mockCase.getAssignedJudge()).isEqualTo(mockJudge);
        assertThat(mockCase.getAssignedCourtroom()).isEqualTo(mockCourtroom);
        assertThat(mockCase.getNextHearingDate()).isEqualTo(scheduledTime);
        verify(caseRepository).save(mockCase);

        // Verify proposal repository updated old proposed state
        verify(proposalRepository).updateStatusByCaseIdAndOldStatus(caseId, ProposalStatus.PROPOSED, ProposalStatus.OVERRIDDEN);
    }

    @Test
    @DisplayName("applyManualOverride throws ConflictException when judge is on approved leave")
    void testApplyManualOverrideFailsOnJudgeLeave() {
        LocalDateTime scheduledTime = LocalDateTime.of(2026, 10, 15, 10, 0);

        when(caseRepository.findByIdAndDeletedFalse(caseId)).thenReturn(Optional.of(mockCase));
        when(judgeRepository.findById(judgeId)).thenReturn(Optional.of(mockJudge));
        when(courtroomRepository.findById(courtroomId)).thenReturn(Optional.of(mockCourtroom));
        when(judgeLeaveRepository.existsByJudgeIdAndDate(judgeId, scheduledTime.toLocalDate())).thenReturn(true);

        ManualOverrideRequest request = ManualOverrideRequest.builder()
                .caseId(caseId)
                .judgeId(judgeId)
                .courtroomId(courtroomId)
                .scheduledTime(scheduledTime)
                .durationMinutes(60)
                .reason("Urgent mention direct allocation")
                .overriddenBy("registrar_admin")
                .build();

        assertThatThrownBy(() -> schedulingService.applyManualOverride(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("on approved leave");

        verify(hearingRepository, never()).save(any());
        verify(caseRepository, never()).save(mockCase);
    }

    @Test
    @DisplayName("applyManualOverride throws ConflictException when courtroom has overlapping hearing")
    void testApplyManualOverrideFailsOnCourtroomConflict() {
        LocalDateTime scheduledTime = LocalDateTime.of(2026, 10, 15, 10, 0);

        when(caseRepository.findByIdAndDeletedFalse(caseId)).thenReturn(Optional.of(mockCase));
        when(judgeRepository.findById(judgeId)).thenReturn(Optional.of(mockJudge));
        when(courtroomRepository.findById(courtroomId)).thenReturn(Optional.of(mockCourtroom));
        when(judgeLeaveRepository.existsByJudgeIdAndDate(judgeId, scheduledTime.toLocalDate())).thenReturn(false);

        Hearing conflictingHearing = Hearing.builder()
                .id(UUID.randomUUID())
                .judge(Judge.builder().id(UUID.randomUUID()).name("Other Judge").build())
                .courtroom(mockCourtroom)
                .scheduledTime(LocalDateTime.of(2026, 10, 15, 10, 30))
                .estimatedDurationMinutes(60)
                .status(HearingStatus.SCHEDULED)
                .build();

        when(hearingRepository.findPotentialConflicts(eq(judgeId), eq(courtroomId), eq(HearingStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(conflictingHearing));

        ManualOverrideRequest request = ManualOverrideRequest.builder()
                .caseId(caseId)
                .judgeId(judgeId)
                .courtroomId(courtroomId)
                .scheduledTime(scheduledTime)
                .durationMinutes(60)
                .reason("Urgent mention direct allocation")
                .overriddenBy("registrar_admin")
                .build();

        assertThatThrownBy(() -> schedulingService.applyManualOverride(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Courtroom Courtroom 301 is already booked");

        verify(hearingRepository, never()).save(any());
        verify(caseRepository, never()).save(mockCase);
    }
}
