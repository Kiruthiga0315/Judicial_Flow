-- V1__init_schema.sql

CREATE TABLE judges (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    specialization VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE courtrooms (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    capacity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE cases (
    id UUID PRIMARY KEY,
    case_number VARCHAR(100) UNIQUE NOT NULL,
    case_type VARCHAR(50) NOT NULL, -- BAIL, POCSO, MATRIMONIAL, CIVIL, CRIMINAL_OTHER
    filing_date DATE NOT NULL,
    current_status VARCHAR(50) NOT NULL, -- e.g., PENDING, SCHEDULED, DISPOSED
    prior_adjournments INT NOT NULL DEFAULT 0,
    linked_case_id UUID REFERENCES cases(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE priority_scores (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    total_score NUMERIC(10, 4) NOT NULL,
    base_weight NUMERIC(10, 4) NOT NULL,
    age_multiplier NUMERIC(10, 4) NOT NULL,
    adjournment_boost NUMERIC(10, 4) NOT NULL,
    explanation TEXT NOT NULL,
    computed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE hearings (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(id),
    judge_id UUID REFERENCES judges(id),
    courtroom_id UUID REFERENCES courtrooms(id),
    scheduled_time TIMESTAMP NOT NULL,
    estimated_duration_minutes INT NOT NULL,
    status VARCHAR(50) NOT NULL, -- SCHEDULED, COMPLETED, ADJOURNED
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_log_entries (
    id UUID PRIMARY KEY,
    entity_name VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE, SCHEDULE
    performed_by VARCHAR(255) NOT NULL,
    action_time TIMESTAMP NOT NULL DEFAULT NOW(),
    reason_code VARCHAR(100) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cases_type ON cases(case_type);
CREATE INDEX idx_cases_status ON cases(current_status);
CREATE INDEX idx_hearings_scheduled_time ON hearings(scheduled_time);
CREATE INDEX idx_audit_log_time ON audit_log_entries(action_time);
