#!/usr/bin/env bash
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_HOST_WG="${NAS_HOST_WG:-10.13.13.1}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"
BACKUP_ID="${BACKUP_ID:-$(date +%Y%m%d_%H%M%S)}"
BACKUP_NAME="${BACKUP_NAME:-nas_blade_project_prod_${BACKUP_ID}.sql}"

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

echo "Creating NAS database backup: $BACKUP_NAME"

ssh "$NAS_USER@$NAS_HOST" "set -e; mkdir -p '$NAS_DIR/db-backups'; cd '$NAS_DIR'; \
  /usr/local/bin/docker exec blade-mysql sh -c 'mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --default-character-set=utf8mb4 --routines --triggers \"\$MYSQL_DATABASE\"' \
  > '$NAS_DIR/db-backups/$BACKUP_NAME'; \
  test -s '$NAS_DIR/db-backups/$BACKUP_NAME'; \
  ls -lh '$NAS_DIR/db-backups/$BACKUP_NAME'"

echo "Backup completed: $NAS_DIR/db-backups/$BACKUP_NAME"
