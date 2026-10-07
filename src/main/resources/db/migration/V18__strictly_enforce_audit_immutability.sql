-- V18__strictly_enforce_audit_immutability.sql
-- Strictly enforce table-level immutability on audit_log_entries without session-level bypasses.
-- UPDATE and DELETE operations are unconditionally prohibited.

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table audit_log_entries is immutable and append-only. UPDATE and DELETE are prohibited.';
END;
$$ LANGUAGE plpgsql;
