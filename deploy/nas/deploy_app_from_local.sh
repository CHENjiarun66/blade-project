#!/usr/bin/env bash
set -euo pipefail

EXECUTE=0
if [ "${1:-}" = "--execute" ]; then EXECUTE=1; fi

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_HOST_WG="${NAS_HOST_WG:-10.13.13.1}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"
NODE22="${NODE22:-/Users/chenjiarun/.local/node-v22/current/bin}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"
RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d_%H%M%S)}"
IMAGE_TAR="${IMAGE_TAR:-/private/tmp/blade-app-images-${RELEASE_ID}-amd64.tar}"
LOCAL_BACKUP_DIR="${LOCAL_BACKUP_DIR:-/private/tmp/blade-production-backups}"
AGENT_EXTERNAL_URL="${AGENT_EXTERNAL_URL:-https://www.chenjianas.asia:33294}"
EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION:-58}"
TENANT_ID="${TENANT_ID:-1}"
TLS_SECRET_DIR="${TLS_SECRET_DIR:-$NAS_DIR/secrets/tls}"

cd "$(dirname "$0")/../.."
git_commit="$(git rev-parse HEAD)"
backend_image="blade-backend:${RELEASE_ID}"
web_image="blade-web:${RELEASE_ID}"
maintenance_enabled=0

resolve_nas_host() {
  if [ -n "${NAS_HOST_FIXED:-}" ]; then return; fi
  if ssh -o BatchMode=yes -o ConnectTimeout=5 "$NAS_USER@$NAS_HOST" true >/dev/null 2>&1; then return; fi
  if ssh -o BatchMode=yes -o ConnectTimeout=5 "$NAS_USER@$NAS_HOST_WG" true >/dev/null 2>&1; then
    NAS_HOST="$NAS_HOST_WG"
    return
  fi
  echo "ERROR: NAS is unreachable over SSH."
  exit 1
}

require_release_evidence() {
  if [ -z "${REHEARSAL_REPORT:-}" ] || [ ! -f "$REHEARSAL_REPORT" ]; then
    echo "ERROR: REHEARSAL_REPORT must point to the production-copy V42→V${EXPECTED_FLYWAY_VERSION} rehearsal evidence."
    exit 1
  fi
  grep -Fqx "git_commit=$git_commit" "$REHEARSAL_REPORT" || {
    echo "ERROR: rehearsal commit does not match $git_commit"; exit 1;
  }
  grep -Fqx "result=PASS" "$REHEARSAL_REPORT" || {
    echo "ERROR: rehearsal did not pass"; exit 1;
  }
  grep -Fqx "manual_review=0" "$REHEARSAL_REPORT" || {
    echo "ERROR: rehearsal still contains manual-review orders"; exit 1;
  }
}

require_remote_tls_secret() {
  ssh "$NAS_USER@$NAS_HOST" "set -eu; \
    test -s '$TLS_SECRET_DIR/blade.crt'; \
    test -s '$TLS_SECRET_DIR/blade.key'; \
    /usr/bin/openssl x509 -in '$TLS_SECRET_DIR/blade.crt' -noout -checkend 86400; \
    cert_key=\$(/usr/bin/openssl x509 -in '$TLS_SECRET_DIR/blade.crt' -pubkey -noout | /usr/bin/openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | cut -d ' ' -f1); \
    private_key=\$(/usr/bin/openssl pkey -in '$TLS_SECRET_DIR/blade.key' -pubout -outform DER 2>/dev/null | sha256sum | cut -d ' ' -f1); \
    test -n \"\$cert_key\"; \
    test \"\$cert_key\" = \"\$private_key\"" || {
      echo "ERROR: NAS TLS secret is missing, expired, or its certificate/private key do not match."
      echo "Expected: $TLS_SECRET_DIR/blade.crt and blade.key"
      exit 1
    }
}

on_failure() {
  if [ "$maintenance_enabled" -eq 1 ]; then
    set +e
    ssh "$NAS_USER@$NAS_HOST" "mkdir -p '$NAS_DIR/maintenance'; touch '$NAS_DIR/maintenance/enabled'" >/dev/null 2>&1
    echo ""
    echo "RELEASE FAILED. Maintenance has been forced back on; do not reopen order writes."
    echo "Inspect: $NAS_DIR/releases/$RELEASE_ID"
    echo "After diagnosis, roll application images back with the previous-image values in release-manifest.txt."
  fi
}
trap on_failure ERR

if [ "$EXECUTE" -ne 1 ]; then
  cat <<EOF
Dry run only. Production is unchanged.

Order-refactor release gates:
  - clean Git commit and matching production-copy rehearsal evidence
  - full backend tests plus PC/mobile/types builds completed before this script
  - immutable linux/amd64 image tags ($backend_image, $web_image)
  - compressed DB backup + SHA-256 + verified NAS-external copy
  - maintenance mode before backend migration
  - NAS-mounted TLS certificate/private key secret (never baked into the image)
  - Flyway V$EXPECTED_FLYWAY_VERSION, legacy migrator execute/replay, and SQL invariants
  - trusted TLS check at $AGENT_EXTERNAL_URL before reopening traffic

Required execution form:
  ORDER_RELEASE_CONFIRM=YES REHEARSAL_REPORT=/absolute/path/rehearsal.env \\
    deploy/nas/deploy_app_from_local.sh --execute
EOF
  exit 0
fi

if [ "${ORDER_RELEASE_CONFIRM:-}" != "YES" ]; then
  echo "ERROR: set ORDER_RELEASE_CONFIRM=YES after approving the maintenance window."
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "ERROR: release worktree must be clean and committed."
  git status --short
  exit 1
fi
require_release_evidence
resolve_nas_host
require_remote_tls_secret

echo "Release $RELEASE_ID from $git_commit"
echo "Build and package application..."
(cd blade-backend && mvn clean package -DskipTests)
(cd blade-admin && PATH="$NODE22:$PATH" npm run build)
docker build --platform "$IMAGE_PLATFORM" -t "$backend_image" blade-backend
docker build --platform "$IMAGE_PLATFORM" -t "$web_image" -f blade-admin/Dockerfile .

for image in "$backend_image" "$web_image"; do
  architecture="$(docker image inspect "$image" --format '{{.Os}}/{{.Architecture}}')"
  if [ "$architecture" != "linux/amd64" ]; then
    echo "ERROR: $image is $architecture; NAS requires linux/amd64."
    exit 1
  fi
done
docker save "$backend_image" "$web_image" -o "$IMAGE_TAR"
image_tar_sha="$(shasum -a 256 "$IMAGE_TAR" | awk '{print $1}')"

remote_release="$NAS_DIR/releases/$RELEASE_ID"
ssh "$NAS_USER@$NAS_HOST" "set -eu; mkdir -p '$remote_release' '$NAS_DIR/maintenance' '$NAS_DIR/app/blade-backend/target' '$NAS_DIR/app/blade-admin' '$NAS_DIR/app/deploy/nas/nginx'"

BACKUP_ID="$RELEASE_ID" LOCAL_BACKUP_DIR="$LOCAL_BACKUP_DIR" NAS_HOST_FIXED=1 \
  NAS_HOST="$NAS_HOST" NAS_USER="$NAS_USER" NAS_DIR="$NAS_DIR" \
  deploy/nas/backup_db.sh --execute

previous_backend="$(ssh "$NAS_USER@$NAS_HOST" "/usr/local/bin/docker inspect blade-backend --format '{{.Config.Image}}' 2>/dev/null || true")"
previous_web="$(ssh "$NAS_USER@$NAS_HOST" "/usr/local/bin/docker inspect blade-web --format '{{.Config.Image}}' 2>/dev/null || true")"
rollback_backend="blade-backend:pre-${RELEASE_ID}"
rollback_web="blade-web:pre-${RELEASE_ID}"
ssh "$NAS_USER@$NAS_HOST" "set -eu; \
  if [ -n '$previous_backend' ]; then /usr/local/bin/docker tag '$previous_backend' '$rollback_backend'; fi; \
  if [ -n '$previous_web' ]; then /usr/local/bin/docker tag '$previous_web' '$rollback_web'; fi"

manifest="/private/tmp/blade-release-manifest-${RELEASE_ID}.txt"
{
  echo "release_id=$RELEASE_ID"
  echo "git_commit=$git_commit"
  echo "backend_image=$backend_image"
  echo "web_image=$web_image"
  echo "previous_backend_image=$rollback_backend"
  echo "previous_web_image=$rollback_web"
  echo "image_tar_sha256=$image_tar_sha"
  echo "flyway_target=$EXPECTED_FLYWAY_VERSION"
  echo "backup_basename=nas_blade_project_prod_$RELEASE_ID"
} > "$manifest"

scp -O blade-backend/Dockerfile "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-backend/Dockerfile"
scp -O blade-backend/target/blade-backend-1.0.0.jar "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-backend/target/"
scp -O blade-admin/Dockerfile "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-admin/Dockerfile"
scp -O -r blade-admin/dist "$NAS_USER@$NAS_HOST:$NAS_DIR/app/blade-admin/"
scp -O deploy/nas/nginx/default.conf "$NAS_USER@$NAS_HOST:$NAS_DIR/app/deploy/nas/nginx/default.conf"
scp -O deploy/nas/docker-compose.prod.yml "$NAS_USER@$NAS_HOST:$NAS_DIR/docker-compose.prod.yml"
scp -O deploy/nas/verify_order_release.sh "$NAS_USER@$NAS_HOST:$remote_release/verify_order_release.sh"
scp -O "$manifest" "$REHEARSAL_REPORT" "$NAS_USER@$NAS_HOST:$remote_release/"
scp -O "$IMAGE_TAR" "$NAS_USER@$NAS_HOST:$remote_release/blade-app-images.tar"

ssh "$NAS_USER@$NAS_HOST" "set -eu; echo '$image_tar_sha  blade-app-images.tar' > '$remote_release/SHA256SUMS'; cd '$remote_release'; sha256sum -c SHA256SUMS; /usr/local/bin/docker load -i blade-app-images.tar"

echo "Enable maintenance page and replace web first."
ssh "$NAS_USER@$NAS_HOST" "touch '$NAS_DIR/maintenance/enabled'"
maintenance_enabled=1
ssh "$NAS_USER@$NAS_HOST" "cd '$NAS_DIR'; BLADE_WEB_IMAGE='$web_image' /usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps web"
ssh "$NAS_USER@$NAS_HOST" "curl -k -s -o /dev/null -w '%{http_code}' https://127.0.0.1:8899/catalog | grep -qx 503"

echo "Replace backend; application startup applies additive Flyway migrations."
ssh "$NAS_USER@$NAS_HOST" "cd '$NAS_DIR'; BLADE_BACKEND_IMAGE='$backend_image' /usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps backend"
ssh "$NAS_USER@$NAS_HOST" "set -eu; i=0; until /usr/local/bin/docker exec blade-backend sh -c 'wget -qO- http://127.0.0.1:8080/api/user/info >/dev/null'; do i=\$((i+1)); [ \$i -lt 120 ] || exit 1; sleep 2; done"

echo "Run controlled legacy migration and idempotent replay."
migration_output="$(ssh "$NAS_USER@$NAS_HOST" "/usr/local/bin/docker exec blade-backend sh -lc 'java -Dloader.main=com.blade.order.migration.OrderLegacyMigrator -cp /app/blade-backend.jar org.springframework.boot.loader.launch.PropertiesLauncher --url \"\$BLADE_DB_URL\" --user \"\$BLADE_DB_USERNAME\" --password \"\$BLADE_DB_PASSWORD\" --tenant '$TENANT_ID' --execute --report /app/logs/migration-${RELEASE_ID}.md'")"
echo "$migration_output"
echo "$migration_output" | grep -q '人工核对=0'

replay_output="$(ssh "$NAS_USER@$NAS_HOST" "/usr/local/bin/docker exec blade-backend sh -lc 'java -Dloader.main=com.blade.order.migration.OrderLegacyMigrator -cp /app/blade-backend.jar org.springframework.boot.loader.launch.PropertiesLauncher --url \"\$BLADE_DB_URL\" --user \"\$BLADE_DB_USERNAME\" --password \"\$BLADE_DB_PASSWORD\" --tenant '$TENANT_ID' --execute --report /app/logs/migration-${RELEASE_ID}-replay.md'")"
echo "$replay_output"
echo "$replay_output" | grep -q '迁移=0 人工核对=0'

ssh "$NAS_USER@$NAS_HOST" "chmod +x '$remote_release/verify_order_release.sh'; EXPECTED_FLYWAY_VERSION='$EXPECTED_FLYWAY_VERSION' '$remote_release/verify_order_release.sh'"

echo "Trusted external TLS handshake must pass before traffic reopens (HTTP 503 is expected in maintenance)."
curl -sSI -o /dev/null "$AGENT_EXTERNAL_URL/catalog"

ssh "$NAS_USER@$NAS_HOST" "set -eu; \
  /usr/local/bin/docker tag '$backend_image' blade-backend:prod; \
  /usr/local/bin/docker tag '$web_image' blade-web:prod; \
  rm '$NAS_DIR/maintenance/enabled'; \
  curl -k -fsSI https://127.0.0.1:8899/catalog >/dev/null"
maintenance_enabled=0
trap - ERR

curl -fsSI "$AGENT_EXTERNAL_URL/catalog" >/dev/null

echo "Release completed: $RELEASE_ID"
echo "Manifest: $remote_release/blade-release-manifest-${RELEASE_ID}.txt"
