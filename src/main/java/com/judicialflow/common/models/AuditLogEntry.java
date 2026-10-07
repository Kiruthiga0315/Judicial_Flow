package com.judicialflow.common.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log_entries")
@org.hibernate.annotations.Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Builder.Default
    @Column(name = "actor_role", nullable = false)
    private String actorRole = "SYSTEM";

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Builder.Default
    @Column(name = "timestamp", nullable = false)
    private java.time.Instant timestamp = java.time.Instant.now();

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @Column(columnDefinition = "TEXT")
    private String details;

    // --- Legacy field mappings maintained for backwards compatibility (hidden from API response) ---
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Builder.Default
    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime = LocalDateTime.now();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
