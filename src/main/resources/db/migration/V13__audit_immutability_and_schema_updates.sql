-- V13__audit_immutability_and_schema_updates.sql

-- 1. Align legacy case status PENDING to FILED
UPDATE cases SET current_status = 'FILED' WHERE current_status = 'PENDING';

-- 2. Add nullable judge_id on users table so JUDGE role users can be mapped to their judicial schedule
ALTER TABLE users ADD COLUMN IF NOT EXISTS judge_id UUID REFERENCES judges(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_users_judge ON users(judge_id);

-- 3. Database-level immutability guard: prevent UPDATE and DELETE on audit_log_entries
CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table audit_log_entries is immutable and append-only. UPDATE and DELETE are prohibited.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_audit_log_modification ON audit_log_entries;
CREATE TRIGGER trg_prevent_audit_log_modification
BEFORE UPDATE OR DELETE ON audit_log_entries
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();
