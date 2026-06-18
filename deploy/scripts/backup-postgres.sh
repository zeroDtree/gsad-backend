#!/usr/bin/env bash
# Backup gsad PostgreSQL database from the running compose stack.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

: "${DB_PASSWORD:?Set DB_PASSWORD in .env}"

STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${BACKUP_DIR:-$ROOT/backups}"
mkdir -p "$OUT_DIR"
OUT_FILE="$OUT_DIR/gsad_${STAMP}.sql.gz"

docker compose exec -T postgres pg_dump -U gsad gsad | gzip > "$OUT_FILE"
echo "Backup written to $OUT_FILE"
