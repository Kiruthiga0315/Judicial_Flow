-- V12__notification_and_litigant_email.sql

-- Add litigant contact email to cases table
ALTER TABLE cases ADD COLUMN IF NOT EXISTS litigant_contact_email VARCHAR(255);

-- Create notification_logs table to record all email attempts
CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    notification_type VARCHAR(50) NOT NULL, -- HEARING_SCHEDULED, HEARING_RESCHEDULED, REPRIORITIZATION_DIGEST
    recipient VARCHAR(255) NOT NULL,
    case_id UUID REFERENCES cases(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL, -- SENT, FAILED
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_logs_case ON notification_logs(case_id);
CREATE INDEX IF NOT EXISTS idx_notification_logs_status ON notification_logs(status);
CREATE INDEX IF NOT EXISTS idx_notification_logs_type ON notification_logs(notification_type);
CREATE INDEX IF NOT EXISTS idx_notification_logs_created ON notification_logs(created_at DESC);
