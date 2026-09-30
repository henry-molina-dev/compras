#!/usr/bin/env bash
# Back up (or restore) the Postgres database. Works in dev and prod: it talks to the running
# container directly, so it does not depend on which compose files are active.
#
#   ./backup.sh [--keep DAYS]          dump to backups/ordenes_compra-<timestamp>.sql.gz
#   ./backup.sh --restore <file> [--yes]   replace the database contents with a dump
#
# --keep DAYS  delete backups older than DAYS days after a successful dump (default 14)
# --yes        do not ask for confirmation before restoring
#
# Overrides (environment): POSTGRES_CONTAINER (default compras-postgres-1), DB_NAME
# (default ordenes_compra), BACKUP_DIR (default ./backups next to this script).
# Backups live on the same machine: also copy them off-host (scp, S3) and/or use Lightsail snapshots.
#
# Cron example (daily 03:00):  0 3 * * * cd ~/compose && ./backup.sh >> backups/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")"

CONTAINER="${POSTGRES_CONTAINER:-compras-postgres-1}"
DB_NAME="${DB_NAME:-ordenes_compra}"
DB_USER="app"
BACKUP_DIR="${BACKUP_DIR:-./backups}"

KEEP_DAYS=14
RESTORE_FILE=""
ASSUME_YES=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep) KEEP_DAYS="${2:?--keep needs a number of days}"; shift 2 ;;
    --restore) RESTORE_FILE="${2:?--restore needs a file}"; shift 2 ;;
    --yes) ASSUME_YES=true; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[[ "$KEEP_DAYS" =~ ^[0-9]+$ ]] || { echo "--keep must be a whole number of days" >&2; exit 2; }

docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true \
  || { echo "Container $CONTAINER is not running" >&2; exit 1; }

if [[ -n "$RESTORE_FILE" ]]; then
  [[ -f "$RESTORE_FILE" ]] || { echo "File not found: $RESTORE_FILE" >&2; exit 1; }
  gzip -t "$RESTORE_FILE" || { echo "$RESTORE_FILE is not a valid gzip file" >&2; exit 1; }

  echo ">> This REPLACES the contents of database '$DB_NAME' in $CONTAINER with $RESTORE_FILE."
  echo ">> Stop the backend first so nothing writes during the restore:  docker stop compras-backend-1"
  if [[ "$ASSUME_YES" == false ]]; then
    read -r -p "Type the database name ($DB_NAME) to continue: " answer
    [[ "$answer" == "$DB_NAME" ]] || { echo "Aborted." >&2; exit 1; }
  fi

  gunzip -c "$RESTORE_FILE" | docker exec -i "$CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 --single-transaction --quiet -o /dev/null
  echo ">> Restored $RESTORE_FILE into $DB_NAME. Start the backend again:  docker start compras-backend-1"
  exit 0
fi

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
FINAL="$BACKUP_DIR/ordenes_compra-$STAMP.sql.gz"
TMP="$FINAL.partial"
trap 'rm -f "$TMP"' EXIT

# --clean --if-exists makes the dump replace existing objects when restored.
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner \
  | gzip > "$TMP"
gzip -t "$TMP"
mv "$TMP" "$FINAL"
echo ">> Backup written: $FINAL ($(du -h "$FINAL" | cut -f1))"

# Only touches files this script created.
find "$BACKUP_DIR" -maxdepth 1 -name 'ordenes_compra-*.sql.gz' -mtime "+$KEEP_DAYS" -print -delete \
  | sed 's|^|>> Removed old backup: |'
