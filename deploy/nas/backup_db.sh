#!/usr/bin/env bash
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.1.10}"
NAS_USER="${NAS_USER:-admin008}"
NAS_DIR="${NAS_DIR:-/volume2/blade}"
BACKUP_ID="${BACKUP_ID:-$(date +%Y%m%d_%H%M%S)}"
BACKUP_NAME="${BACKUP_NAME:-nas_blade_project_prod_${BACKUP_ID}.sql}"

echo "Creating NAS database backup: $BACKUP_NAME"

ssh "$NAS_USER@$NAS_HOST" "set -e; mkdir -p '$NAS_DIR/db-backups'; cd '$NAS_DIR'; \
  /usr/local/bin/docker exec blade-mysql sh -c 'mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --default-character-set=utf8mb4 --routines --triggers \"\$MYSQL_DATABASE\"' \
  > '$NAS_DIR/db-backups/$BACKUP_NAME'; \
  test -s '$NAS_DIR/db-backups/$BACKUP_NAME'; \
  ls -lh '$NAS_DIR/db-backups/$BACKUP_NAME'"

echo "Backup completed: $NAS_DIR/db-backups/$BACKUP_NAME"
