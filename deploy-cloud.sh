#!/bin/bash
# ==============================================================================
#  OCVAP Backend – Separates Cloud Run Deployment
#  Baut und deployed 5 Services einzeln (kein Eureka in Cloud Run)
#
#  Aufruf:  bash deploy-cloud.sh
# ==============================================================================

set -e

PROJECT="ovacp-db"
REGION="europe-west3"
REGISTRY="europe-west3-docker.pkg.dev/${PROJECT}/ovacp-repo"
FRONTEND_URL="https://ovacp-frontend-765786852340.europe-west3.run.app"

# Gemeinsame Env-Vars fuer ALLE Services (Eureka komplett deaktivieren)
COMMON_ENV="EUREKA_CLIENT_ENABLED=false,SERVER_PORT=8080,SPRING_CLOUD_DISCOVERY_ENABLED=false,SPRING_CLOUD_SERVICE_REGISTRY_AUTO_REGISTRATION_ENABLED=false"

echo "============================================="
echo "  OCVAP Backend – Cloud Run Deployment"
echo "============================================="

# ── 1) Alle Images bauen ─────────────────────────────────────────────────────
echo ""
echo "=== [1/6] Baue alle Docker-Images ==="
gcloud builds submit \
  --config=cloudbuild.yaml \
  --region="${REGION}" \
  --substitutions="_REGISTRY=${REGISTRY}" \
  --timeout=1800
echo "=== Alle Images gebaut! ==="

# ── 2) File Management ──────────────────────────────────────────────────────
echo ""
echo "=== [2/6] Deploy: file-management ==="
gcloud run deploy file-management \
  --image="${REGISTRY}/file-management:latest" \
  --region="${REGION}" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=512Mi --cpu=1 \
  --timeout=300 \
  --set-env-vars="${COMMON_ENV}"

FILE_MGMT_URL=$(gcloud run services describe file-management --region="${REGION}" --format='value(status.url)')
echo "  -> ${FILE_MGMT_URL}"

# ── 3) Usermanagement ───────────────────────────────────────────────────────
echo ""
echo "=== [3/6] Deploy: usermanagement ==="
gcloud run deploy usermanagement \
  --image="${REGISTRY}/usermanagement:latest" \
  --region="${REGION}" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=512Mi --cpu=1 \
  --timeout=300 \
  --add-cloudsql-instances="${PROJECT}:${REGION}:ovacp" \
  --set-env-vars="${COMMON_ENV}"

USERMGMT_URL=$(gcloud run services describe usermanagement --region="${REGION}" --format='value(status.url)')
echo "  -> ${USERMGMT_URL}"

# ── 4) AI Analysis ──────────────────────────────────────────────────────────
echo ""
echo "=== [4/6] Deploy: ai-analysis ==="
gcloud run deploy ai-analysis \
  --image="${REGISTRY}/ai-analysis:latest" \
  --region="${REGION}" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=1Gi --cpu=1 \
  --timeout=600 \
  --set-env-vars="${COMMON_ENV},FILE_MANAGEMENT_URL=${FILE_MGMT_URL}"

AI_URL=$(gcloud run services describe ai-analysis --region="${REGION}" --format='value(status.url)')
echo "  -> ${AI_URL}"

# ── 5) Chart Data ───────────────────────────────────────────────────────────
echo ""
echo "=== [5/6] Deploy: chart-data ==="
gcloud run deploy chart-data \
  --image="${REGISTRY}/chart-data:latest" \
  --region="${REGION}" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=1Gi --cpu=1 \
  --timeout=300 \
  --set-env-vars="${COMMON_ENV},FILE_MANAGEMENT_URL=${FILE_MGMT_URL}"

CHART_URL=$(gcloud run services describe chart-data --region="${REGION}" --format='value(status.url)')
echo "  -> ${CHART_URL}"

# ── 6) Gateway ──────────────────────────────────────────────────────────────
echo ""
echo "=== [6/6] Deploy: gateway ==="

cat > /tmp/gateway-env.yaml <<EOF
EUREKA_CLIENT_ENABLED: "false"
SERVER_PORT: "8080"
SPRING_CLOUD_DISCOVERY_ENABLED: "false"
SPRING_CLOUD_SERVICE_REGISTRY_AUTO_REGISTRATION_ENABLED: "false"
CORS_ALLOWED_ORIGINS: "${FRONTEND_URL},http://localhost:3000"
ROUTE_USERMANAGEMENT: "${USERMGMT_URL}"
ROUTE_FILE_MANAGEMENT: "${FILE_MGMT_URL}"
ROUTE_AI_ANALYSIS: "${AI_URL}"
ROUTE_CHART_DATA: "${CHART_URL}"
EOF

gcloud run deploy gateway \
  --image="${REGISTRY}/gateway:latest" \
  --region="${REGION}" \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --memory=512Mi --cpu=1 \
  --timeout=600 \
  --env-vars-file=/tmp/gateway-env.yaml

GATEWAY_URL=$(gcloud run services describe gateway --region="${REGION}" --format='value(status.url)')

echo ""
echo "============================================="
echo "  Deployment abgeschlossen!"
echo "============================================="
echo "  Gateway:         ${GATEWAY_URL}"
echo "  File Management: ${FILE_MGMT_URL}"
echo "  Usermanagement:  ${USERMGMT_URL}"
echo "  AI Analysis:     ${AI_URL}"
echo "  Chart Data:      ${CHART_URL}"
echo ""
echo "  Frontend API_BASE_URL = ${GATEWAY_URL}"
echo "============================================="
