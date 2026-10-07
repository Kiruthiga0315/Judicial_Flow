package com.judicialflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("judicialflow_test")
                .withUsername("testowner")
                .withPassword("testownerpass");
        postgres.start();

        // Initialize app user role 'testuser' with testpass
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DO $$ BEGIN " +
                    "IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'testuser') THEN " +
                    "  CREATE ROLE testuser WITH LOGIN PASSWORD 'testpass'; " +
                    "END IF; " +
                    "GRANT ALL PRIVILEGES ON DATABASE judicialflow_test TO testuser; " +
                    "END $$;");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to initialize test roles", e);
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // App datasource connects as app user (testuser)
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "testuser");
        registry.add("spring.datasource.password", () -> "testpass");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Flyway connects as migration owner (testowner)
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");
    }

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    protected void clearAuditLogs() {
        // Test cleanup uses owner connection, adhering to role separation (app user cannot disable triggers)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE audit_log_entries DISABLE TRIGGER trg_prevent_audit_log_modification; " +
                    "DELETE FROM audit_log_entries; " +
                    "ALTER TABLE audit_log_entries ENABLE TRIGGER trg_prevent_audit_log_modification;");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to clear audit logs using owner connection", e);
        }
    }
}
