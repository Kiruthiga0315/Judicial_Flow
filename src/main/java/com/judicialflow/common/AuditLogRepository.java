package com.judicialflow.common;

import com.judicialflow.common.models.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findByEntityNameAndEntityId(String entityName, String entityId);

    List<AuditLogEntry> findByAction(String action);
}
