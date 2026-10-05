-- V5__add_citations_disposal.sql

-- Add citations column for Phase 2
ALTER TABLE cases ADD COLUMN citations TEXT;
