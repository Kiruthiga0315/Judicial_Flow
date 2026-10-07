-- V15__enforce_case_status_not_pending.sql

-- Ensure no case has the PENDING status and add a DB constraint
UPDATE cases SET current_status = 'FILED' WHERE current_status = 'PENDING';

ALTER TABLE cases ADD CONSTRAINT chk_case_status_not_pending CHECK (current_status != 'PENDING');
