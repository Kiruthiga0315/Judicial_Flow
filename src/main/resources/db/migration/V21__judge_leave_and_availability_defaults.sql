-- V21__judge_leave_and_availability_defaults.sql
-- 1. Create judge_leaves table for date-specific absence / leave tracking
CREATE TABLE IF NOT EXISTS judge_leaves (
    id UUID PRIMARY KEY,
    judge_id UUID NOT NULL REFERENCES judges(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_judge_leaves_dates ON judge_leaves(judge_id, start_date, end_date);

-- 2. Populate default working availability (Monday-Friday 09:00 - 17:00)
-- for any existing judges that have empty or null availability
UPDATE judges
SET availability_windows = '[
  {"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "17:00"}
]'::jsonb
WHERE availability_windows IS NULL OR availability_windows = '[]'::jsonb;

-- 3. Populate default working availability (Monday-Friday 09:00 - 17:00)
-- for any existing courtrooms that have empty or null availability
UPDATE courtrooms
SET availability = '[
  {"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "17:00"},
  {"dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "17:00"}
]'::jsonb
WHERE availability IS NULL OR availability = '[]'::jsonb;
