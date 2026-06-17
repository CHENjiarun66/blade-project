#!/usr/bin/env bash
set -euo pipefail

EXECUTE=0
if [ "${1:-}" = "--execute" ]; then
  EXECUTE=1
fi

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_HOST_WG="${NAS_HOST_WG:-10.13.13.1}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"
NODE22="${NODE22:-/Users/chenjiarun/.local/node-v22/current/bin}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"
IMAGE_TAR="${IMAGE_TAR:-/private/tmp/blade-app-images-amd64.tar}"
RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d_%H%M%S)}"

cd "$(dirname "$0")/../.."

resolve_nas_host() {
  if [ -n "${NAS_HOST_FIXED:-}" ]; then
    return
  fi

  if ssh -o BatchMode=yes -o ConnectTimeout=5 "$NAS_USER@$NAS_HOST" "true" >/dev/null 2>&1; then
    return
  fi

  echo "Primary NAS host $NAS_HOST is not reachable over SSH. Trying WireGuard host $NAS_HOST_WG..."
  if ssh -o BatchMode=yes -o ConnectTimeout=5 "$NAS_USER@$NAS_HOST_WG" "true" >/dev/null 2>&1; then
    NAS_HOST="$NAS_HOST_WG"
    echo "Using NAS host: $NAS_HOST"
    return
  fi

  echo "ERROR: NAS is unreachable over SSH via both primary host and WireGuard host."
  echo "Tried: $NAS_HOST and $NAS_HOST_WG"
  exit 1
}

if [ "$EXECUTE" -ne 1 ]; then
  cat <<EOF
Dry run only. No files will be uploaded and NAS will not be changed.

Daily production app release steps:
  1. Record git branch/commit/status.
  2. Build backend and frontend locally.
  3. Build only application images for ${IMAGE_PLATFORM}.
  4. Verify Docker image architecture is linux/amd64.
  5. Upload application artifacts and app images only.
  6. Create NAS database backup before restarting app containers.
  7. Restart only backend and web with --no-deps.
  8. Verify containers and /catalog.

Run for real:
  deploy/nas/deploy_app_from_local.sh --execute
EOF
  exit 0
fi

resolve_nas_host

echo "Release id: $RELEASE_ID"
echo "Git branch: $(git rev-parse --abbrev-ref HEAD)"
echo "Git commit: $(git rev-parse --short HEAD)"
if [ -n "$(git status --short)" ]; then
  echo "WARNING: working tree has uncommitted changes:"
  git status --short
fi

echo "Build backend..."
(cd blade-backend && mvn clean package -DskipTests)

echo "Build admin..."
(cd blade-admin && PATH="$NODE22:$PATH" npm run build)

echo "Build application Docker images for $IMAGE_PLATFORM..."
docker build --platform "$IMAGE_PLATFORM" -t blade-backend:prod blade-backend
docker build --platform "$IMAGE_PLATFORM" -t blade-web:prod -f blade-admin/Dockerfile .

echo "Verify Docker image architecture..."
backend_arch="$(docker image inspect blade-backend:prod --format '{{.Os}}/{{.Architecture}}')"
web_arch="$(docker image inspect blade-web:prod --format '{{.Os}}/{{.Architecture}}')"
echo "blade-backend:prod $backend_arch"
echo "blade-web:prod $web_arch"
if [ "$backend_arch" != "linux/amd64" ] || [ "$web_arch" != "linux/amd64" ]; then
  echo "ERROR: NAS requires linux/amd64 images. Refusing to deploy."
  exit 1
fi

echo "Export application Docker images..."
docker save blade-backend:prod blade-web:prod -o "$IMAGE_TAR"

echo "Prepare remote release directories..."
ssh "$NAS_USER@$NAS_HOST" "set -e; mkdir -p \
  '$NAS_DIR/app/blade-backend/target' \
  '$NAS_DIR/app/blade-admin' \
  '$NAS_DIR/app/deploy/nas/nginx' \
  '$NAS_DIR/releases/$RELEASE_ID' \
  '$NAS_DIR/db-backups' \
  '$NAS_DIR/logs'"

echo "Create NAS database backup before app restart..."
ssh "$NAS_USER@$NAS_HOST" "set -e; cd '$NAS_DIR'; \
  /usr/local/bin/docker exec blade-mysql sh -c 'mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --default-character-set=utf8mb4 --routines --triggers \"\$MYSQL_DATABASE\"' \
  > '$NAS_DIR/db-backups/pre_app_deploy_${RELEASE_ID}.sql'; \
  test -s '$NAS_DIR/db-backups/pre_app_deploy_${RELEASE_ID}.sql'"

echo "Upload application files..."
scp -O blade-backend/Dockerfile "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-backend/Dockerfile"
scp -O blade-backend/target/blade-backend-1.0.0.jar "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-backend/target/"
scp -O blade-admin/Dockerfile "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-admin/Dockerfile"
scp -O -r blade-admin/dist "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-admin/"
scp -O deploy/nas/nginx/default.conf "$NAS_USER@$NAS_HOST:$NAS_DIR/app/deploy/nas/nginx/default.conf"
scp -O deploy/nas/docker-compose.prod.yml "$NAS_USER@$NAS_HOST:$NAS_DIR/docker-compose.prod.yml"
scp -O "$IMAGE_TAR" "$NAS_USER@$NAS_HOST:$NAS_DIR/releases/$RELEASE_ID/blade-app-images.tar"

echo "Load application images on NAS..."
ssh "$NAS_USER@$NAS_HOST" "cd '$NAS_DIR' && /usr/local/bin/docker load -i '$NAS_DIR/releases/$RELEASE_ID/blade-app-images.tar'"

echo "Restart only backend and web..."
ssh "$NAS_USER@$NAS_HOST" "cd '$NAS_DIR' && /usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps backend web"

echo "Verify NAS containers and frontend..."
ssh "$NAS_USER@$NAS_HOST" "set -e; cd '$NAS_DIR'; \
  /usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml ps; \
  curl -fsSI http://127.0.0.1:8899/catalog >/dev/null"

echo "Done. Release: $RELEASE_ID"
echo "Open: http://$NAS_HOST:8899/catalog"
