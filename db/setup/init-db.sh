#!/usr/bin/env bash
# init-db.sh — One-time database setup for the Cloud SQL PostgreSQL 17 instance.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated
#   - Cloud SQL instance already exists (e.g., unified-search-db)
#   - Your GCP project ID set as PROJECT_ID or pass via --project
#
# Usage:
#   DB_PASSWORD='YOUR_STRONG_DB_PASSWORD' \
#     ./db/setup/init-db.sh --project=my-project --instance=unified-search-db
#
# DB_PASSWORD becomes the application role's password. Store the same value in the DB_PASSWORD
# Secret Manager secret so Cloud Run can log in with it.
#
# This script:
#   1. Creates the 'unified_search' database if it does not exist
#   2. Creates required extensions (vector, pg_trgm, citext) — needs cloudsqlsuperuser
#   3. Creates the application database user, sets its password, and grants permissions
#
# After this runs, you can deploy the application and Flyway migrations will run
# automatically on first startup.

set -euo pipefail

PROJECT=""
INSTANCE=""
DATABASE="unified_search"

usage() {
  echo "Usage: $0 --project=PROJECT_ID --instance=INSTANCE_NAME"
  echo ""
  echo "Connects to the Cloud SQL instance as the postgres (cloudsqlsuperuser) user"
  echo "and sets up the database, extensions, and application user."
  exit 1
}

for arg in "$@"; do
  case "$arg" in
    --project=*) PROJECT="${arg#*=}" ;;
    --instance=*) INSTANCE="${arg#*=}" ;;
    --help) usage ;;
    *) echo "Unknown argument: $arg"; usage ;;
  esac
done

if [ -z "$PROJECT" ] || [ -z "$INSTANCE" ]; then
  usage
fi

if [ -z "${DB_PASSWORD:-}" ]; then
  echo "DB_PASSWORD is not set. It becomes the application role's password (see the header)." >&2
  exit 1
fi
export DB_PASSWORD

echo "=== Setting up database for Unified Search ==="
echo "Project:  $PROJECT"
echo "Instance: $INSTANCE"
echo "Database: $DATABASE"
echo ""

# Step 1: Create the database if it does not exist
echo ">>> Creating database '${DATABASE}' (if not exists)..."
gcloud sql databases create "${DATABASE}" \
  --instance="${INSTANCE}" \
  --project="${PROJECT}" \
  --quiet 2>/dev/null || echo "    Database '${DATABASE}' already exists, continuing."

# Step 2: Run the SQL setup script via the Cloud SQL proxy
echo ">>> Connecting to Cloud SQL and running init-db.sql..."
echo ""

gcloud sql connect "${INSTANCE}" \
  --user=postgres \
  --project="${PROJECT}" \
  --database=postgres \
  < "$(dirname "$0")/init-db.sql"

echo ""
echo "=== Database setup complete ==="
echo ""
echo "Next steps:"
echo "  1. Create secrets in Secret Manager:"
echo "     gcloud secrets create DB_USER     --replication-policy=automatic"
echo "     gcloud secrets create DB_PASSWORD --replication-policy=automatic"
echo "     gcloud secrets create API_KEY     --replication-policy=automatic"
echo ""
echo "  2. Add secret versions:"
echo "     echo -n 'unified_search' | gcloud secrets versions add DB_USER     --data-file=-"
echo "     echo -n \"\$DB_PASSWORD\" | gcloud secrets versions add DB_PASSWORD --data-file=-"
echo "     echo -n 'YOUR_STRONG_API_KEY' | gcloud secrets versions add API_KEY     --data-file=-"
echo ""
echo "  3. Deploy:"
echo "     gcloud builds submit --region=us-central1 --config=cloudbuild.yaml \\"
echo "       --substitutions=_DB_INSTANCE=${PROJECT}:REGION:${INSTANCE}"
echo ""
