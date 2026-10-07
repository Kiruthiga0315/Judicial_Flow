package com.judicialflow.notification.listener;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.models.Case;
import com.judicialflow.notification.service.EmailService;
import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import com.judicialflow.scheduling.event.HearingCommittedEvent;
import com.judicialflow.scheduling.event.HearingRescheduledEvent;
import com.judicialflow.scheduling.event.NightlyRunCompletedEvent;
import com.judicialflow.security.UserRepository;
import com.judicialflow.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final CaseRepository caseRepository;
    private final JudgeRepository judgeRepository;
    private final CourtroomRepository courtroomRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${notification.reprioritization-digest.score-delta-threshold:5.0}")
    private double scoreDeltaThreshold;

    @Value("${court.zone-id:Asia/Kolkata}")
    private String courtZoneId;

    private static final java.time.format.DateTimeFormatter TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z", java.util.Locale.US);

    private String formatInIst(java.time.LocalDateTime ldt) {
        if (ldt == null) return "TBD";
        return ldt.atZone(java.time.ZoneId.of(courtZoneId)).format(TIME_FORMATTER);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHearingCommitted(HearingCommittedEvent event) {
        log.info("Processing HearingCommittedEvent for hearing={}, case={}", event.getHearingId(), event.getCaseId());

        Case courtCase = caseRepository.findById(event.getCaseId()).orElse(null);
        if (courtCase == null) {
            log.warn("Case not found for id {}. Skipping scheduled hearing email.", event.getCaseId());
            return;
        }

        String litigantEmail = courtCase.getLitigantContactEmail();
        if (litigantEmail == null || litigantEmail.isBlank()) {
            log.warn("Litigant contact email is null or empty for case {}. Skipping scheduled hearing notification.",
                    courtCase.getCaseNumber());
            return;
        }

        String judgeName = judgeRepository.findById(event.getJudgeId())
                .map(j -> j.getName()).orElse("Unknown Judge");
        String courtroomName = courtroomRepository.findById(event.getCourtroomId())
                .map(c -> c.getName()).orElse("Unknown Courtroom");

        String formattedTime = formatInIst(event.getScheduledTime());

        String subject = "Hearing Scheduled: Case " + courtCase.getCaseNumber();
        String body = String.format("""
                Dear Litigant,

                Your court hearing has been officially scheduled:
                - Case Number: %s
                - Judge: %s
                - Courtroom: %s
                - Scheduled Time: %s
                - Estimated Duration: %d minutes

                Regards,
                JudicialFlow Registry
                """,
                courtCase.getCaseNumber(), judgeName, courtroomName, formattedTime, event.getDurationMinutes());

        emailService.sendEmailWithRetry("HEARING_SCHEDULED", litigantEmail, courtCase.getId(), subject, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHearingRescheduled(HearingRescheduledEvent event) {
        log.info("Processing HearingRescheduledEvent for hearing={}, case={}", event.getHearingId(), event.getCaseId());

        Case courtCase = caseRepository.findById(event.getCaseId()).orElse(null);
        if (courtCase == null) {
            log.warn("Case not found for id {}. Skipping rescheduled hearing email.", event.getCaseId());
            return;
        }

        String litigantEmail = courtCase.getLitigantContactEmail();
        if (litigantEmail == null || litigantEmail.isBlank()) {
            log.warn("Litigant contact email is null or empty for case {}. Skipping rescheduled hearing notification.",
                    courtCase.getCaseNumber());
            return;
        }

        String judgeName = judgeRepository.findById(event.getJudgeId())
                .map(j -> j.getName()).orElse("Unknown Judge");
        String courtroomName = courtroomRepository.findById(event.getCourtroomId())
                .map(c -> c.getName()).orElse("Unknown Courtroom");

        String formattedNewTime = formatInIst(event.getNewScheduledTime());
        String formattedPrevTime = formatInIst(event.getPreviousScheduledTime());

        String litigantNotice = (event.getLitigantMessage() != null && !event.getLitigantMessage().isBlank())
                ? event.getLitigantMessage()
                : "Administrative court schedule adjustment";

        String subject = "Hearing Rescheduled: Case " + courtCase.getCaseNumber();
        String body = String.format("""
                Dear Litigant,

                Your court hearing has been rescheduled:
                - Case Number: %s
                - Judge: %s
                - Courtroom: %s
                - New Scheduled Time: %s
                - Previous Time: %s
                - Duration: %d minutes
                - Notice: %s

                Regards,
                JudicialFlow Registry
                """,
                courtCase.getCaseNumber(), judgeName, courtroomName,
                formattedNewTime, formattedPrevTime,
                event.getDurationMinutes(), litigantNotice);

        emailService.sendEmailWithRetry("HEARING_RESCHEDULED", litigantEmail, courtCase.getId(), subject, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNightlyRunCompleted(NightlyRunCompletedEvent event) {
        log.info("Processing NightlyRunCompletedEvent for run={}", event.getRunId());

        BigDecimal thresholdBD = BigDecimal.valueOf(scoreDeltaThreshold);
        List<ScheduleDiffSummary.ReprioritizationDiff> qualifyingCases = event.getReprioritizations().stream()
                .filter(diff -> diff.getScoreDelta() != null && diff.getScoreDelta().abs().compareTo(thresholdBD) >= 0)
                .toList();

        if (qualifyingCases.isEmpty()) {
            log.info("No cases exceeded the reprioritization threshold ({}) for run {}. No digest sent.",
                    scoreDeltaThreshold, event.getRunId());
            return;
        }

        // Collect registrar emails
        List<String> registrarEmails = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.REGISTRAR && u.getEmail() != null && !u.getEmail().isBlank())
                .map(u -> u.getEmail().trim())
                .distinct()
                .toList();

        if (registrarEmails.isEmpty()) {
            log.warn("No registrar users with valid email found to send nightly reprioritization digest.");
            registrarEmails = List.of("registrar@judicialflow.org");
        }

        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("Dear Registrar,\n\n");
        bodyBuilder.append(String.format("Nightly rescheduling run %s has completed. The following %d cases experienced a priority score shift of %s or more points:\n\n",
                event.getRunId(), qualifyingCases.size(), thresholdBD));

        for (var diff : qualifyingCases) {
            bodyBuilder.append(String.format(" • Case %s: Previous=%s, Current=%s (Delta=%s)\n   Details: %s\n\n",
                    diff.getCaseNumber(), diff.getPreviousScore(), diff.getCurrentScore(), diff.getScoreDelta(), diff.getDetails()));
        }

        bodyBuilder.append("Regards,\nJudicialFlow Automated Engine\n");

        String subject = "Nightly Reprioritization Digest: " + qualifyingCases.size() + " cases updated (Run " + event.getRunId() + ")";
        String body = bodyBuilder.toString();

        emailService.sendDigestEmailWithRetry(registrarEmails, subject, body);
    }
}
