package com.judicialflow.notification.repository;

import com.judicialflow.notification.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByRecipient(String recipient);

    List<NotificationLog> findByCaseId(UUID caseId);

    List<NotificationLog> findByNotificationType(String notificationType);

    List<NotificationLog> findByStatus(String status);
}
