#!/usr/bin/env bash
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"
NODE22="${NODE22:-/Users/chenjiarun/.local/node-v22/current/bin}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"
IMAGE_TAR="${IMAGE_TAR:-/private/tmp/blade-prod-images-amd64.tar}"

if [ "${FIRST_DEPLOY_CONFIRM:-}" != "YES" ]; then
  cat <<'EOF'
Refusing to run deploy_from_local.sh.

This script is for first deployment or infrastructure rebuild only. It packages
MySQL/Redis base images and may start all containers. For daily production
release, use:

  deploy/nas/deploy_app_from_local.sh --execute

If this is truly a first deployment or infrastructure rebuild, rerun with:

  FIRST_DEPLOY_CONFIRM=YES deploy/nas/deploy_from_local.sh
EOF
  exit 1
fi

cd "$(dirname "$0")/../.."

echo "Build backend..."
(cd blade-backend && mvn clean package -DskipTests)

echo "Build admin..."
(cd blade-admin && PATH="$NODE22:$PATH" npm run build)

echo "Build Docker images for $IMAGE_PLATFORM..."
docker pull --platform "$IMAGE_PLATFORM" mysql:8.0
docker pull --platform "$IMAGE_PLATFORM" redis:7-alpine
docker pull --platform "$IMAGE_PLATFORM" nginx:alpine
docker pull --platform "$IMAGE_PLATFORM" maven:3.9.9-eclipse-temurin-17
docker build --platform "$IMAGE_PLATFORM" -t blade-backend:prod blade-backend
docker build --platform "$IMAGE_PLATFORM" -t blade-web:prod -f blade-admin/Dockerfile .

echo "Export Docker images..."
docker save mysql:8.0 redis:7-alpine blade-backend:prod blade-web:prod -o "$IMAGE_TAR"

echo "Prepare remote directories..."
ssh "$NAS_USER@$NAS_HOST" "set -e; mkdir -p \
  '$NAS_DIR/app/blade-backend/target' \
  '$NAS_DIR/app/blade-admin' \
  '$NAS_DIR/app/deploy/nas/nginx' \
  '$NAS_DIR/mysql' '$NAS_DIR/redis' '$NAS_DIR/uploads' '$NAS_DIR/logs'"

echo "Upload release files..."
scp -O blade-backend/Dockerfile "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-backend/Dockerfile"
scp -O blade-backend/target/blade-backend-1.0.0.jar "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-backend/target/"
scp -O blade-admin/Dockerfile "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-admin/Dockerfile"
scp -O -r blade-admin/dist "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-admin/"
scp -O deploy/nas/nginx/default.conf "$NAS_USER@$NAS_HOST:$NAS_DIR/app/deploy/nas/nginx/default.conf"
scp -O deploy/nas/docker-compose.prod.yml "$NAS_USER@$NAS_HOST:$NAS_DIR/docker-compose.prod.yml"
scp -O deploy/nas/README.md "$NAS_USER@$NAS_HOST:$NAS_DIR/README.md"
scp -O "$IMAGE_TAR" "$NAS_USER@$NAS_HOST:$NAS_DIR/blade-prod-images.tar"

echo "Create remote .env.prod if missing..."
ssh "$NAS_USER@$NAS_HOST" "set -e; cd '$NAS_DIR'; if [ ! -f .env.prod ]; then umask 077; MYSQL_ROOT_PASSWORD=\$(openssl rand -hex 24); JWT_SECRET=\$(openssl rand -hex 32); printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=blade_project_prod\nJWT_SECRET=%s\nWEB_PORT=8899\n' \"\$MYSQL_ROOT_PASSWORD\" \"\$JWT_SECRET\" > .env.prod; fi"

echo "Load Docker images on NAS..."
ssh "$NAS_USER@$NAS_HOST" "cd '$NAS_DIR' && /usr/local/bin/docker load -i blade-prod-images.tar"

echo "Start containers..."
ssh "$NAS_USER@$NAS_HOST" "cd '$NAS_DIR' && /usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-build"

echo "Done. Open: http://$NAS_HOST:8899/catalog"
