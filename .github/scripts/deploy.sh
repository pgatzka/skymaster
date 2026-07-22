#!/usr/bin/env bash
#
# Deploy a container image from GHCR with docker compose, gate on the
# container's Docker HEALTHCHECK, and roll back to the previously running
# version on failure.
#
# Compose file(s) are downloaded at the exact commit the image was built from
# (OCI revision label) into a persistent per-environment directory, keeping a
# timestamped history of releases with `current`/`previous` symlinks.
#
# Environment variables (defaults in brackets; others are required):
#   IMAGE                  required, e.g. ghcr.io/owner/repo
#   TAG                    [latest]
#   TRIGGER_SHA            fallback revision if the image carries no label
#   SERVICE                compose service to deploy/track [app]
#   COMPOSE_PROJECT_NAME   required (docker compose project / stack name)
#   COMPOSE_FILE           required, colon-separated repo-relative paths
#   GITHUB_REPOSITORY      required, owner/repo (auto-set in Actions)
#   GH_TOKEN               token with contents:read (for private repos)
#   DEPLOYMENT_DIRECTORY   required, where release history is stored
#   KEEP_RELEASES          required, how many past releases to retain
#   HEALTH_TIMEOUT         required, seconds to wait for "healthy"
#   HEALTH_INTERVAL        required, seconds between polls
set -euo pipefail

IMAGE="${IMAGE:?IMAGE is required (e.g. ghcr.io/owner/repo)}"
TAG="${TAG:-latest}"
TRIGGER_SHA="${TRIGGER_SHA:-}"
SERVICE="${SERVICE:-app}"
: "${COMPOSE_PROJECT_NAME:?COMPOSE_PROJECT_NAME is required}"; export COMPOSE_PROJECT_NAME
: "${COMPOSE_FILE:?COMPOSE_FILE is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required (owner/repo)}"
GH_TOKEN="${GH_TOKEN:-}"
: "${DEPLOYMENT_DIRECTORY:?DEPLOYMENT_DIRECTORY is required}"
DEPLOYMENT_DIRECTORY="${DEPLOYMENT_DIRECTORY%/}"    # drop trailing slash
: "${KEEP_RELEASES:?KEEP_RELEASES is required}"
: "${HEALTH_TIMEOUT:?HEALTH_TIMEOUT is required}"
: "${HEALTH_INTERVAL:?HEALTH_INTERVAL is required}"

RELEASES_DIR="$DEPLOYMENT_DIRECTORY/releases"
mkdir -p "$RELEASES_DIR"
RELEASE_DIRECTORY=""   # set by deploy()

# --- helpers ---------------------------------------------------------------
image_revision() {  # $1 = image ref/id -> git sha it was built from (or "")
  docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$1" 2>/dev/null || true
}

service_container() {  # -> running container id for this stack's SERVICE (or "")
  docker ps -q \
    --filter "label=com.docker.compose.project=${COMPOSE_PROJECT_NAME}" \
    --filter "label=com.docker.compose.service=${SERVICE}" | head -n1
}

# Download the compose file(s) at a revision into the current RELEASE_DIRECTORY
# (created by deploy()). Echoes the colon-separated local file list.
# NOTE: this is called inside $(...) so any variable it sets would be lost;
# that's why RELEASE_DIRECTORY is created by the caller, not here.
fetch_compose_files() {  # $1 = revision
  local sha="$1" list="" out
  IFS=':' read -ra files <<< "$COMPOSE_FILE"
  for f in "${files[@]}"; do
    out="$RELEASE_DIRECTORY/$f"
    mkdir -p "$(dirname "$out")"
    echo "Fetching $f @ $sha -> $out" >&2
    curl -fsSL \
      ${GH_TOKEN:+-H "Authorization: Bearer $GH_TOKEN"} \
      -H "Accept: application/vnd.github.raw+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "https://api.github.com/repos/${GITHUB_REPOSITORY}/contents/${f}?ref=${sha}" \
      -o "$out"
    list="${list:+$list:}$out"
  done
  echo "$list"
}

deploy() {  # $1 = image ref, $2 = revision for its compose files
  # Create the release dir in THIS shell (not the subshell below) so
  # RELEASE_DIRECTORY is visible to fetch_compose_files, deploy.meta, mark_current.
  RELEASE_DIRECTORY="$RELEASES_DIR/$(date -u +%Y%m%dT%H%M%SZ)-${2:0:12}"
  mkdir -p "$RELEASE_DIRECTORY"
  local files
  files="$(fetch_compose_files "$2")"
  printf 'image=%s\nrevision=%s\ntime=%s\n' "$1" "$2" "$(date -u +%FT%TZ)" > "$RELEASE_DIRECTORY/deploy.meta"
  APP_IMAGE="$1" COMPOSE_FILE="$files" docker compose up -d --remove-orphans
}

# Point `current` at the given release (rotating the old current to `previous`).
mark_current() {  # $1 = release dir now live
  local cur="$DEPLOYMENT_DIRECTORY/current"
  if [ -L "$cur" ]; then
    ln -sfn "$(readlink "$cur")" "$DEPLOYMENT_DIRECTORY/previous"
  fi
  ln -sfn "$1" "$cur"
  echo "current -> $1"
}

prune_releases() {
  local keep="$KEEP_RELEASES" cur prev i=0 d
  cur="$(readlink -f "$DEPLOYMENT_DIRECTORY/current"  2>/dev/null || true)"
  prev="$(readlink -f "$DEPLOYMENT_DIRECTORY/previous" 2>/dev/null || true)"
  while IFS= read -r d; do
    d="${d%/}"; i=$((i+1))
    [ "$i" -le "$keep" ] && continue
    [ "$d" = "$cur" ]  && continue
    [ "$d" = "$prev" ] && continue
    echo "Pruning old release $d"
    rm -rf "$d"
  done < <(ls -1dt "$RELEASES_DIR"/*/ 2>/dev/null || true)
}

# Poll the container's Docker HEALTHCHECK status.
#   0 = healthy, 1 = unhealthy/timeout, 2 = no healthcheck defined
wait_healthy() {
  local deadline=$(( $(date +%s) + HEALTH_TIMEOUT )) cid status
  echo "Waiting for Docker health of '$SERVICE' (timeout ${HEALTH_TIMEOUT}s)..."
  while [ "$(date +%s)" -lt "$deadline" ]; do
    cid="$(service_container)"
    if [ -n "$cid" ]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || echo missing)"
      case "$status" in
        healthy)   echo "Container is healthy."; return 0 ;;
        unhealthy) echo "Container reported unhealthy."; return 1 ;;
        none)      echo "::error::Service '$SERVICE' has no HEALTHCHECK defined — cannot gate on Docker health."; return 2 ;;
        *)         echo "  status: ${status:-<none>} ..." ;;
      esac
    fi
    sleep "$HEALTH_INTERVAL"
  done
  echo "Timed out waiting for 'healthy'."
  return 1
}

# --- resolve NEW version ---------------------------------------------------
NEW_REF="${IMAGE}:${TAG}"
docker pull "$NEW_REF"

NEW_SHA="$(image_revision "$NEW_REF")"
if [ -z "$NEW_SHA" ]; then
  case "$TAG" in
    sha-*) NEW_SHA="${TAG#sha-}" ;;
    *)     NEW_SHA="$TRIGGER_SHA" ;;
  esac
fi
echo "New image $NEW_REF was built from revision: ${NEW_SHA:-<unknown>}"
echo "Release history: $RELEASES_DIR"

# --- capture PREVIOUS version ---------------------------------------------
PREV_CID="$(service_container)"
PREV_IMAGE=""; PREV_SHA=""
if [ -n "$PREV_CID" ]; then
  PREV_IMAGE=$(docker inspect --format '{{.Image}}' "$PREV_CID")
  PREV_SHA=$(image_revision "$PREV_IMAGE")
  echo "Previous image: $PREV_IMAGE (revision ${PREV_SHA:-<unknown>})"
else
  echo "No running '$SERVICE' — first deploy, nothing to roll back to."
fi

# --- deploy NEW ------------------------------------------------------------
echo "::group::Deploying $NEW_REF"
deploy "$NEW_REF" "$NEW_SHA"
echo "::endgroup::"

rc=0; wait_healthy || rc=$?
if [ "$rc" -eq 0 ]; then
  mark_current "$RELEASE_DIRECTORY"; prune_releases
  echo "✅ Deployment of $NEW_REF succeeded and is healthy."
  exit 0
elif [ "$rc" -eq 2 ]; then
  echo "::error::Aborting without rollback. Define a HEALTHCHECK for '$SERVICE'."
  echo "Failed release kept at: $RELEASE_DIRECTORY"
  exit 1
fi
echo "❌ New version failed its health check. Failed release kept at: $RELEASE_DIRECTORY"

# --- roll back to PREVIOUS -------------------------------------------------
if [ -z "$PREV_IMAGE" ]; then
  echo "::error::No previous version to roll back to. Leaving failed stack for inspection."
  docker compose logs --tail 100 "$SERVICE" 2>/dev/null || true
  exit 1
fi

echo "::group::Rolling back to $PREV_IMAGE"
deploy "$PREV_IMAGE" "$PREV_SHA"
echo "::endgroup::"

rc=0; wait_healthy || rc=$?
if [ "$rc" -eq 0 ]; then
  mark_current "$RELEASE_DIRECTORY"; prune_releases
  echo "::error::Deploy failed; rolled back to previous version ($PREV_IMAGE)."
  exit 1
else
  echo "::error::Deploy failed AND rollback failed — environment may be down!"
  docker compose logs --tail 100 "$SERVICE" 2>/dev/null || true
  exit 1
fi