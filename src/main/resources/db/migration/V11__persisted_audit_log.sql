-- V11__persisted_audit_log.sql
-- Add explicit audit columns to audit_log_entries table

ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS actor_id VARCHAR(100);
ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS actor_username VARCHAR(100);
ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS actor_role VARCHAR(50) DEFAULT 'SYSTEM';
ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100);
ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS before_state JSONB;
ALTER TABLE audit_log_entries ADD COLUMN IF NOT EXISTS after_state JSONB;

-- Backfill existing rows where possible
UPDATE audit_log_entries
SET
    actor_username = COALESCE(actor_username, performed_by),
    entity_type = COALESCE(entity_type, entity_name),
    timestamp = COALESCE(timestamp, action_time),
    actor_role = COALESCE(actor_role, 'SYSTEM')
WHERE actor_username IS NULL OR entity_type IS NULL OR timestamp IS NULL OR actor_role IS NULL;

-- Make critical columns NOT NULL after backfill where applicable
UPDATE audit_log_entries SET actor_username = 'SYSTEM' WHERE actor_username IS NULL;
UPDATE audit_log_entries SET entity_type = 'UNKNOWN' WHERE entity_type IS NULL;
UPDATE audit_log_entries SET actor_role = 'SYSTEM' WHERE actor_role IS NULL;
UPDATE audit_log_entries SET timestamp = NOW() WHERE timestamp IS NULL;

-- Performance indexes for querying and filtering
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log_entries(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor_username ON audit_log_entries(actor_username);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log_entries(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log_entries(entity_type, entity_id);
