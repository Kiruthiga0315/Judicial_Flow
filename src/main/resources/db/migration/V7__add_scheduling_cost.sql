-- V7__add_scheduling_cost.sql

ALTER TABLE scheduling_runs ADD COLUMN seed BIGINT;
ALTER TABLE scheduling_runs ADD COLUMN total_weighted_soft_cost DECIMAL(12, 4);
ALTER TABLE scheduling_runs ADD COLUMN cost_breakdown TEXT;
