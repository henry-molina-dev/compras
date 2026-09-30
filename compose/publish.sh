#!/usr/bin/env bash
# Build both images for linux/amd64 and push them to Docker Hub (manual release, no CI).
#
#   ./publish.sh [version] [--deploy] [--allow-dirty]
#
# version      defaults to the git short SHA
# --deploy     after pushing, run deploy.sh on the server over ssh
# --allow-dirty  build from a dirty tree: the version gets a "-dirty" suffix and :latest is not moved
#
# Reads DOCKERHUB_USER and PROD_DOMAIN (and DEPLOY_HOST / DEPLOY_DIR for --deploy) from the
# environment or from .env next to this script. Run `docker login` once beforehand.
set -euo pipefail

cd "$(dirname "$0")"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# .env is not sourced: COMPOSE_FILE may contain ';', which bash would treat as a separator.
get_var() {
  local name="$1" value="${!1:-}"
  if [[ -z "$value" && -f .env ]]; then
    value="$(grep -E "^${name}=" .env | tail -n1 | cut -d= -f2- || true)"
  fi
  printf '%s' "$value"
}

VERSION=""
DEPLOY=false
ALLOW_DIRTY=false
for arg in "$@"; do
  case "$arg" in
    --deploy) DEPLOY=true ;;
    --allow-dirty) ALLOW_DIRTY=true ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) VERSION="$arg" ;;
  esac
done
VERSION="${VERSION:-$(git -C "$REPO_ROOT" rev-parse --short HEAD)}"

DOCKERHUB_USER="$(get_var DOCKERHUB_USER)"
DOMAIN="$(get_var PROD_DOMAIN)"
[[ -n "$DOCKERHUB_USER" ]] || { echo "DOCKERHUB_USER is required (env or .env)" >&2; exit 1; }
[[ -n "$DOMAIN" ]] || { echo "PROD_DOMAIN is required (env or .env): the frontend bakes https://compras-api.<PROD_DOMAIN> in at build time" >&2; exit 1; }

DIRTY=false
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  if [[ "$ALLOW_DIRTY" == false ]]; then
    echo "Working tree has uncommitted changes; commit them so the tag maps to a commit (or pass --allow-dirty)." >&2
    exit 1
  fi
  DIRTY=true
  VERSION="$VERSION-dirty"   # never looks like a clean release; :latest is not moved
fi

BACKEND_IMAGE="$DOCKERHUB_USER/ordenes-backend"
FRONTEND_IMAGE="$DOCKERHUB_USER/ordenes-frontend"

echo ">> Publishing version $VERSION (api: https://compras-api.$DOMAIN)"

# -t flags for one image: always the version; :latest only for clean builds.
tag_args() {
  TAGS=(-t "$1:$VERSION")
  if [[ "$DIRTY" == false ]]; then TAGS+=(-t "$1:latest"); fi
}

tag_args "$BACKEND_IMAGE"
docker buildx build --platform linux/amd64 --push "${TAGS[@]}" "$REPO_ROOT/backend"

tag_args "$FRONTEND_IMAGE"
docker buildx build --platform linux/amd64 --push \
  --build-arg "VITE_API_URL=https://compras-api.$DOMAIN" \
  "${TAGS[@]}" "$REPO_ROOT/frontend"

echo ">> Pushed $BACKEND_IMAGE:$VERSION and $FRONTEND_IMAGE:$VERSION"
if [[ "$DIRTY" == true ]]; then echo ">> Dirty build: :latest was NOT updated"; fi

if [[ "$DEPLOY" == true ]]; then
  DEPLOY_HOST="$(get_var DEPLOY_HOST)"
  DEPLOY_DIR="$(get_var DEPLOY_DIR)"
  [[ -n "$DEPLOY_HOST" && -n "$DEPLOY_DIR" ]] || { echo "--deploy needs DEPLOY_HOST and DEPLOY_DIR (env or .env)" >&2; exit 1; }
  echo ">> Deploying $VERSION to $DEPLOY_HOST:$DEPLOY_DIR"
  ssh "$DEPLOY_HOST" "cd '$DEPLOY_DIR' && ./deploy.sh '$VERSION'"
else
  echo ">> On the server: ./deploy.sh $VERSION   (or re-run with --deploy)"
fi
