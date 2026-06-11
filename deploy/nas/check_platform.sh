#!/usr/bin/env bash
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"

ssh "$NAS_USER@$NAS_HOST" "set -e; \
  echo '=== system ==='; uname -a; uname -m; \
  echo '=== docker ==='; /usr/local/bin/docker version --format '{{.Server.Os}}/{{.Server.Arch}}'; \
  echo '=== compose ==='; /usr/local/bin/docker-compose version; \
  echo '=== production dir ==='; test -d '$NAS_DIR' && ls -ld '$NAS_DIR'; \
  echo '=== persistent dirs ==='; \
  test -d '$NAS_DIR/mysql' && ls -ld '$NAS_DIR/mysql'; \
  test -d '$NAS_DIR/uploads' && ls -ld '$NAS_DIR/uploads'; \
  echo '=== containers ==='; \
  cd '$NAS_DIR' && /usr/local/bin/docker-compose --env-file .env.prod -f docker-compose.prod.yml ps"
