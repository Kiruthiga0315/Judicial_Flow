-- V14__audit_trigger_test_session_override.sql
-- Allow test sessions to bypass immutability trigger for cleanup if session flag is set.

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF current_setting('judicialflow.allow_audit_cleanup', true) = 'true' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Table audit_log_entries is immutable and append-only. UPDATE and DELETE are prohibited.';
END;
$$ LANGUAGE plpgsql;
