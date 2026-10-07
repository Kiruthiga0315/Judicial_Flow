package com.judicialflow.notification.service;

import com.judicialflow.notification.model.NotificationLog;
import com.judicialflow.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepository;

    private static final int MAX_RETRIES = 3;

    /**
     * Sends an email with a bounded retry policy (max 3 attempts).
     * Records every send attempt (SENT or FAILED) in notification_logs.
     * Never loops forever and never throws unhandled exceptions that break the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendEmailWithRetry(
            String notificationType,
            String recipient,
            UUID caseId,
            String subject,
            String body) {

        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRIES) {
            attempts++;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("notifications@judicialflow.org");
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);

                mailSender.send(message);

                NotificationLog successLog = NotificationLog.builder()
                        .notificationType(notificationType)
                        .recipient(recipient)
                        .caseId(caseId)
                        .status("SENT")
                        .sentAt(LocalDateTime.now())
                        .retryCount(attempts - 1)
                        .build();
                notificationLogRepository.save(successLog);

                log.info("Successfully sent {} email to {} on attempt {}", notificationType, recipient, attempts);
                return true;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Attempt {}/{} failed sending {} email to {}: {}",
                        attempts, MAX_RETRIES, notificationType, recipient, ex.getMessage());
                if (attempts < MAX_RETRIES) {
                    try {
                        Thread.sleep(50); // Small backoff between retries
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Record failed attempt
        NotificationLog failureLog = NotificationLog.builder()
                .notificationType(notificationType)
                .recipient(recipient)
                .caseId(caseId)
                .status("FAILED")
                .errorMessage(lastException != null ? lastException.getMessage() : "Unknown error")
                .retryCount(attempts)
                .build();
        notificationLogRepository.save(failureLog);

        log.error("Failed to send {} email to {} after {} attempts: {}",
                notificationType, recipient, attempts, lastException != null ? lastException.getMessage() : "unknown");
        return false;
    }

    /**
     * Sends a digest email to multiple recipients in a single send invocation.
     * Records the send attempt in notification_logs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendDigestEmailWithRetry(
            java.util.List<String> recipients,
            String subject,
            String body) {

        int attempts = 0;
        Exception lastException = null;

        String recipientSummary = String.join(", ", recipients);
        if (recipientSummary.length() > 255) {
            recipientSummary = recipientSummary.substring(0, 252) + "...";
        }

        while (attempts < MAX_RETRIES) {
            attempts++;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("notifications@judicialflow.org");
                message.setTo(recipients.toArray(new String[0]));
                message.setSubject(subject);
                message.setText(body);

                mailSender.send(message);

                NotificationLog successLog = NotificationLog.builder()
                        .notificationType("REPRIORITIZATION_DIGEST")
                        .recipient(recipientSummary)
                        .caseId(null)
                        .status("SENT")
                        .sentAt(LocalDateTime.now())
                        .retryCount(attempts - 1)
                        .build();
                notificationLogRepository.save(successLog);

                log.info("Successfully sent REPRIORITIZATION_DIGEST email to {} on attempt {}", recipientSummary, attempts);
                return true;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Attempt {}/{} failed sending REPRIORITIZATION_DIGEST email to {}: {}",
                        attempts, MAX_RETRIES, recipientSummary, ex.getMessage());
                if (attempts < MAX_RETRIES) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Record failed attempt
        NotificationLog failureLog = NotificationLog.builder()
                .notificationType("REPRIORITIZATION_DIGEST")
                .recipient(recipientSummary)
                .caseId(null)
                .status("FAILED")
                .errorMessage(lastException != null ? lastException.getMessage() : "Unknown error")
                .retryCount(attempts)
                .build();
        notificationLogRepository.save(failureLog);

        log.error("Failed to send REPRIORITIZATION_DIGEST email to {} after {} attempts: {}",
                recipientSummary, attempts, lastException != null ? lastException.getMessage() : "unknown");
        return false;
    }
}
