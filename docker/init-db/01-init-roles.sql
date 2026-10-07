-- Docker Compose PostgreSQL Database Initialization
-- Run as superuser (jfowner) on container first boot

-- Create application user role if it doesn't already exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'jfuser') THEN
        CREATE ROLE jfuser WITH LOGIN PASSWORD 'jfpass';
    END IF;
END
$$;

GRANT ALL PRIVILEGES ON DATABASE judicialflow TO jfuser;
