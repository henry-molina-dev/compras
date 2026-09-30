#!/usr/bin/env bash
# Deploy a published version on the server: set IMAGE_TAG, pull, restart, show status.
#
#   ./deploy.sh <version>
#
# Rollback = run it again with the previous version (printed below).
# Always uses the base + prod files explicitly, so a stray COMPOSE_FILE in .env is ignored.
set -euo pipefail

cd "$(dirname "$0")"

VERSION="${1:-}"
[[ -n "$VERSION" ]] || { echo "Usage: ./deploy.sh <version>" >&2; exit 2; }
[[ -f .env ]] || { echo ".env not found in $(pwd); copy .env.example and fill it in" >&2; exit 1; }

PREVIOUS="$(grep -E '^IMAGE_TAG=' .env | tail -n1 | cut -d= -f2- || true)"

if grep -qE '^IMAGE_TAG=' .env; then
  sed -i.bak "s|^IMAGE_TAG=.*|IMAGE_TAG=$VERSION|" .env && rm -f .env.bak
else
  printf '\nIMAGE_TAG=%s\n' "$VERSION" >> .env
fi

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)

echo ">> Deploying $VERSION (previous: ${PREVIOUS:-none})"
"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up -d --remove-orphans
"${COMPOSE[@]}" ps

echo ">> Done. To roll back: ./deploy.sh ${PREVIOUS:-<previous-version>}"
