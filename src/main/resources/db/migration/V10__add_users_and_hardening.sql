-- V10__add_users_and_hardening.sql
-- Fix 1: users table for real authentication and RBAC
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL, -- ADMIN, REGISTRAR, JUDGE
    email VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_username ON users(username);

-- Fix 2 & 3: Additional fields on cases for statutory deadline, assigned courtroom, and next hearing date
ALTER TABLE cases ADD COLUMN IF NOT EXISTS statutory_deadline DATE;
ALTER TABLE cases ADD COLUMN IF NOT EXISTS assigned_courtroom_id UUID REFERENCES courtrooms(id);
ALTER TABLE cases ADD COLUMN IF NOT EXISTS next_hearing_date TIMESTAMP;

-- Fix 2: Hearing engine flag
ALTER TABLE hearings ADD COLUMN IF NOT EXISTS created_by_engine BOOLEAN NOT NULL DEFAULT false;

-- Fix 2: Scheduling proposals rejection reason & status support
ALTER TABLE scheduling_proposals ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- Fix 3: Statutory deadline bonus in priority scores
ALTER TABLE priority_scores ADD COLUMN IF NOT EXISTS statutory_deadline_bonus NUMERIC(10, 4) NOT NULL DEFAULT 0.0000;

-- Fix 5: Indexes for latest-score, retention pruning, and per-run proposal lookups
CREATE INDEX IF NOT EXISTS idx_priority_scores_computed_at ON priority_scores (computed_at);
CREATE INDEX IF NOT EXISTS idx_scheduling_proposals_run_id ON scheduling_proposals (run_id);
CREATE INDEX IF NOT EXISTS idx_scheduling_proposals_case_id ON scheduling_proposals (case_id);
CREATE INDEX IF NOT EXISTS idx_scheduling_proposals_run_case ON scheduling_proposals (run_id, case_id);
CREATE INDEX IF NOT EXISTS idx_scheduling_proposals_case_status ON scheduling_proposals (case_id, status);
CREATE INDEX IF NOT EXISTS idx_hearings_judge_time ON hearings (judge_id, scheduled_time);
CREATE INDEX IF NOT EXISTS idx_hearings_courtroom_time ON hearings (courtroom_id, scheduled_time);
