#!/bin/bash
# Deployment of the staging stack on the VM (ADR-010, Phase 7 plan PR-68). Run as root by SSM Run Command,
# sent by .github/workflows/deploy-staging.yml together with the release files; never by hand except `status`.
#
#   vm-deploy.sh deploy <commit> <backend image> <keycloak image>
#       <root>/releases/<commit>/ holds compose.yml and Caddyfile (written by the workflow).
#       Renders the env files from SSM Parameter Store, pulls, starts and waits for the health checks.
#       A failure before the start changes nothing; a failed start brings the current release back.
#   vm-deploy.sh revert     brings the previous release back (smoke checks failed after a deployment)
#   vm-deploy.sh status     current and previous releases, state of the containers
#
# Never prints a setting's value: the output of the command is shown in the GitHub Actions log.
set -euo pipefail

ROOT=${MBIA_DEPLOY_ROOT:-/opt/mbia/staging}
ENV_DIR=${MBIA_ENV_DIR:-/etc/mbia/staging}
SSM_PREFIX=${MBIA_SSM_PREFIX:-/mbia/staging}
export AWS_REGION=${MBIA_AWS_REGION:-eu-west-3}
WAIT_TIMEOUT=${MBIA_WAIT_TIMEOUT:-300}

log() { printf '[vm-deploy] %s\n' "$*"; }
fail() { log "ERROR: $*"; exit 1; }

compose() {
  local release=$1
  shift
  docker compose --project-directory "$release" --env-file "$release/compose.env" -f "$release/compose.yml" "$@"
}

# Release folder a link points to, or nothing.
target() { if [ -L "$ROOT/$1" ]; then readlink -f "$ROOT/$1"; fi; }

# Writes <ENV_DIR>/<service>.env (root, 0600) from the parameters <SSM_PREFIX>/<service>/<NAME>.
# Values are single-quoted: Compose reads them literally (a "$" in a password is not interpolated).
render_env() {
  local service=$1 tmp
  tmp=$(mktemp "$ENV_DIR/.$service.env.XXXXXX")
  if ! aws ssm get-parameters-by-path --path "$SSM_PREFIX/$service" --recursive --with-decryption \
    --query 'Parameters[].[Name,Value]' --output json |
    python3 -I -c '
import json, re, sys
prefix, service, out = sys.argv[1], sys.argv[2], sys.argv[3]
rows = json.load(sys.stdin)
if not rows:
    sys.exit(f"no parameter under {prefix}/{service}")
lines = []
for name, value in sorted(rows):
    key = name.rsplit("/", 1)[-1]
    if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
        sys.exit(f"{name}: not an environment variable name")
    if value == "change-me":
        sys.exit(f"{name}: still the placeholder change-me (runbook: SSM parameters)")
    if "\n" in value or "\x27" in value:
        sys.exit(f"{name}: a value cannot hold a line break nor a single quote")
    lines.append(f"{key}=\x27{value}\x27\n")
with open(out, "w") as f:
    f.writelines(lines)
print(f"{service}.env: {len(lines)} settings")
' "$SSM_PREFIX" "$service" "$tmp"; then
    rm -f "$tmp"
    fail "cannot render $service.env"
  fi
  chmod 0600 "$tmp"
  mv -f "$tmp" "$ENV_DIR/$service.env"
}

ecr_login() {
  local registry=${1%%/*}
  aws ecr get-login-password | docker login --username AWS --password-stdin "$registry" >/dev/null
}

# Starts a release and waits until every container is healthy.
start() {
  compose "$1" up -d --wait --wait-timeout "$WAIT_TIMEOUT" --remove-orphans
}

# After a failed start: the state of the containers, never their logs (CloudWatch, runbook).
report() {
  compose "$1" ps --all --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}' || true
  log "Logs: aws logs tail /mbia/staging --log-stream-names backend keycloak caddy --since 15m"
}

link() { ln -sfn "$2" "$ROOT/$1.tmp" && mv -Tf "$ROOT/$1.tmp" "$ROOT/$1"; }

deploy() {
  local commit=$1 backend_image=$2 keycloak_image=$3
  local release="$ROOT/releases/$commit" current
  [[ $commit =~ ^[0-9a-f]{40}$ ]] || fail "not a commit: $commit"
  [ -f "$release/compose.yml" ] && [ -f "$release/Caddyfile" ] || fail "release files missing in $release"
  docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin missing (runbook: first installation)"
  current=$(target current)

  printf 'MBIA_BACKEND_IMAGE=%s\nMBIA_KEYCLOAK_IMAGE=%s\nMBIA_ENV_DIR=%s\nMBIA_AWS_REGION=%s\n' \
    "$backend_image" "$keycloak_image" "$ENV_DIR" "$AWS_REGION" >"$release/compose.env"

  # Nothing running changes until the start: a failure here leaves the current release as it is.
  install -d -m 0700 -o root -g root "$ENV_DIR"
  render_env backend
  render_env keycloak
  ecr_login "$backend_image" || fail "cannot log in to ECR"
  compose "$release" pull --quiet || fail "cannot pull the images of $commit"
  log "Images pulled: $commit"

  if ! start "$release"; then
    log "Release $commit did not become healthy"
    report "$release"
    if [ -n "$current" ] && [ "$current" != "$release" ]; then
      log "Bringing back the current release $(basename "$current")"
      start "$current" || { report "$current"; fail "the current release did not come back either"; }
    fi
    fail "deployment of $commit failed"
  fi

  if [ -n "$current" ] && [ "$current" != "$release" ]; then link previous "$current"; fi
  link current "$release"
  printf '%s deploy %s\n' "$(date -u +%FT%TZ)" "$commit" >>"$ROOT/releases.log"
  log "Release $commit running"
  log "previous: $(basename "$(target previous)")"
}

revert() {
  local previous failed
  previous=$(target previous)
  failed=$(target current)
  [ -n "$previous" ] || fail "no previous release to bring back"
  log "Bringing back $(basename "$previous") in place of $(basename "$failed")"
  start "$previous" || { report "$previous"; fail "the previous release did not come back"; }
  link current "$previous"
  rm -f "$ROOT/previous"
  printf '%s revert %s\n' "$(date -u +%FT%TZ)" "$(basename "$previous")" >>"$ROOT/releases.log"
  log "Release $(basename "$previous") running"
}

status() {
  local current
  current=$(target current)
  log "current:  $(basename "${current:-none}")"
  log "previous: $(basename "$(target previous)")"
  if [ -n "$current" ]; then compose "$current" ps --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}'; fi
}

case "${1:-}" in
  deploy) [ $# -eq 4 ] || fail "usage: $0 deploy <commit> <backend image> <keycloak image>"; deploy "$2" "$3" "$4" ;;
  revert) revert ;;
  status) status ;;
  *) fail "usage: $0 deploy <commit> <backend image> <keycloak image> | revert | status" ;;
esac
