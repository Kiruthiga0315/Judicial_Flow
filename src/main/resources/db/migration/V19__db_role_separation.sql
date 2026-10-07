-- Phase 9 Part A: Database Role Separation
-- Flyway runs as the migration owner role (jfowner / testowner).
-- The application connects as the app user role (jfuser / testuser).

DO $$
BEGIN
    -- Ensure app roles exist if not created by database init script
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'jfuser') THEN
        CREATE ROLE jfuser WITH LOGIN PASSWORD 'jfpass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'testuser') THEN
        CREATE ROLE testuser WITH LOGIN PASSWORD 'testpass';
    END IF;
END
$$;

-- Grant usage and create on public schema
GRANT USAGE, CREATE ON SCHEMA public TO jfuser, testuser;

-- Grant basic DML privileges on all existing tables
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jfuser, testuser;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jfuser, testuser;

-- Strict Immutability for audit_log_entries:
-- App users (jfuser, testuser) have only SELECT and INSERT on audit_log_entries.
-- App users are explicitly REVOKED of UPDATE, DELETE, TRUNCATE, and TRIGGER privileges.
-- Furthermore, table ownership remains with the migration owner, preventing ALTER TABLE ... DISABLE TRIGGER.
REVOKE UPDATE, DELETE, TRUNCATE, TRIGGER ON audit_log_entries FROM jfuser, testuser;

-- Ensure future tables created by the migration owner default to granting DML to app users
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jfuser, testuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO jfuser, testuser;
