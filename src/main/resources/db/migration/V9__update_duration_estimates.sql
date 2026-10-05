ALTER TABLE duration_estimates
ADD COLUMN test_mae DOUBLE PRECISION,
ADD COLUMN baseline_mae_days DOUBLE PRECISION,
ADD COLUMN beats_baseline BOOLEAN;
