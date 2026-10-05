-- V3__priority_scoring_phase3.sql
--
-- Phase 3: Priority / Aging Scoring Module
--
-- The original priority_scores table (V1) had a UNIQUE constraint implied by
-- the OneToOne JPA mapping (one row per case).  Phase 3 converts this to a
-- history table: each scoring run appends a new row, so we:
--   1. Drop the unique constraint on case_id if present.
--   2. Add the linked_case_bonus column for the fourth scoring factor.
--   3. Add a performance index on (case_id, computed_at DESC) for the
--      "latest score per case" query.
--   4. Add a performance index on total_score DESC for the top-N query.

-- 1. Remove any existing unique constraint on case_id so multiple score rows
--    are allowed per case (score history).
--    The constraint name from V1 DDL defaults to priority_scores_case_id_key.
--    Use IF EXISTS so this is idempotent if the constraint doesn't exist.
ALTER TABLE priority_scores
    DROP CONSTRAINT IF EXISTS priority_scores_case_id_key;

-- 2. Add the linked_case_bonus factor column (defaults to 0 for existing rows).
ALTER TABLE priority_scores
    ADD COLUMN IF NOT EXISTS linked_case_bonus NUMERIC(10, 4) NOT NULL DEFAULT 0.0000;

-- 3. Index for "latest score per case" query pattern.
CREATE INDEX IF NOT EXISTS idx_priority_scores_case_computed
    ON priority_scores (case_id, computed_at DESC);

-- 4. Index for top-N ranking query pattern.
CREATE INDEX IF NOT EXISTS idx_priority_scores_total_score
    ON priority_scores (total_score DESC);
