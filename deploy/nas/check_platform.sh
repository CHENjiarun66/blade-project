#!/usr/bin/env bash
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_HOST_WG="${NAS_HOST_WG:-10.13.13.1}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"

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

resolve_nas_host

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
