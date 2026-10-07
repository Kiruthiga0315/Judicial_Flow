-- Phase 9 Part B: Add training_sample_count to duration_estimates
ALTER TABLE duration_estimates
ADD COLUMN training_sample_count INTEGER;
