#!/usr/bin/env bash
#
# Deploy a container image as a docker compose stack, on the host it runs on.
#
# The image is pulled and pinned to its immutable digest, the compose file(s)
# are materialised into a timestamped release directory, the rollout is gated
# on the containers' Docker HEALTHCHECK, and a rollout that does not become
# healthy is rolled back to the previous release automatically.
#
# Usage:
#   ./deploy.sh              deploy IMAGE_NAME:IMAGE_TAG
#   ./deploy.sh --dry-run    validate the configuration and print the plan
#   ./deploy.sh --rollback   redeploy the `previous` release
#   ./deploy.sh --help
#
# Exit codes: 0 success, 1 deploy failed (rolled back where possible),
#             2 bad arguments.
#
# CONFIGURATION
#   The configuration block below is the complete list of variables this script
#   reads, each with its default. Four are required - IMAGE_NAME, IMAGE_TAG,
#   COMPOSE_PROJECT_NAME, DEPLOYMENT_DIRECTORY - plus GITHUB_REPOSITORY when
#   fetching compose files from GitHub, and credentials when REGISTRY_AUTH is
#   not none. Missing ones stop the run before anything is touched; --dry-run
#   reports them and prints the plan.
#
# STATE ON DISK
#   $DEPLOYMENT_DIRECTORY/
#   |- .deploy.lock                      flock, one deploy at a time
#   |- .env                              optional, yours: compose variables
#   |- current  -> releases/...-a1b2c3d4 live release
#   |- previous -> releases/...-9f8e7d6c rollback target
#   `- releases/
#      `- 20260810T165954Z-a1b2c3d4/
#         |- compose.yml                 as of the commit the image was built at
#         |- compose.sh                  run compose commands against this release
#         `- deploy.meta                 image digest, revision, tag, timestamp
#
# MANAGING THE STACK BY HAND
#   Compose files keep their ${...} placeholders unexpanded on disk, so a bare
#   `docker compose -f releases/.../compose.yml down` fails with "required
#   variable APP_IMAGE is missing a value". Use the generated wrapper instead,
#   which carries the project name, the -f list and the resolved image ref and
#   works from any directory:
#
#     $DEPLOYMENT_DIRECTORY/current/compose.sh ps
#     $DEPLOYMENT_DIRECTORY/current/compose.sh logs -f
#     $DEPLOYMENT_DIRECTORY/current/compose.sh down
#
#   As a last resort that needs no files at all, `docker compose -p
#   $COMPOSE_PROJECT_NAME down` tears the stack down using the container labels.
#
#   Compose runs with --project-directory $DEPLOYMENT_DIRECTORY rather than the
#   release directory, because that path is stable across releases: put
#   host-specific compose variables in $DEPLOYMENT_DIRECTORY/.env (loaded
#   automatically, never written by this script, so it is safe for secrets), and
#   relative bind mounts keep resolving after old releases are pruned.
#
# WHAT THE COMPOSE FILE MUST PROVIDE
#   The image ref comes from a variable, and every service listed in SERVICES
#   needs a HEALTHCHECK - without one there is nothing to gate on and a broken
#   release would be reported as a success, which is why the default posture is
#   to abort rather than guess (see REQUIRE_HEALTHCHECK):
#
#     services:
#       app:
#         image: ${APP_IMAGE:?APP_IMAGE is required}
#         healthcheck:
#           test: ["CMD", "curl", "-fsS", "http://localhost:8080/health"]
#           interval: 10s
#           timeout: 3s
#           retries: 3
#           start_period: 15s
#
# BEHAVIOUR ON FAILURE
#   new version healthy         current/previous rotated, old releases and
#                               dangling images pruned, exit 0
#   never becomes healthy       previous release redeployed, logs dumped, exit 1
#   nothing to roll back to     failed stack left up for inspection, exit 1
#   rollback itself fails       loud error, logs dumped, exit 1 - needs a human
#   service has no HEALTHCHECK  abort WITHOUT rollback, exit 1
#   another deploy running      waits LOCK_TIMEOUT, then exit 1
#   Failed release directories are always kept on disk for inspection.
#
# RUNNING IT FROM GITHUB ACTIONS
#   Push the image first, then run this over SSH on the target host. Keep the
#   host-shaped variables (COMPOSE_PROJECT_NAME, COMPOSE_FILE, SERVICES,
#   DEPLOYMENT_DIRECTORY, ...) in the host's own environment - for example
#   /etc/default/deploy-<stack> sourced by a wrapper - so CI cannot repoint a
#   deploy at a different stack:
#
#     deploy:
#       runs-on: ubuntu-latest
#       steps:
#         - uses: appleboy/ssh-action@v1
#           with:
#             host: ${{ secrets.DEPLOY_HOST }}
#             username: deploy
#             key: ${{ secrets.DEPLOY_SSH_KEY }}
#             envs: IMAGE_NAME,IMAGE_TAG,TRIGGER_SHA,GITHUB_REPOSITORY,GH_TOKEN
#             script: /opt/deploy/deploy.sh
#           env:
#             IMAGE_NAME: ghcr.io/${{ github.repository }}
#             IMAGE_TAG: sha-${{ github.sha }}
#             TRIGGER_SHA: ${{ github.sha }}
#             GITHUB_REPOSITORY: ${{ github.repository }}
#             GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
#
#   When GITHUB_ACTIONS is set, the deploy and rollback phases are wrapped in
#   ::group:: log folds.

set -euo pipefail

readonly SCRIPT_NAME="${0##*/}"

# The variable the compose file reads the image ref from:
#   image: ${APP_IMAGE:?APP_IMAGE is required}
# Fixed by convention rather than configurable - every caller would set it to
# the same thing, and a mismatch between the two spellings is a deploy failure.
readonly IMAGE_ENV_VAR="APP_IMAGE"

# --- logging ---------------------------------------------------------------

log() {
    local level="$1"; shift
    printf '%s [%-7s] - %s\n' "$(date '+%d.%m.%Y %H:%M:%S.%3N')" "$level" "$*"
}

log_info() {
  log "INFO" "$@";
}

log_warning() {
  log "WARNING" "$@";
}

log_error() {
  log "ERROR" "$@" >&2;
}

log_debug() {
  [[ "${DEBUG:-}" == "true" ]] || return 0
  log "DEBUG" "$@";
}

# GitHub Actions log folding; no-ops outside Actions.
group_start() {
  [[ -n "${GITHUB_ACTIONS:-}" ]] && printf '::group::%s\n' "$*"
  log_info "$@"
}

group_end() {
  [[ -n "${GITHUB_ACTIONS:-}" ]] && printf '::endgroup::\n'
  return 0
}

# --- configuration ---------------------------------------------------------

# This block is the complete list of values the script takes from its
# environment - nothing outside it is read. Each entry states its default, or
# [required] when there is none and the run stops until you supply it.
#
# The four required ones identify *what* is being deployed and *where*: a
# wrong guess there would silently deploy the wrong thing, or write state to a
# surprise directory. Everything else defaults to a value that is either
# universal or fails loudly when it does not fit.
#
# Entries marked (conditional) are read only in the mode that uses them.

# --- image -----------------------------------------------------------------

# [required] Image repository without a tag, e.g. ghcr.io/pgatzka/todo-list.
IMAGE_NAME="${IMAGE_NAME:-}"

# [required] Tag to deploy, e.g. sha-abc1234 or v1.4.2. Resolved to an
# immutable digest before anything starts, so a moving tag cannot change under
# the rollout. Deliberately has no default: falling back to "latest" is how you
# ship a release nobody meant to ship.
IMAGE_TAG="${IMAGE_TAG:-}"

# [default: empty] Commit the deployment was triggered for, e.g.
# ${{ github.sha }}. Only a fallback: the revision is taken from the image's
# org.opencontainers.image.revision label, then from a sha-* tag, then from
# here. That revision decides which commit the compose files are fetched at, so
# with COMPOSE_SOURCE=github the run fails if all three are empty.
TRIGGER_SHA="${TRIGGER_SHA:-}"

# --- stack -----------------------------------------------------------------

# [required] Compose project (stack) name. Also the label docker finds the
# running containers by, so changing it orphans the existing stack.
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"

# [default: compose.yml] Colon-separated compose file paths, relative to the
# repository root, e.g. "deploy/compose.yml:deploy/compose.prod.yml". Must not
# be absolute or contain "..". A path that does not exist fails the run.
COMPOSE_FILE="${COMPOSE_FILE:-compose.yml}"

# [default: app] Comma- or space-separated compose services whose health gates
# the deploy, e.g. "app" or "app, worker". Checked against the compose file
# before the rollout starts, so a name that does not exist fails immediately.
SERVICES="${SERVICES:-app}"

# --- compose file source ---------------------------------------------------

# [default: github] Where compose files come from: "github" downloads them at
# the commit the image was built from, "local" copies them from
# COMPOSE_LOCAL_DIRECTORY.
COMPOSE_SOURCE="${COMPOSE_SOURCE:-github}"

# [required] (COMPOSE_SOURCE=github) owner/repo to fetch compose files from,
# e.g. pgatzka/todo-list. GitHub Actions sets this automatically.
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"

# [default: empty] (COMPOSE_SOURCE=github) Token with contents:read on
# GITHUB_REPOSITORY. Only needed for private repositories. Never logged.
GH_TOKEN="${GH_TOKEN:-}"

# [default: .] (COMPOSE_SOURCE=local) Directory the compose files are copied
# from, e.g. a checkout on the host. COMPOSE_FILE paths resolve inside it.
COMPOSE_LOCAL_DIRECTORY="${COMPOSE_LOCAL_DIRECTORY:-.}"

# --- release history -------------------------------------------------------

# [required] Directory owning this stack's state: release history,
# current/previous symlinks, the deploy lock and your optional .env, e.g.
# /opt/stacks/todo-list. Also the compose project directory, so relative bind
# mounts and env_file paths in the compose files resolve against it.
DEPLOYMENT_DIRECTORY="${DEPLOYMENT_DIRECTORY:-}"
DEPLOYMENT_DIRECTORY="${DEPLOYMENT_DIRECTORY%/}"   # drop trailing slash

# [default: 5] How many release directories to keep. current and previous are
# never pruned regardless of this number.
KEEP_RELEASES="${KEEP_RELEASES:-5}"

# --- health gating ---------------------------------------------------------

# [default: 180] Seconds to wait for every tracked service to report healthy
# before the rollout counts as failed. Must cover the image's start_period.
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

# [default: 5] Seconds between health polls. Must be at least 1.
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

# [default: true] "true" | "false". When true, a tracked service without a
# HEALTHCHECK aborts the deploy rather than being reported as a success there
# is no way to verify.
REQUIRE_HEALTHCHECK="${REQUIRE_HEALTHCHECK:-true}"

# --- registry authentication -----------------------------------------------

# [default: none] How to authenticate to the registry:
#   none           use the credentials the Docker daemon already holds
#   password       log in with REGISTRY_PASSWORD
#   password-file  log in with the contents of REGISTRY_PASSWORD_FILE
REGISTRY_AUTH="${REGISTRY_AUTH:-none}"

# [default: derived from IMAGE_NAME] (REGISTRY_AUTH != none) Registry host to
# log in to, e.g. ghcr.io.
REGISTRY="${REGISTRY:-}"

# [required] (REGISTRY_AUTH != none) Registry username. For ghcr.io any
# non-empty value works alongside a token; Actions provides GITHUB_ACTOR.
REGISTRY_USERNAME="${REGISTRY_USERNAME:-}"

# [required] (REGISTRY_AUTH=password) Registry password or token. Passed to
# docker login on stdin and never logged. Prefer password-file on a host.
REGISTRY_PASSWORD="${REGISTRY_PASSWORD:-}"

# [required] (REGISTRY_AUTH=password-file) File holding the registry password,
# e.g. /etc/deploy/ghcr.token. Must be readable and non-empty.
REGISTRY_PASSWORD_FILE="${REGISTRY_PASSWORD_FILE:-}"

# --- housekeeping ----------------------------------------------------------

# [default: true] "true" | "false". Prune dangling images after a successful
# deploy. Only untagged images that no container references are removed.
PRUNE_IMAGES="${PRUNE_IMAGES:-true}"

# [default: 300] Seconds to wait for a concurrent deploy of this stack to
# finish before giving up.
LOCK_TIMEOUT="${LOCK_TIMEOUT:-300}"

# [default: false] "true" | "false". Verbose logging. Never logs secrets.
DEBUG="${DEBUG:-false}"

# Runtime flags
DRY_RUN=false
ROLLBACK_ONLY=false

# Runtime state
RELEASES_DIR=""
RELEASE_DIRECTORY=""      # release dir of the deploy currently in flight
ACTIVE_COMPOSE_FILE=""    # colon-separated local compose paths of that release
ACTIVE_IMAGE_REF=""       # image ref that release was deployed with
LOGGED_IN_REGISTRY=""     # set once `docker login` succeeded, for logout
SERVICE_LIST=()

usage() {
  cat <<EOF
${SCRIPT_NAME} - deploy a container image as a docker compose stack.

Options:
  -n, --dry-run    Validate configuration and print the plan; change nothing.
  -r, --rollback   Redeploy the previous release instead of deploying a new image.
  -h, --help       Show this help.

Required - no default, the run stops until you set them:
  IMAGE_NAME              Image repository, e.g. ghcr.io/owner/repo
  IMAGE_TAG               Tag to deploy, e.g. sha-abc1234
  COMPOSE_PROJECT_NAME    Compose project (stack) name
  DEPLOYMENT_DIRECTORY    Directory holding this stack's state

Optional - shown with their defaults:
  COMPOSE_FILE            compose.yml   Colon-separated repo-relative paths
  SERVICES                app           Services whose health gates the deploy
  COMPOSE_SOURCE          github        github | local
  TRIGGER_SHA             (empty)       Revision fallback for compose fetching
  KEEP_RELEASES           5             Release directories to retain
  HEALTH_TIMEOUT          180           Seconds to wait for health
  HEALTH_INTERVAL         5             Seconds between health polls
  REQUIRE_HEALTHCHECK     true          Abort if a service has no HEALTHCHECK
  REGISTRY_AUTH           none          none | password | password-file
  PRUNE_IMAGES            true          Prune dangling images after success
  LOCK_TIMEOUT            300           Seconds to wait for a concurrent deploy
  DEBUG                   false         Verbose logging

Conditional - only read in the mode that uses them:
  GITHUB_REPOSITORY       required      (COMPOSE_SOURCE=github)
  GH_TOKEN                (empty)       (COMPOSE_SOURCE=github, private repos)
  COMPOSE_LOCAL_DIRECTORY .             (COMPOSE_SOURCE=local)
  REGISTRY                from IMAGE_NAME  (REGISTRY_AUTH != none)
  REGISTRY_USERNAME       required      (REGISTRY_AUTH != none)
  REGISTRY_PASSWORD       required      (REGISTRY_AUTH=password)
  REGISTRY_PASSWORD_FILE  required      (REGISTRY_AUTH=password-file)

Each variable is documented in the configuration block at the top of this
script, along with the state layout, failure behaviour and a CI example.
EOF
}

parse_arguments() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -n|--dry-run)  DRY_RUN=true; shift ;;
      -r|--rollback) ROLLBACK_ONLY=true; shift ;;
      -h|--help)     usage; exit 0 ;;
      *)
        log_error "Unknown argument: $1"
        usage >&2
        exit 2
        ;;
    esac
  done
}

require_variable() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        log_error "${name} is not set"
        exit 1
    fi
    log_info "${name} is set to '${!name}'"
}

# Like require_variable, but never echoes the value.
require_secret() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        log_error "${name} is not set"
        exit 1
    fi
    log_info "${name} is set (value hidden)"
}

require_integer() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    log_error "${name} is not set"
    exit 1
  fi
  if [[ ! "${!name}" =~ ^[0-9]+$ ]]; then
    log_error "${name} must be a non-negative integer, got '${!name}'"
    exit 1
  fi
  log_info "${name} is set to '${!name}'"
}

# Booleans are validated strictly: a typo like 'yes' or 'TRUE' would otherwise
# be silently read as false and quietly disable a safety gate.
require_boolean() {
  local name="$1"
  case "${!name:-}" in
    true|false)
      log_info "${name} is set to '${!name}'"
      ;;
    "")
      log_error "${name} is not set (expected 'true' or 'false')"
      exit 1
      ;;
    *)
      log_error "${name} must be exactly 'true' or 'false', got '${!name}'"
      exit 1
      ;;
  esac
}

validate_configuration() {
  require_variable IMAGE_NAME
  require_variable IMAGE_TAG
  require_variable COMPOSE_PROJECT_NAME
  require_variable DEPLOYMENT_DIRECTORY
  require_variable COMPOSE_FILE
  require_variable SERVICES
  require_variable COMPOSE_SOURCE
  require_integer KEEP_RELEASES
  require_integer HEALTH_TIMEOUT
  require_integer HEALTH_INTERVAL
  require_integer LOCK_TIMEOUT
  require_boolean REQUIRE_HEALTHCHECK
  require_boolean PRUNE_IMAGES
  require_boolean DEBUG

  if [[ "$HEALTH_INTERVAL" -lt 1 ]]; then
    log_error "HEALTH_INTERVAL must be at least 1 second"
    exit 1
  fi

  case "$COMPOSE_SOURCE" in
    github)
      require_variable GITHUB_REPOSITORY
      # GH_TOKEN is optional: public repositories are readable without one.
      if [[ -n "$GH_TOKEN" ]]; then
        log_info "GH_TOKEN is set (value hidden)"
      else
        log_info "GH_TOKEN is not set; fetching compose files unauthenticated"
      fi
      ;;
    local)
      require_variable COMPOSE_LOCAL_DIRECTORY
      ;;
    *)
      log_error "COMPOSE_SOURCE must be 'github' or 'local', got '${COMPOSE_SOURCE}'"
      exit 1
      ;;
  esac

  # Only the variables the selected auth mode actually uses are required;
  # demanding both REGISTRY_PASSWORD and REGISTRY_PASSWORD_FILE would be
  # impossible to satisfy.
  require_variable REGISTRY_AUTH
  case "$REGISTRY_AUTH" in
    none)
      ;;
    password)
      [[ -z "$REGISTRY" ]] && REGISTRY="$(registry_host)"
      log_info "REGISTRY is set to '${REGISTRY}'"
      require_variable REGISTRY_USERNAME
      require_secret REGISTRY_PASSWORD
      ;;
    password-file)
      [[ -z "$REGISTRY" ]] && REGISTRY="$(registry_host)"
      log_info "REGISTRY is set to '${REGISTRY}'"
      require_variable REGISTRY_USERNAME
      require_variable REGISTRY_PASSWORD_FILE
      if [[ ! -r "$REGISTRY_PASSWORD_FILE" ]]; then
        log_error "REGISTRY_PASSWORD_FILE '${REGISTRY_PASSWORD_FILE}' is not readable"
        exit 1
      fi
      ;;
    *)
      log_error "REGISTRY_AUTH must be 'none', 'password' or 'password-file', got '${REGISTRY_AUTH}'"
      exit 1
      ;;
  esac

  # Compose paths are joined onto a directory we create; keep them relative and
  # inside it.
  local path service
  local -a compose_paths raw_services
  IFS=':' read -ra compose_paths <<< "$COMPOSE_FILE"
  for path in "${compose_paths[@]}"; do
    if [[ -z "$path" || "$path" == /* || "$path" == *".."* ]]; then
      log_error "COMPOSE_FILE entry must be a relative path without '..': '${path}'"
      exit 1
    fi
  done

  IFS=', ' read -ra raw_services <<< "$SERVICES"
  for service in "${raw_services[@]}"; do
    [[ -n "$service" ]] && SERVICE_LIST+=("$service")
  done
  if [[ ${#SERVICE_LIST[@]} -eq 0 ]]; then
    log_error "SERVICES did not contain any service name"
    exit 1
  fi

  export COMPOSE_PROJECT_NAME
  RELEASES_DIR="$DEPLOYMENT_DIRECTORY/releases"
}

# --- locking ---------------------------------------------------------------

acquire_lock() {
  local lock_file="$DEPLOYMENT_DIRECTORY/.deploy.lock"
  exec 9>"$lock_file"
  if ! flock -w "$LOCK_TIMEOUT" 9; then
    log_error "Another deployment holds ${lock_file} (waited ${LOCK_TIMEOUT}s). Aborting."
    exit 1
  fi
  log_debug "Acquired deployment lock ${lock_file}"
}

on_exit() {
  local rc=$?
  if [[ -n "$LOGGED_IN_REGISTRY" ]]; then
    docker logout "$LOGGED_IN_REGISTRY" >/dev/null 2>&1 || true
    log_debug "Logged out of ${LOGGED_IN_REGISTRY}"
  fi
  exec 9>&- || true
  return "$rc"
}

# --- docker helpers --------------------------------------------------------

# docker compose, scoped to the release currently deployed.
#
# --project-directory is pinned to DEPLOYMENT_DIRECTORY rather than defaulting
# to the release directory: it is stable across releases, so relative bind
# mounts and env_file paths keep resolving after old releases are pruned, and
# a host-managed ${DEPLOYMENT_DIRECTORY}/.env is picked up automatically.
compose() {
  env COMPOSE_FILE="$ACTIVE_COMPOSE_FILE" \
      "${IMAGE_ENV_VAR}=${ACTIVE_IMAGE_REF}" \
      docker compose --project-directory "$DEPLOYMENT_DIRECTORY" "$@"
}

# Write a self-contained wrapper into the release directory so an operator can
# run any compose command by hand without re-supplying the interpolated
# variables: `${DEPLOYMENT_DIRECTORY}/current/compose.sh down`.
write_compose_wrapper() {
  local wrapper="$RELEASE_DIRECTORY/compose.sh"
  {
    printf '#!/usr/bin/env bash\n'
    printf '#\n'
    printf '# Generated by deploy.sh for release %s\n' "${RELEASE_DIRECTORY##*/}"
    printf '# Runs docker compose against this release with the image ref it was\n'
    printf '# deployed with, so ${%s} is always resolved.\n' "$IMAGE_ENV_VAR"
    printf '#\n'
    printf '#   ./compose.sh ps\n'
    printf '#   ./compose.sh logs -f\n'
    printf '#   ./compose.sh down\n'
    printf 'set -euo pipefail\n'
    printf 'exec env COMPOSE_PROJECT_NAME=%q COMPOSE_FILE=%q %s=%q \\\n' \
      "$COMPOSE_PROJECT_NAME" "$ACTIVE_COMPOSE_FILE" "$IMAGE_ENV_VAR" "$ACTIVE_IMAGE_REF"
    printf '  docker compose --project-directory %q "$@"\n' "$DEPLOYMENT_DIRECTORY"
  } > "$wrapper"
  chmod +x "$wrapper"
  log_debug "Wrote compose wrapper ${wrapper}"
}

check_docker() {
  if ! docker info >/dev/null 2>&1; then
    log_error "Cannot talk to the Docker daemon. Is it running and is this user in the docker group?"
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    log_error "'docker compose' is not available. Install the Compose v2 plugin."
    exit 1
  fi
  log_debug "Docker daemon reachable: $(docker version --format '{{.Server.Version}}')"
}

# Registry host that IMAGE_NAME points at, used when REGISTRY is not set
# explicitly. "ghcr.io/owner/repo" -> ghcr.io, "owner/repo" -> docker.io.
registry_host() {
  local first="${IMAGE_NAME%%/*}"
  if [[ "$IMAGE_NAME" == */* && ( "$first" == *.* || "$first" == *:* || "$first" == "localhost" ) ]]; then
    printf '%s' "$first"
  else
    printf 'docker.io'
  fi
}

registry_login() {
  case "$REGISTRY_AUTH" in
    none)
      log_info "REGISTRY_AUTH=none: using the credentials the Docker daemon already holds"
      return 0
      ;;
    password-file)
      REGISTRY_PASSWORD="$(< "$REGISTRY_PASSWORD_FILE")"
      if [[ -z "$REGISTRY_PASSWORD" ]]; then
        log_error "REGISTRY_PASSWORD_FILE '${REGISTRY_PASSWORD_FILE}' is empty"
        exit 1
      fi
      ;;
  esac

  log_info "Logging in to ${REGISTRY} as ${REGISTRY_USERNAME}"
  if ! printf '%s' "$REGISTRY_PASSWORD" \
      | docker login "$REGISTRY" --username "$REGISTRY_USERNAME" --password-stdin >/dev/null 2>&1; then
    log_error "docker login to ${REGISTRY} failed"
    exit 1
  fi
  LOGGED_IN_REGISTRY="$REGISTRY"
}

pull() {
    log_info "Pulling $*"
    local output rc=0
    output="$(DOCKER_CLI_HINTS=false docker pull "$@" -q 2>&1)" || rc=$?
    if [[ "$rc" -ne 0 ]]; then
      log_error "docker pull failed for $*:"
      log_error "${output}"
      exit 1
    fi
    log_info "${output}"
}

image_revision() {  # $1 = image ref/id -> git sha it was built from (or "")
  docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$1" 2>/dev/null || true
}

# Immutable IMAGE_NAME@sha256:... ref for a pulled image (empty if unknown).
image_digest_ref() {  # $1 = image ref
  local digests digest
  digests="$(docker inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$1" 2>/dev/null || true)"
  while IFS= read -r digest; do
    [[ -z "$digest" ]] && continue
    if [[ "$digest" == "${IMAGE_NAME}@"* ]]; then
      printf '%s' "$digest"
      return 0
    fi
  done <<< "$digests"
  return 0
}

# --- release bookkeeping ---------------------------------------------------

meta_get() {  # $1 = meta file, $2 = key -> value (or "")
  [[ -f "$1" ]] || return 0
  local line
  while IFS= read -r line; do
    if [[ "$line" == "$2="* ]]; then
      printf '%s' "${line#*=}"
      return 0
    fi
  done < "$1"
  return 0
}

new_release_directory() {  # $1 = revision -> path
  local base suffix=1 candidate
  base="$RELEASES_DIR/$(date -u +%Y%m%dT%H%M%SZ)-${1:0:12}"
  candidate="$base"
  while [[ -e "$candidate" ]]; do
    candidate="${base}.${suffix}"
    suffix=$((suffix + 1))
  done
  printf '%s' "$candidate"
}

# Download the compose file(s) at $1 into RELEASE_DIRECTORY, preserving their
# repo-relative layout. Sets ACTIVE_COMPOSE_FILE.
fetch_compose_files() {  # $1 = revision
  local revision="$1" path target list=""
  local -a files auth_header=()
  IFS=':' read -ra files <<< "$COMPOSE_FILE"

  # Built as an array: an unquoted ${VAR:+-H "..."} would word-split the header.
  [[ -n "$GH_TOKEN" ]] && auth_header=(-H "Authorization: Bearer ${GH_TOKEN}")

  for path in "${files[@]}"; do
    target="$RELEASE_DIRECTORY/$path"
    mkdir -p "$(dirname "$target")"

    case "$COMPOSE_SOURCE" in
      github)
        if [[ -z "$revision" ]]; then
          log_error "Cannot fetch compose files: no revision known (set TRIGGER_SHA or label the image)"
          exit 1
        fi
        log_info "Fetching ${path} @ ${revision:0:12} -> ${target}"
        if ! curl -fsSL \
            "${auth_header[@]}" \
            -H "Accept: application/vnd.github.raw+json" \
            -H "X-GitHub-Api-Version: 2022-11-28" \
            "https://api.github.com/repos/${GITHUB_REPOSITORY}/contents/${path}?ref=${revision}" \
            -o "$target"; then
          log_error "Failed to download ${path} at ${revision} from ${GITHUB_REPOSITORY}"
          exit 1
        fi
        ;;
      local)
        local source_path="${COMPOSE_LOCAL_DIRECTORY%/}/$path"
        if [[ ! -f "$source_path" ]]; then
          log_error "Compose file '${source_path}' not found"
          exit 1
        fi
        log_info "Copying ${source_path} -> ${target}"
        cp "$source_path" "$target"
        ;;
    esac

    list="${list:+$list:}$target"
  done

  ACTIVE_COMPOSE_FILE="$list"
}

# Fail before touching the running stack if SERVICES names something the
# compose file does not define - otherwise the health gate would simply wait
# out HEALTH_TIMEOUT looking for a container that is never going to appear.
validate_services_exist() {
  local available service
  available="$(compose config --services 2>/dev/null || true)"

  if [[ -z "$available" ]]; then
    log_warning "Could not list services from the compose file(s); skipping the service name check"
    return 0
  fi

  for service in "${SERVICE_LIST[@]}"; do
    if ! grep -qxF -- "$service" <<< "$available"; then
      log_error "SERVICES names '${service}', which is not defined in ${COMPOSE_FILE}"
      log_error "Services defined: $(printf '%s' "$available" | tr '\n' ' ')"
      exit 1
    fi
  done
  log_debug "All tracked services exist in the compose file"
}

# Bring the stack up from a fresh release directory.
deploy() {  # $1 = image ref, $2 = revision
  local image_ref="$1" revision="$2"

  RELEASE_DIRECTORY="$(new_release_directory "$revision")"
  mkdir -p "$RELEASE_DIRECTORY"
  ACTIVE_IMAGE_REF="$image_ref"

  fetch_compose_files "$revision"

  {
    printf 'image=%s\n'    "$image_ref"
    printf 'revision=%s\n' "$revision"
    printf 'tag=%s\n'      "$IMAGE_TAG"
    printf 'time=%s\n'     "$(date -u +%FT%TZ)"
    printf 'services=%s\n' "${SERVICE_LIST[*]}"
  } > "$RELEASE_DIRECTORY/deploy.meta"

  write_compose_wrapper
  validate_services_exist

  log_info "Release directory: ${RELEASE_DIRECTORY}"
  compose up -d --remove-orphans
}

# Redeploy an existing release directory as-is (used by --rollback).
redeploy_release() {  # $1 = release dir
  local release="$1" image_ref revision path list=""
  local -a files
  image_ref="$(meta_get "$release/deploy.meta" image)"
  revision="$(meta_get "$release/deploy.meta" revision)"

  if [[ -z "$image_ref" ]]; then
    log_error "Release ${release} has no recorded image in deploy.meta"
    exit 1
  fi

  IFS=':' read -ra files <<< "$COMPOSE_FILE"
  for path in "${files[@]}"; do
    if [[ ! -f "$release/$path" ]]; then
      log_error "Release ${release} is missing compose file ${path}"
      exit 1
    fi
    list="${list:+$list:}$release/$path"
  done

  RELEASE_DIRECTORY="$release"
  ACTIVE_COMPOSE_FILE="$list"
  ACTIVE_IMAGE_REF="$image_ref"

  write_compose_wrapper

  log_info "Redeploying ${image_ref} (revision ${revision:-<unknown>}) from ${release}"
  pull "$image_ref"
  compose up -d --remove-orphans
}

# Point `current` at a release, rotating the old `current` to `previous`.
mark_current() {  # $1 = release dir now live
  local current="$DEPLOYMENT_DIRECTORY/current" existing
  existing="$(readlink "$current" 2>/dev/null || true)"

  if [[ -n "$existing" && "$existing" != "$1" ]]; then
    ln -sfn "$existing" "$DEPLOYMENT_DIRECTORY/previous"
  fi
  ln -sfn "$1" "$current"
  log_info "current -> $1"
  log_info "Manage this stack with: ${current}/compose.sh [ps|logs -f|down|restart]"
}

prune_releases() {
  local current previous index=0 dir
  current="$(readlink -f "$DEPLOYMENT_DIRECTORY/current" 2>/dev/null || true)"
  previous="$(readlink -f "$DEPLOYMENT_DIRECTORY/previous" 2>/dev/null || true)"

  while IFS= read -r dir; do
    dir="${dir%/}"
    index=$((index + 1))
    [[ "$index" -le "$KEEP_RELEASES" ]] && continue
    [[ "$dir" == "$current" ]] && continue
    [[ "$dir" == "$previous" ]] && continue
    log_info "Pruning old release ${dir}"
    rm -rf "$dir"
  done < <(ls -1dt "$RELEASES_DIR"/*/ 2>/dev/null || true)
}

prune_images() {
  [[ "$PRUNE_IMAGES" == "true" ]] || return 0
  local output
  # Dangling images only - never touches images a container still references.
  output="$(docker image prune --force 2>&1 || true)"
  log_info "Image cleanup: $(printf '%s' "$output" | tail -n1)"
}

# --- health gating ---------------------------------------------------------

# Poll the Docker HEALTHCHECK of every tracked service.
#   0 = all healthy, 1 = unhealthy/crashed/timeout, 2 = no healthcheck defined
wait_healthy() {
  local deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
  local service cid pending state health exit_code

  log_info "Waiting up to ${HEALTH_TIMEOUT}s for services to become healthy: ${SERVICE_LIST[*]}"

  while :; do
    pending=0

    for service in "${SERVICE_LIST[@]}"; do
      local containers=()
      mapfile -t containers < <(compose ps --all --quiet "$service" 2>/dev/null || true)

      if [[ ${#containers[@]} -eq 0 ]]; then
        log_info "  ${service}: no container yet"
        pending=1
        continue
      fi

      for cid in "${containers[@]}"; do
        [[ -z "$cid" ]] && continue
        IFS='|' read -r state health exit_code <<< "$(
          docker inspect --format \
            '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|{{.State.ExitCode}}' \
            "$cid" 2>/dev/null || printf 'missing|none|0'
        )"

        case "${state}/${health}" in
          running/healthy)
            log_debug "  ${service}: healthy"
            ;;
          running/none)
            if [[ "$REQUIRE_HEALTHCHECK" == "true" ]]; then
              log_error "Service '${service}' has no HEALTHCHECK defined - cannot gate on Docker health."
              return 2
            fi
            log_info "  ${service}: running (no healthcheck, accepted)"
            ;;
          running/starting)
            log_info "  ${service}: starting"
            pending=1
            ;;
          running/unhealthy)
            log_error "  ${service}: reported unhealthy"
            return 1
            ;;
          exited/*)
            log_error "  ${service}: container exited with code ${exit_code}"
            return 1
            ;;
          dead/*)
            log_error "  ${service}: container is dead"
            return 1
            ;;
          restarting/*)
            log_warning "  ${service}: restarting"
            pending=1
            ;;
          *)
            log_info "  ${service}: ${state}"
            pending=1
            ;;
        esac
      done
    done

    if [[ "$pending" -eq 0 ]]; then
      log_info "All tracked services are healthy."
      return 0
    fi

    if [[ "$(date +%s)" -ge "$deadline" ]]; then
      log_error "Timed out after ${HEALTH_TIMEOUT}s waiting for services to become healthy."
      return 1
    fi

    sleep "$HEALTH_INTERVAL"
  done
}

dump_logs() {
  local service
  log_warning "Recent logs from the failing stack:"
  for service in "${SERVICE_LIST[@]}"; do
    compose logs --tail 100 "$service" 2>/dev/null || true
  done
}

# --- deployment steps ------------------------------------------------------

NEW_REF=""
NEW_SHA=""
PREV_REF=""
PREV_SHA=""
PREV_RELEASE=""

resolve_new_image() {
  local tagged_ref="${IMAGE_NAME}:${IMAGE_TAG}"
  pull "$tagged_ref"

  NEW_SHA="$(image_revision "$tagged_ref")"
  if [[ -z "$NEW_SHA" ]]; then
    case "$IMAGE_TAG" in
      sha-*) NEW_SHA="${IMAGE_TAG#sha-}" ;;
      *)     NEW_SHA="$TRIGGER_SHA" ;;
    esac
  fi

  # Pin to the digest so the rollout is reproducible and rollback has an exact
  # target even if the tag moves underneath us.
  NEW_REF="$(image_digest_ref "$tagged_ref")"
  if [[ -z "$NEW_REF" ]]; then
    log_warning "${tagged_ref} has no repository digest (never pushed?); deploying the mutable tag instead"
    NEW_REF="$tagged_ref"
  fi

  log_info "Deploying ${NEW_REF}"
  log_info "Built from revision: ${NEW_SHA:-<unknown>}"
}

resolve_previous_release() {
  local current cid
  current="$(readlink -f "$DEPLOYMENT_DIRECTORY/current" 2>/dev/null || true)"

  if [[ -n "$current" && -d "$current" ]]; then
    PREV_RELEASE="$current"
    PREV_REF="$(meta_get "$current/deploy.meta" image)"
    PREV_SHA="$(meta_get "$current/deploy.meta" revision)"
  fi

  # No release history yet (first run of this script against a live stack):
  # fall back to whatever the running container is using.
  if [[ -z "$PREV_REF" ]]; then
    cid="$(docker ps --quiet \
      --filter "label=com.docker.compose.project=${COMPOSE_PROJECT_NAME}" \
      --filter "label=com.docker.compose.service=${SERVICE_LIST[0]}" | head -n1)"
    if [[ -n "$cid" ]]; then
      PREV_REF="$(docker inspect --format '{{.Image}}' "$cid")"
      PREV_SHA="$(image_revision "$PREV_REF")"
    fi
  fi

  if [[ -n "$PREV_REF" ]]; then
    log_info "Previous version: ${PREV_REF} (revision ${PREV_SHA:-<unknown>})"
  else
    log_info "No previous version found - this is a first deploy, nothing to roll back to."
  fi
}

print_plan() {
  log_info "--- dry run: no changes will be made ---"
  log_info "  stack              : ${COMPOSE_PROJECT_NAME}"
  log_info "  image              : ${IMAGE_NAME}:${IMAGE_TAG}"
  log_info "  services gated     : ${SERVICE_LIST[*]}"
  log_info "  compose source     : ${COMPOSE_SOURCE} (${COMPOSE_FILE})"
  log_info "  release history    : ${RELEASES_DIR} (keep ${KEEP_RELEASES})"
  log_info "  health gate        : ${HEALTH_TIMEOUT}s timeout, ${HEALTH_INTERVAL}s interval, required=${REQUIRE_HEALTHCHECK}"
  log_info "  registry auth      : ${REGISTRY_AUTH}${REGISTRY:+ (${REGISTRY} as ${REGISTRY_USERNAME})}"
  log_info "  would deploy       : ${IMAGE_NAME}:${IMAGE_TAG} -> new release directory"
  log_info "  would roll back to : ${PREV_REF:-<nothing>}"
}

rollback() {
  group_start "Rolling back to ${PREV_REF}"
  if [[ -n "$PREV_RELEASE" && -f "$PREV_RELEASE/deploy.meta" ]]; then
    redeploy_release "$PREV_RELEASE"
  else
    deploy "$PREV_REF" "$PREV_SHA"
  fi
  group_end

  local rc=0
  wait_healthy || rc=$?
  if [[ "$rc" -eq 0 ]]; then
    mark_current "$RELEASE_DIRECTORY"
    prune_releases
    log_error "Deploy failed; rolled back to the previous version (${PREV_REF})."
    return 1
  fi

  log_error "Deploy failed AND rollback failed - the stack may be down!"
  dump_logs
  return 1
}

run_rollback_only() {
  local previous
  previous="$(readlink -f "$DEPLOYMENT_DIRECTORY/previous" 2>/dev/null || true)"
  if [[ -z "$previous" || ! -d "$previous" ]]; then
    log_error "No previous release recorded at ${DEPLOYMENT_DIRECTORY}/previous - nothing to roll back to."
    exit 1
  fi

  log_info "Manual rollback requested"
  if [[ "$DRY_RUN" == "true" ]]; then
    log_info "--- dry run: would redeploy ${previous} ($(meta_get "$previous/deploy.meta" image)) ---"
    exit 0
  fi

  group_start "Redeploying ${previous}"
  redeploy_release "$previous"
  group_end

  local rc=0
  wait_healthy || rc=$?
  if [[ "$rc" -ne 0 ]]; then
    log_error "Rollback target did not become healthy."
    dump_logs
    exit 1
  fi

  mark_current "$RELEASE_DIRECTORY"
  prune_releases
  log_info "Rollback to ${ACTIVE_IMAGE_REF} succeeded and is healthy."
}

main() {
  parse_arguments "$@"
  validate_configuration
  check_docker

  mkdir -p "$RELEASES_DIR"
  trap on_exit EXIT
  acquire_lock

  if [[ "$ROLLBACK_ONLY" == "true" ]]; then
    run_rollback_only
    return 0
  fi

  [[ "$DRY_RUN" == "true" ]] || registry_login

  if [[ "$DRY_RUN" == "true" ]]; then
    resolve_previous_release
    print_plan
    return 0
  fi

  resolve_new_image
  resolve_previous_release

  group_start "Deploying ${NEW_REF}"
  deploy "$NEW_REF" "$NEW_SHA"
  group_end

  local rc=0
  wait_healthy || rc=$?

  case "$rc" in
    0)
      mark_current "$RELEASE_DIRECTORY"
      prune_releases
      prune_images
      log_info "Deployment of ${NEW_REF} succeeded and is healthy."
      return 0
      ;;
    2)
      log_error "Aborting without rollback. Define a HEALTHCHECK for the tracked services, or set REQUIRE_HEALTHCHECK=false."
      log_error "Failed release kept at: ${RELEASE_DIRECTORY}"
      exit 1
      ;;
  esac

  log_error "New version failed its health check. Failed release kept at: ${RELEASE_DIRECTORY}"
  dump_logs

  if [[ -z "$PREV_REF" ]]; then
    log_error "No previous version to roll back to. Leaving the failed stack up for inspection."
    exit 1
  fi

  rollback || exit 1
}

main "$@"