#!/usr/bin/env bash
# =============================================================================
# Realm-as-code Validation
# =============================================================================
#
# Proves that infrastructure/keycloak/config/*.json:
#   1. applies cleanly to a brand-new Keycloak with keycloak-config-cli,
#   2. is idempotent: a second forced apply leaves realm and users unchanged,
#   3. yields the expected users, claims and token exchange behaviour
#      (validate-realm-claims.sh),
#   4. supports the browser path: an employee-portal PKCE token can be
#      exchanged by employee-bff (Standard Token Exchange V2).
#
# Starts a throwaway Keycloak container on KC_PORT (default 8090) and removes
# it on exit. Does not touch the compose stack.
#
# Usage:   ./scripts/keycloak-realm-test/validate-realm-config.sh
# Env:     KC_PORT (8090), KC_IMAGE, KCC_IMAGE — default to the compose versions
# Requires: podman (or docker), curl, jq, openssl
# =============================================================================

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$ROOT/scripts/keycloak-realm-test"
CONFIG_DIR="$ROOT/infrastructure/keycloak/config"
KC_PORT="${KC_PORT:-8090}"
KC_URL="http://localhost:$KC_PORT"
KC_IMAGE="${KC_IMAGE:-$(sed -n 's#^ *image: \(quay.io/keycloak/keycloak:.*\)#\1#p' "$ROOT/docker-compose.yml")}"
KCC_IMAGE="${KCC_IMAGE:-$(sed -n 's#^ *image: \(.*keycloak-config-cli:.*\)#\1#p' "$ROOT/docker-compose.yml")}"
REALM="corporate-travel"
BFF_SECRET="bff-service-secret-change-in-production"
CONTAINER="ctp-realm-validate-$$"

if command -v podman >/dev/null 2>&1; then CTR=podman; HOST_ALIAS=host.containers.internal; ADD_HOST=()
# Linux Docker only resolves host.docker.internal when mapped explicitly
else CTR=docker; HOST_ALIAS=host.docker.internal; ADD_HOST=(--add-host=host.docker.internal:host-gateway); fi

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
PASS=0; FAIL=0
pass()   { echo -e "  ${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
fail()   { echo -e "  ${RED}FAIL${NC} $1"; FAIL=$((FAIL+1)); }
info()   { echo -e "  ${CYAN}INFO${NC} $1"; }
header() { echo -e "\n${BOLD}$1${NC}"; echo -e "${BOLD}$(printf '─%.0s' {1..60})${NC}"; }

WORK=$(mktemp -d)
cleanup() { $CTR rm -f "$CONTAINER" >/dev/null 2>&1; rm -rf "$WORK"; }
trap cleanup EXIT

admin_token() {
  curl -s -d client_id=admin-cli -d username=admin -d password=admin123 -d grant_type=password \
    "$KC_URL/realms/master/protocol/openid-connect/token" | jq -r .access_token
}

# Realm + users with generated/volatile fields removed, for diffing between applies
snapshot() {
  local out="$1" t; t=$(admin_token)
  curl -s -X POST -H "Authorization: Bearer $t" \
    "$KC_URL/admin/realms/$REALM/partial-export?exportClients=true&exportGroupsAndRoles=true" \
    | jq -S '.attributes |= with_entries(select(.key | startswith("de.adorsys") | not)) | del(.. | .id?) | del(.. | .containerId?)' > "$out.realm.json"
  curl -s -H "Authorization: Bearer $t" "$KC_URL/admin/realms/$REALM/users?max=100&briefRepresentation=false" \
    | jq -S 'map(del(.id, .createdTimestamp))' > "$out.users.json"
}

apply_config() {
  # ${arr[@]+...} form: empty arrays are "unbound" under set -u in bash 3.2 (macOS)
  $CTR run --rm ${ADD_HOST[@]+"${ADD_HOST[@]}"} \
    -e KEYCLOAK_URL="http://$HOST_ALIAS:$KC_PORT" -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin123 \
    -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true -e KEYCLOAK_AVAILABILITYCHECK_TIMEOUT=120s \
    -e IMPORT_FILES_LOCATIONS='/config/*.json' -e IMPORT_VARSUBSTITUTION_ENABLED=true \
    -e IMPORT_CACHE_ENABLED=false \
    -e KC_FRONTEND_URL="$KC_URL" -e KC_DEMO_USER_PASSWORD=password123 \
    -e KC_CLIENT_SECRET_APPROVAL_SERVICE=approval-service-secret-change-in-production \
    -e KC_CLIENT_SECRET_CONSENT_SERVICE=consent-service-secret-change-in-production \
    -e KC_CLIENT_SECRET_DELEGATION_SERVICE=delegation-service-secret-change-in-production \
    -e KC_CLIENT_SECRET_EMPLOYEE_BFF="$BFF_SECRET" \
    -e KC_CLIENT_SECRET_EXPENSE_SERVICE=expense-service-secret-change-in-production \
    -e KC_CLIENT_SECRET_TRAVEL_SERVICE=travel-service-secret-change-in-production \
    -v "$CONFIG_DIR:/config:ro" "$KCC_IMAGE" > "$WORK/apply-$1.log" 2>&1
}

decode_jwt() {
  cut -d. -f2 | tr '_-' '/+' | awk '{l=length($0)%4; if(l==2)$0=$0"=="; else if(l==3)$0=$0"="; print}' \
    | { base64 -d 2>/dev/null || base64 -D; }
}

# -----------------------------------------------------------------------------
header "Phase A — Start throwaway Keycloak ($KC_IMAGE on :$KC_PORT)"
$CTR run -d --name "$CONTAINER" -p "$KC_PORT:8080" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin123 \
  "$KC_IMAGE" start-dev >/dev/null || { fail "Could not start Keycloak"; exit 1; }
for _ in $(seq 1 90); do curl -sf -o /dev/null "$KC_URL/realms/master" && break; sleep 2; done
curl -sf -o /dev/null "$KC_URL/realms/master" && pass "Keycloak is up" || { fail "Keycloak did not start"; exit 1; }

# -----------------------------------------------------------------------------
header "Phase B — Apply realm config and check idempotency ($KCC_IMAGE)"
if apply_config 1; then pass "First apply succeeded (realm created)"; else fail "First apply failed — see below"; tail -20 "$WORK/apply-1.log"; exit 1; fi
snapshot "$WORK/s1"
if apply_config 2; then pass "Second forced apply succeeded"; else fail "Second apply failed"; tail -20 "$WORK/apply-2.log"; exit 1; fi
snapshot "$WORK/s2"
if diff -q "$WORK/s1.realm.json" "$WORK/s2.realm.json" >/dev/null && diff -q "$WORK/s1.users.json" "$WORK/s2.users.json" >/dev/null; then
  pass "Idempotent: realm and users unchanged by second apply"
else
  fail "Not idempotent — second apply changed state:"
  diff "$WORK/s1.realm.json" "$WORK/s2.realm.json" | head -20
  diff "$WORK/s1.users.json" "$WORK/s2.users.json" | head -10
fi

# -----------------------------------------------------------------------------
header "Phase C — Claims and token exchange (validate-realm-claims.sh)"
if "$HERE/validate-realm-claims.sh" "$KC_URL" > "$WORK/claims.log" 2>&1; then
  pass "$(sed 's/\x1b\[[0-9;]*m//g' "$WORK/claims.log" | grep -o '[0-9]* checks — [0-9]* passed.*' | tail -1)"
else
  fail "validate-realm-claims.sh reported failures:"
  sed 's/\x1b\[[0-9;]*m//g' "$WORK/claims.log" | grep -E 'FAIL' | head -20
fi

# -----------------------------------------------------------------------------
header "Phase D — Browser path: employee-portal PKCE token exchanged by employee-bff"
J="$WORK/cookies"
VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
REDIRECT=http://localhost:3000/api/auth/callback/keycloak
AUTH="$KC_URL/realms/$REALM/protocol/openid-connect/auth?client_id=employee-portal&response_type=code&scope=openid&redirect_uri=$REDIRECT&code_challenge=$CHALLENGE&code_challenge_method=S256&state=x"
ACTION=$(curl -s -c "$J" -b "$J" "$AUTH" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')
LOC=$(curl -s -c "$J" -b "$J" -o /dev/null -w '%{redirect_url}' -d username=dave.assistant -d password=password123 "$ACTION")
CODE=$(printf '%s' "$LOC" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
PORTAL=$(curl -s -d grant_type=authorization_code -d client_id=employee-portal -d code="$CODE" \
  -d redirect_uri="$REDIRECT" -d code_verifier="$VERIFIER" "$KC_URL/realms/$REALM/protocol/openid-connect/token" | jq -r '.access_token // empty')
if [[ -n "$PORTAL" ]]; then
  pass "PKCE login as dave.assistant via employee-portal"
  AUD_OK=$(printf '%s' "$PORTAL" | decode_jwt | jq -r '[.aud] | flatten | index("employee-bff") != null')
  [[ "$AUD_OK" == "true" ]] && pass "Portal token audience contains employee-bff" || fail "Portal token audience lacks employee-bff"
  EX=$(curl -s -d grant_type=urn:ietf:params:oauth:grant-type:token-exchange -d client_id=employee-bff -d client_secret="$BFF_SECRET" \
    -d subject_token="$PORTAL" -d subject_token_type=urn:ietf:params:oauth:token-type:access_token \
    -d requested_token_type=urn:ietf:params:oauth:token-type:access_token -d audience=travel-service \
    "$KC_URL/realms/$REALM/protocol/openid-connect/token" | jq -r '.access_token // empty')
  if [[ -n "$EX" ]]; then
    CLAIMS=$(printf '%s' "$EX" | decode_jwt | jq -c '{aud, azp, preferred_username}')
    [[ "$CLAIMS" == '{"aud":"travel-service","azp":"employee-bff","preferred_username":"dave.assistant"}' ]] \
      && pass "Exchanged token: $CLAIMS" || fail "Unexpected exchanged token claims: $CLAIMS"
  else
    fail "employee-bff could not exchange the portal token"
  fi
else
  fail "PKCE login failed (redirect: $LOC)"
fi

# -----------------------------------------------------------------------------
echo -e "\n${BOLD}$(printf '═%.0s' {1..60})${NC}"
echo -e "${BOLD}  Result: $((PASS+FAIL)) checks — ${GREEN}${PASS} passed${NC}${BOLD}, ${RED}${FAIL} failed${NC}"
echo -e "${BOLD}$(printf '═%.0s' {1..60})${NC}"
[[ $FAIL -eq 0 ]]
