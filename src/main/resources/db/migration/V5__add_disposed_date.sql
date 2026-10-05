-- V5__add_disposed_date.sql

-- Add disposed_date column for historical cases
ALTER TABLE cases ADD COLUMN disposed_date DATE;
