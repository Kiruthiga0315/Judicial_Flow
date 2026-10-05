-- V6__add_priority_trigger.sql

-- Add triggered_by column for priority scores
ALTER TABLE priority_scores ADD COLUMN triggered_by VARCHAR(50) DEFAULT 'BATCH';
