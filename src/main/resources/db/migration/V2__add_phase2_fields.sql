-- V2__add_phase2_fields.sql

ALTER TABLE cases ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cases ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE cases ADD COLUMN assigned_judge_id UUID REFERENCES judges(id);

ALTER TABLE judges ADD COLUMN availability_windows JSONB DEFAULT '[]'::jsonb;
ALTER TABLE judges ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE courtrooms ADD COLUMN availability JSONB DEFAULT '[]'::jsonb;
ALTER TABLE courtrooms ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

CREATE INDEX idx_cases_deleted ON cases(deleted);
CREATE INDEX idx_cases_assigned_judge ON cases(assigned_judge_id);
CREATE INDEX idx_cases_filing_date ON cases(filing_date);
