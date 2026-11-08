#!/bin/bash
# Checks after a staging deployment (Phase 7 plan PR-68), from outside: DNS, TLS, Caddy, CloudFront.
#   smoke-checks.sh backend                      the API answers through Caddy, the OIDC discovery document
#   smoke-checks.sh frontend <built index.html>  the frontend serves the index.html just built, deep links too
# Hosts: MBIA_FRONTEND_HOST, MBIA_API_HOST, MBIA_AUTH_HOST (staging by default).
set -euo pipefail

FRONTEND=https://${MBIA_FRONTEND_HOST:-mbia-staging.lehnade.com}
API=https://${MBIA_API_HOST:-api.mbia-staging.lehnade.com}
AUTH=https://${MBIA_AUTH_HOST:-auth.mbia-staging.lehnade.com}
ATTEMPTS=${MBIA_SMOKE_ATTEMPTS:-12}
body=$(mktemp)
trap 'rm -f "$body"' EXIT

# check <name> <url> <test function>: retries until the test passes. Tests read the status in $code and the
# response body in the file $body.
check() {
  local name=$1 url=$2 test=$3 attempt
  for attempt in $(seq "$ATTEMPTS"); do
    code=$(curl -sS -o "$body" -w '%{http_code}' --max-time 10 "$url" 2>/dev/null || echo 000)
    if "$test"; then
      echo "ok   $name ($code)"
      return 0
    fi
    echo "wait $name: HTTP $code, attempt $attempt/$ATTEMPTS"
    sleep 10
  done
  echo "FAIL $name: $url"
  return 1
}

not_found() { [ "$code" = 404 ]; }
# Caddy forwards /api/v1 to the backend: an anonymous call gets the backend's 401 problem. Caddy itself would
# answer 502 or 503 (backend down, or failing the health check Caddy runs on /actuator/health).
backend_problem() { [ "$code" = 401 ] && grep -q '"AUTHENTICATION_REQUIRED"' "$body"; }
issuer_matches() {
  [ "$code" = 200 ] &&
    python3 -I -c 'import json, sys; sys.exit(json.load(open(sys.argv[1]))["issuer"] != sys.argv[2])' \
      "$body" "$AUTH/realms/mbia"
}
built_index() { [ "$code" = 200 ] && cmp -s "$body" "$index"; }
robots_disallow() { [ "$code" = 200 ] && grep -q '^Disallow: /' "$body"; }

case "${1:-}" in
  backend)
    check "API through Caddy" "$API/api/v1/me" backend_problem
    check "Actuator not exposed" "$API/actuator/health" not_found
    check "OIDC discovery document" "$AUTH/realms/mbia/.well-known/openid-configuration" issuer_matches
    check "Keycloak admin console not exposed" "$AUTH/admin/" not_found
    ;;
  frontend)
    index=${2:?usage: smoke-checks.sh frontend <built index.html>}
    check "frontend serves the new index.html" "$FRONTEND/" built_index
    check "deep link" "$FRONTEND/invitations/smoke-check" built_index
    check "robots.txt" "$FRONTEND/robots.txt" robots_disallow
    ;;
  *)
    echo "usage: $0 backend | frontend <built index.html>" >&2
    exit 2
    ;;
esac
