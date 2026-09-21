#!/usr/bin/env bash
# deploy.sh — Build and deploy Unified Search to Cloud Run.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated (gcloud auth login)
#   - Docker installed
#   - Cloud SQL PostgreSQL 17 instance running with extensions created
#     (run db/setup/init-db.sh first)
#   - Secrets created in Secret Manager (see db/setup/init-db.sh for instructions)
#   - Artifact Registry repository created:
#       gcloud artifacts repositories create unified-search \
#         --repository-format=docker \
#         --location=us-central1
#
# Usage:
#   ./deploy.sh --project=my-project --instance=my-instance --region=us-central1
#
# Optional:
#   --db-name=unified_search     Database name (default: unified_search)
#   --service=unified-search     Cloud Run service name (default: unified-search)
#   --repo=unified-search        Artifact Registry repo name (default: unified-search)
#   --gemini-secret=GEMINI_API_KEY
#                                Secret Manager secret holding the Gemini key; omit to leave
#                                summaries disabled (default: not mounted)

set -euo pipefail

PROJECT=""
INSTANCE=""
REGION="us-central1"
DB_NAME="unified_search"
SERVICE_NAME="unified-search"
REPO="unified-search"
GEMINI_SECRET=""

usage() {
  echo "Usage: $0 --project=PROJECT_ID --instance=INSTANCE_NAME [--region=REGION]"
  echo ""
  echo "Required:"
  echo "  --project=PROJECT_ID    GCP project ID"
  echo "  --instance=INSTANCE     Cloud SQL instance connection name (project:region:instance)"
  echo "                          or just the instance name if --project is set"
  echo ""
  echo "Optional:"
  echo "  --region=REGION         GCP region (default: us-central1)"
  echo "  --db-name=NAME          Database name (default: unified_search)"
  echo "  --service=NAME          Cloud Run service name (default: unified-search)"
  echo "  --repo=NAME             Artifact Registry repo (default: unified-search)"
  echo "  --gemini-secret=NAME    Secret holding the Gemini key; omit to keep summaries disabled"
  exit 1
}

for arg in "$@"; do
  case "$arg" in
    --project=*) PROJECT="${arg#*=}" ;;
    --instance=*) INSTANCE="${arg#*=}" ;;
    --region=*) REGION="${arg#*=}" ;;
    --db-name=*) DB_NAME="${arg#*=}" ;;
    --service=*) SERVICE_NAME="${arg#*=}" ;;
    --repo=*) REPO="${arg#*=}" ;;
    --gemini-secret=*) GEMINI_SECRET="${arg#*=}" ;;
    --help) usage ;;
    *) echo "Unknown argument: $arg"; usage ;;
  esac
done

if [ -z "$PROJECT" ] || [ -z "$INSTANCE" ]; then
  usage
fi

# If the instance is just a name (no colons), prepend the project and region
if [[ "$INSTANCE" != *:* ]]; then
  INSTANCE="${PROJECT}:${REGION}:${INSTANCE}"
fi

echo "=== Deploying Unified Search to Cloud Run ==="
echo "Project:  $PROJECT"
echo "Instance: $INSTANCE"
echo "Region:   $REGION"
echo "Service:  $SERVICE_NAME"
echo ""

# Step 1: Submit Cloud Build
echo ">>> Submitting Cloud Build..."
gcloud builds submit \
  --project="${PROJECT}" \
  --region="${REGION}" \
  --config=cloudbuild.yaml \
  --substitutions="_DB_INSTANCE=${INSTANCE},_DB_NAME=${DB_NAME},_REGION=${REGION},_SERVICE_NAME=${SERVICE_NAME},_REPO=${REPO},_GEMINI_API_KEY_SECRET=${GEMINI_SECRET}"

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Your service will be available at:"
echo "  https://${SERVICE_NAME}-xxxxxxxxxx-${REGION}.a.run.app"
echo ""
echo "To find the exact URL:"
echo "  gcloud run services describe ${SERVICE_NAME} --region=${REGION} --project=${PROJECT} --format='value(status.url)'"
echo ""
echo "Set the API key in your requests:"
echo "  curl -H \"X-API-Key: YOUR_API_KEY\" https://.../health"
echo ""
