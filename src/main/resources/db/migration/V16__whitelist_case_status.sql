-- V16__whitelist_case_status.sql
-- Drop the negative check and replace with an explicit whitelist check

ALTER TABLE cases DROP CONSTRAINT IF EXISTS chk_case_status_not_pending;

ALTER TABLE cases ADD CONSTRAINT chk_case_status_whitelist 
    CHECK (current_status IN ('FILED', 'SCHEDULED', 'HEARD', 'DISPOSED', 'ADJOURNED'));
