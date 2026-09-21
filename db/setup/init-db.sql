-- Database setup script for the Cloud SQL PostgreSQL 17 instance.
--
-- Run this ONCE, as the postgres (cloudsqlsuperuser) user. It is a privileged bootstrap and the
-- one deliberate exception to "Flyway only": Cloud SQL lets only a superuser run CREATE EXTENSION,
-- and the application role does not exist until this runs. Flyway's V1 declares the same
-- extensions with IF NOT EXISTS, so it stays a no-op here. Tables and indexes are Flyway's.
--
-- The application role's password is read from the DB_PASSWORD environment variable, so it never
-- lands in the repository. Store the same value in the DB_PASSWORD Secret Manager secret.
--
-- Usage (the database must already exist; init-db.sh creates it):
--   gcloud sql databases create unified_search --instance=YOUR_INSTANCE
--   DB_PASSWORD='YOUR_STRONG_DB_PASSWORD' \
--     gcloud sql connect YOUR_INSTANCE --user=postgres --database=postgres < db/setup/init-db.sql

\set app_password `printenv DB_PASSWORD`
SELECT :'app_password' = '' AS missing_password \gset
\if :missing_password
  \echo 'DB_PASSWORD is not set: refusing to create the application role without a password.'
  \quit
\endif

\c unified_search

-- Required extensions for vector search, trigram matching, and case-insensitive text.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;

-- The application connects as this role. Its password is set (or rotated) on every run.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'unified_search') THEN
    CREATE ROLE unified_search WITH LOGIN INHERIT;
  END IF;
END
$$;
ALTER ROLE unified_search WITH PASSWORD :'app_password';

-- Grant schema permissions. Flyway will create and own the schema.
GRANT CONNECT ON DATABASE unified_search TO unified_search;
GRANT CREATE ON DATABASE unified_search TO unified_search;
GRANT CREATE ON SCHEMA public TO unified_search;
GRANT USAGE ON SCHEMA public TO unified_search;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO unified_search;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO unified_search;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO unified_search;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO unified_search;

-- Verify extensions
SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name IN ('vector', 'pg_trgm', 'citext')
  AND installed_version IS NOT NULL;
