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
BACKUP_ID="${BACKUP_ID:-$(date +%Y%m%d_%H%M%S)}"
BACKUP_BASENAME="${BACKUP_BASENAME:-nas_blade_project_prod_${BACKUP_ID}}"
LOCAL_BACKUP_DIR="${LOCAL_BACKUP_DIR:-/private/tmp/blade-production-backups}"

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

if [ "$EXECUTE" -ne 1 ]; then
  cat <<EOF
Dry run only. Production is unchanged.

This creates a transaction-consistent compressed SQL backup, schema-only backup,
SHA-256 files, and an independently verified copy outside the NAS at:
  $LOCAL_BACKUP_DIR/$BACKUP_BASENAME

Run for real:
  deploy/nas/backup_db.sh --execute
EOF
  exit 0
fi

resolve_nas_host
remote_backup_dir="$NAS_DIR/db-backups/$BACKUP_BASENAME"
local_backup_dir="$LOCAL_BACKUP_DIR/$BACKUP_BASENAME"

echo "Creating immutable backup set: $remote_backup_dir"
ssh "$NAS_USER@$NAS_HOST" "set -eu; mkdir -p '$remote_backup_dir'; \
  /usr/local/bin/docker exec blade-mysql sh -c 'mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --quick --default-character-set=utf8mb4 --routines --triggers --events \"\$MYSQL_DATABASE\"' \
    | gzip -c > '$remote_backup_dir/database.sql.gz'; \
  /usr/local/bin/docker exec blade-mysql sh -c 'mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --no-data --default-character-set=utf8mb4 --routines --triggers --events \"\$MYSQL_DATABASE\"' \
    | gzip -c > '$remote_backup_dir/schema.sql.gz'; \
  test -s '$remote_backup_dir/database.sql.gz'; \
  test -s '$remote_backup_dir/schema.sql.gz'; \
  cd '$remote_backup_dir'; sha256sum database.sql.gz schema.sql.gz > SHA256SUMS; \
  /usr/local/bin/docker exec blade-mysql sh -c 'mysql -N -uroot -p\"\$MYSQL_ROOT_PASSWORD\" \"\$MYSQL_DATABASE\" -e \"SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank\"' > flyway-history.tsv; \
  sha256sum -c SHA256SUMS"

mkdir -p "$local_backup_dir"
scp -O -r "$NAS_USER@$NAS_HOST:$remote_backup_dir/." "$local_backup_dir/"
(cd "$local_backup_dir" && shasum -a 256 -c SHA256SUMS)

echo "Backup verified on NAS and outside NAS."
echo "NAS:   $remote_backup_dir"
echo "Local: $local_backup_dir"
