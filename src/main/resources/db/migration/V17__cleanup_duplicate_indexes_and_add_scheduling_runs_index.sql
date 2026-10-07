-- Drop duplicate indexes on scheduling_proposals
-- Keeping idx_scheduling_proposals_run_id and idx_scheduling_proposals_case_id
DROP INDEX IF EXISTS idx_scheduling_proposals_run;
DROP INDEX IF EXISTS idx_scheduling_proposals_case;

-- Add index on scheduling_runs (status, triggered_at DESC)
CREATE INDEX IF NOT EXISTS idx_scheduling_runs_status_triggered_at ON scheduling_runs (status, triggered_at DESC);
