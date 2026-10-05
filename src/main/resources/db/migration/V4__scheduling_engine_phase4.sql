-- scheduling_runs: tracks each scheduling run
CREATE TABLE scheduling_runs (
    id UUID PRIMARY KEY,
    triggered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    status VARCHAR(50) NOT NULL, -- RUNNING, COMPLETED, FAILED
    total_cases_input INT NOT NULL DEFAULT 0,
    total_assigned INT NOT NULL DEFAULT 0,
    total_unschedulable INT NOT NULL DEFAULT 0,
    horizon_days INT NOT NULL DEFAULT 5,
    default_duration_minutes INT NOT NULL DEFAULT 60,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- scheduling_proposals: each proposed case→slot assignment within a run
CREATE TABLE scheduling_proposals (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES scheduling_runs(id) ON DELETE CASCADE,
    case_id UUID NOT NULL REFERENCES cases(id),
    judge_id UUID NOT NULL REFERENCES judges(id),
    courtroom_id UUID NOT NULL REFERENCES courtrooms(id),
    proposed_time TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    status VARCHAR(50) NOT NULL DEFAULT 'PROPOSED', -- PROPOSED, ACCEPTED, REJECTED, OVERRIDDEN
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- scheduling_decisions: explainability log for each proposal
CREATE TABLE scheduling_decisions (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES scheduling_proposals(id) ON DELETE CASCADE,
    case_number VARCHAR(100) NOT NULL,
    chosen_judge_name VARCHAR(255) NOT NULL,
    chosen_courtroom_name VARCHAR(255) NOT NULL,
    chosen_time TIMESTAMP NOT NULL,
    constraints_satisfied TEXT NOT NULL, -- JSON array of constraint names
    runner_up_judge_name VARCHAR(255),
    runner_up_courtroom_name VARCHAR(255),
    runner_up_time TIMESTAMP,
    runner_up_rejection_reason TEXT,
    soft_score_chosen NUMERIC(10,4),
    soft_score_runner_up NUMERIC(10,4),
    explanation TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- scheduling_overrides: manual override records
CREATE TABLE scheduling_overrides (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(id),
    judge_id UUID NOT NULL REFERENCES judges(id),
    courtroom_id UUID NOT NULL REFERENCES courtrooms(id),
    scheduled_time TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    reason TEXT NOT NULL,
    overridden_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Performance indexes for conflict detection
CREATE INDEX idx_hearings_judge_time ON hearings(judge_id, scheduled_time);
CREATE INDEX idx_hearings_courtroom_time ON hearings(courtroom_id, scheduled_time);
CREATE INDEX idx_scheduling_proposals_run ON scheduling_proposals(run_id);
CREATE INDEX idx_scheduling_proposals_case ON scheduling_proposals(case_id);
CREATE INDEX idx_scheduling_decisions_proposal ON scheduling_decisions(proposal_id);
CREATE INDEX idx_scheduling_overrides_case ON scheduling_overrides(case_id);
