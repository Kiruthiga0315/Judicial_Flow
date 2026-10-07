package com.judicialflow.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.judicialflow.common.AuditLogRepository;
import com.judicialflow.common.models.AuditLogEntry;
import com.judicialflow.security.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Single write path for audit log entries.
     * Extracts actor information from SecurityContextHolder.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLogEntry log(
            String entityType,
            String entityId,
            String action,
            String reasonCode,
            Object beforeState,
            Object afterState,
            String details) {

        String actorUsername = "SYSTEM";
        String actorRole = "SYSTEM";
        String actorId = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            actorUsername = auth.getName();
            if (auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                String authStr = auth.getAuthorities().iterator().next().getAuthority();
                actorRole = authStr.startsWith("ROLE_") ? authStr.substring(5) : authStr;
            }
            try {
                var userOpt = userRepository.findByUsername(actorUsername);
                if (userOpt.isPresent()) {
                    actorId = userOpt.get().getId().toString();
                }
            } catch (Exception e) {
                log.debug("Could not resolve user ID for actor: {}", actorUsername);
            }
        }

        return logExplicit(actorId, actorUsername, actorRole, entityType, entityId, action, reasonCode, beforeState, afterState, details);
    }

    /**
     * Single write path for audit log entries with explicit actor details.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLogEntry logExplicit(
            String actorId,
            String actorUsername,
            String actorRole,
            String entityType,
            String entityId,
            String action,
            String reasonCode,
            Object beforeState,
            Object afterState,
            String details) {

        Map<String, Object> beforeMap = toMap(beforeState);
        Map<String, Object> afterMap = toMap(afterState);

        java.time.Instant nowInstant = java.time.Instant.now();
        LocalDateTime nowLdt = LocalDateTime.ofInstant(nowInstant, java.time.ZoneOffset.UTC);
        AuditLogEntry entry = AuditLogEntry.builder()
                .actorId(actorId)
                .actorUsername(actorUsername != null ? actorUsername : "SYSTEM")
                .actorRole(actorRole != null ? actorRole : "SYSTEM")
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .reasonCode(reasonCode)
                .beforeState(beforeMap)
                .afterState(afterMap)
                .details(details)
                .timestamp(nowInstant)
                // Legacy synchronization
                .entityName(entityType)
                .performedBy(actorUsername != null ? actorUsername : "SYSTEM")
                .actionTime(nowLdt)
                .build();

        AuditLogEntry saved = auditLogRepository.save(entry);
        log.info("AUDIT [{}] {} on {} id={} by actor={}({}) - reason={}",
                saved.getAction(), saved.getEntityType(), saved.getEntityId(), saved.getActorUsername(), saved.getActorRole(), saved.getReasonCode(), saved.getDetails());
        return saved;
    }

    /**
     * Logs an entry performed by SYSTEM.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLogEntry logSystem(
            String entityType,
            String entityId,
            String action,
            String reasonCode,
            Object beforeState,
            Object afterState,
            String details) {
        return logExplicit(null, "SYSTEM", "SYSTEM", entityType, entityId, action, reasonCode, beforeState, afterState, details);
    }

    /**
     * Queries audit logs with filtering and pagination.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogEntry> findAuditLogs(AuditFilterCriteria criteria, Pageable pageable) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getActor() != null && !criteria.getActor().isBlank()) {
                String actorSearch = criteria.getActor().trim();
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("actorUsername")), actorSearch.toLowerCase()),
                        cb.equal(root.get("actorId"), actorSearch)
                ));
            }

            if (criteria.getAction() != null && !criteria.getAction().isBlank()) {
                predicates.add(cb.equal(root.get("action"), criteria.getAction().trim()));
            }

            if (criteria.getEntityType() != null && !criteria.getEntityType().isBlank()) {
                String entityType = criteria.getEntityType().trim();
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("entityType")), entityType.toLowerCase()),
                        cb.equal(cb.lower(root.get("entityName")), entityType.toLowerCase())
                ));
            }

            if (criteria.getEntityId() != null && !criteria.getEntityId().isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), criteria.getEntityId().trim()));
            }

            if (criteria.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), criteria.getFrom()));
            }

            if (criteria.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), criteria.getTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not serialize object of type {} to Map", obj.getClass().getName(), e);
            return Map.of("value", obj.toString());
        }
    }
}
