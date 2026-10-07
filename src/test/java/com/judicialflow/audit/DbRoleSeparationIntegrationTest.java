package com.judicialflow.audit;

import com.judicialflow.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Phase 9 Part A: Database Role Separation & Immutability Enforcement Tests")
class DbRoleSeparationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Role Separation: App user (testuser) can SELECT from audit_log_entries")
    void testAppUserCanSelectFromAuditLogEntries() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log_entries", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    @DisplayName("Role Separation: App user (testuser) can INSERT into audit_log_entries")
    void testAppUserCanInsertIntoAuditLogEntries() {
        UUID id = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO audit_log_entries (id, timestamp, actor_username, actor_role, action, entity_type, entity_id, reason_code, entity_name, performed_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, LocalDateTime.now(), "system", "SYSTEM", "TEST_INSERT", "Case", id.toString(), "TEST", "Test Case", "system"
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("Role Separation: App user (testuser) cannot UPDATE audit_log_entries (permission denied)")
    void testAppUserCannotUpdateAuditLogEntries() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO audit_log_entries (id, timestamp, actor_username, actor_role, action, entity_type, entity_id, reason_code, entity_name, performed_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, LocalDateTime.now(), "system", "SYSTEM", "TEST_UPDATE", "Case", id.toString(), "TEST", "Test Case", "system"
        );

        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE audit_log_entries SET actor_username = 'hacker' WHERE id = ?", id)
        );
        String cause = ex.getMostSpecificCause().getMessage();
        assertThat(cause).containsIgnoringCase("permission denied");
    }

    @Test
    @DisplayName("Role Separation: App user (testuser) cannot DELETE from audit_log_entries (permission denied)")
    void testAppUserCannotDeleteFromAuditLogEntries() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO audit_log_entries (id, timestamp, actor_username, actor_role, action, entity_type, entity_id, reason_code, entity_name, performed_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, LocalDateTime.now(), "system", "SYSTEM", "TEST_DELETE", "Case", id.toString(), "TEST", "Test Case", "system"
        );

        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM audit_log_entries WHERE id = ?", id)
        );
        String cause = ex.getMostSpecificCause().getMessage();
        assertThat(cause).containsIgnoringCase("permission denied");
    }

    @Test
    @DisplayName("Role Separation: App user (testuser) cannot ALTER TABLE audit_log_entries DISABLE TRIGGER (not table owner)")
    void testAppUserCannotDisableTriggerOnAuditLogEntries() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.execute("ALTER TABLE audit_log_entries DISABLE TRIGGER trg_prevent_audit_log_modification")
        );
        String cause = ex.getMostSpecificCause().getMessage();
        assertThat(cause).containsIgnoringCase("must be owner");
    }
}
