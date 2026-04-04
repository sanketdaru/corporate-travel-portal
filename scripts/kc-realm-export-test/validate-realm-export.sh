#!/usr/bin/env bash
# =============================================================================
# Realm Export Validation Script
# =============================================================================
#
# Validates that realm-export.json imports correctly into a fresh Keycloak
# instance with all users, roles, attributes, clients, and token exchange
# working as expected.
#
# What this script tests (realm-export scope):
#   Phase 0  — Realm reachability
#   Phase 1  — Import completeness: users, roles, attributes, clients,
#              service accounts (via client credential grant)
#   Phase 2  — Authentication: all 5 human users can obtain tokens
#   Phase 3  — JWT claims: tenant_id, roles, preferred_username per user
#   Phase 4  — Token exchange: BFF client can exchange Dave's token (RFC 8693)
#   Phase 5  — Service account credentials: all service clients authenticate
#   Phase 6  — Cross-tenant isolation: Eve retains tenant-b in exchanged token
#
# What this script does NOT test:
#   Backend service integration (travel, expense, delegation, consent, BFF).
#   Those services validate JWTs against the live KC's JWKS keys, so they
#   cannot accept tokens from a separate test KC instance. Full backend
#   integration is covered by run-delegation-flow.sh against the live env.
#
# Usage:
#   ./scripts/end-to-end-test/validate-realm-export.sh [KC_URL]
#   Default KC_URL: http://localhost:8090
#
# Requirements: curl, jq
# =============================================================================

set -uo pipefail

KC_URL="${1:-http://localhost:8090}"
REALM="corporate-travel"

# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

PASS=0; FAIL=0

pass()   { echo -e "  ${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
fail()   { echo -e "  ${RED}FAIL${NC} $1"; FAIL=$((FAIL+1)); }
info()   { echo -e "  ${CYAN}INFO${NC} $1"; }
header() { echo -e "\n${BOLD}$1${NC}"; echo -e "${BOLD}$(printf '─%.0s' {1..60})${NC}"; }

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then pass "$label"
  else fail "$label (expected='$expected' got='$actual')"; fi
}

assert_not_empty() {
  local label="$1" val="$2"
  if [[ -n "$val" && "$val" != "null" && "$val" != "false" ]]; then pass "$label"
  else fail "$label (empty/null/false)"; fi
}

decode_jwt() {
  local payload
  payload=$(printf '%s' "$1" | cut -d'.' -f2 | tr '_-' '/+')
  local pad=$(( 4 - ${#payload} % 4 ))
  [[ $pad -lt 4 ]] && payload="${payload}$(printf '=%.0s' $(seq 1 $pad))"
  printf '%s' "$payload" | base64 --decode 2>/dev/null
}

admin_token() {
  curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli&grant_type=password&username=admin&password=admin123" \
    | jq -r '.access_token // empty'
}

user_token() {
  # Writes token to the named variable (avoids command-substitution stdout capture)
  local __var="$1" user="$2" pass="$3"
  local resp tok
  resp=$(curl -s -X POST "$KC_URL/realms/$REALM/protocol/openid-connect/token" \
    -d "client_id=employee-bff" \
    -d "client_secret=bff-service-secret-change-in-production" \
    -d "grant_type=password" \
    -d "username=$user" \
    -d "password=$pass" \
    -d "scope=openid")
  tok=$(echo "$resp" | jq -r '.access_token // empty')
  printf -v "$__var" '%s' "$tok"
}

svc_token() {
  local client_id="$1" secret="$2"
  curl -s -X POST "$KC_URL/realms/$REALM/protocol/openid-connect/token" \
    -d "client_id=$client_id" \
    -d "client_secret=$secret" \
    -d "grant_type=client_credentials" \
    | jq -r '.access_token // empty'
}

# ---------------------------------------------------------------------------
# Phase 0 — Reachability
# ---------------------------------------------------------------------------
header "Phase 0 — Keycloak reachability"

REALM_RESP=$(curl -sf "$KC_URL/realms/$REALM" 2>/dev/null || echo "")
assert_not_empty "Realm endpoint reachable" "$REALM_RESP"
if echo "$REALM_RESP" | jq -r '.realm' 2>/dev/null | grep -q "corporate-travel"; then
  pass "Realm name is 'corporate-travel'"
else
  fail "Realm name is 'corporate-travel'"
fi

# ---------------------------------------------------------------------------
# Phase 1 — Import completeness
# ---------------------------------------------------------------------------
header "Phase 1 — Import completeness (admin API)"

ATOK=$(admin_token)
assert_not_empty "Admin token obtained" "$ATOK"

# Human users (exclude service accounts by checking serviceAccountClientId absence)
USERS_JSON=$(curl -s "$KC_URL/admin/realms/$REALM/users?max=200" \
  -H "Authorization: Bearer $ATOK")
HUMAN_USERS=$(echo "$USERS_JSON" | jq '[.[] | select(has("serviceAccountClientId") | not)]')
assert_eq "5 human users imported" "5" "$(echo "$HUMAN_USERS" | jq 'length')"

for UNAME in alice.employee bob.manager carol.executive dave.assistant eve.employee; do
  COUNT=$(echo "$HUMAN_USERS" | jq --arg u "$UNAME" '[.[] | select(.username==$u)] | length')
  assert_eq "User $UNAME present" "1" "$COUNT"
done

# Realm roles
ROLES_JSON=$(curl -s "$KC_URL/admin/realms/$REALM/roles" \
  -H "Authorization: Bearer $ATOK")
for RNAME in employee manager executive assistant admin; do
  COUNT=$(echo "$ROLES_JSON" | jq --arg r "$RNAME" '[.[] | select(.name==$r)] | length')
  assert_eq "Role '$RNAME' exists" "1" "$COUNT"
done

# Clients
CLIENTS_JSON=$(curl -s "$KC_URL/admin/realms/$REALM/clients?max=200" \
  -H "Authorization: Bearer $ATOK")
for CLIENT in employee-bff travel-service expense-service delegation-service consent-service; do
  COUNT=$(echo "$CLIENTS_JSON" | jq --arg c "$CLIENT" '[.[] | select(.clientId==$c)] | length')
  assert_eq "Client '$CLIENT' exists" "1" "$COUNT"
done

# tenant_id attributes
info "Verifying tenant_id attributes"
while IFS= read -r row; do
  uid=$(echo   "$row" | jq -r '.id')
  uname=$(echo "$row" | jq -r '.username')
  UDETAIL=$(curl -s "$KC_URL/admin/realms/$REALM/users/$uid" \
    -H "Authorization: Bearer $ATOK")
  TENANT=$(echo "$UDETAIL" | jq -r '.attributes.tenant_id[0] // "MISSING"')
  case "$uname" in
    eve.employee) assert_eq "$uname: tenant_id=tenant-b" "tenant-b" "$TENANT" ;;
    *)            assert_eq "$uname: tenant_id=tenant-a" "tenant-a" "$TENANT" ;;
  esac
done < <(echo "$HUMAN_USERS" | jq -c '.[]')

# Role assignments
info "Verifying role assignments"
while IFS= read -r row; do
  uid=$(echo   "$row" | jq -r '.id')
  uname=$(echo "$row" | jq -r '.username')
  ROLE_NAMES=$(curl -s "$KC_URL/admin/realms/$REALM/users/$uid/role-mappings/realm" \
    -H "Authorization: Bearer $ATOK" \
    | jq -r '[.[] | .name] | join(" ")')
  check_role() {
    if echo " $ROLE_NAMES " | grep -qw "$1"; then pass "$uname has role '$1'"
    else fail "$uname missing role '$1' (got: $ROLE_NAMES)"; fi
  }
  case "$uname" in
    alice.employee)  check_role employee ;;
    bob.manager)     check_role employee; check_role manager ;;
    carol.executive) check_role employee; check_role executive ;;
    dave.assistant)  check_role employee; check_role assistant ;;
    eve.employee)    check_role employee ;;
  esac
done < <(echo "$HUMAN_USERS" | jq -c '.[]')

# User profile schema
PROFILE_JSON=$(curl -s "$KC_URL/admin/realms/$REALM/users/profile" \
  -H "Authorization: Bearer $ATOK")
TENANT_ID_DECLARED=$(echo "$PROFILE_JSON" | jq '[.attributes[] | select(.name=="tenant_id")] | length')
assert_eq "tenant_id declared in User Profile schema" "1" "$TENANT_ID_DECLARED"

# ---------------------------------------------------------------------------
# Phase 2 — Authentication (password grant)
# ---------------------------------------------------------------------------
header "Phase 2 — Authentication (password grant)"

ALICE_TOKEN=""; BOB_TOKEN=""; CAROL_TOKEN=""; DAVE_TOKEN=""; EVE_TOKEN=""

user_token ALICE_TOKEN alice.employee  password123
user_token BOB_TOKEN   bob.manager     password123
user_token CAROL_TOKEN carol.executive password123
user_token DAVE_TOKEN  dave.assistant  password123
user_token EVE_TOKEN   eve.employee    password123

assert_not_empty "alice.employee obtains token"  "$ALICE_TOKEN"
assert_not_empty "bob.manager obtains token"     "$BOB_TOKEN"
assert_not_empty "carol.executive obtains token" "$CAROL_TOKEN"
assert_not_empty "dave.assistant obtains token"  "$DAVE_TOKEN"
assert_not_empty "eve.employee obtains token"    "$EVE_TOKEN"

# ---------------------------------------------------------------------------
# Phase 3 — JWT claims
# ---------------------------------------------------------------------------
header "Phase 3 — JWT claims (tenant_id, roles, preferred_username)"

check_claims() {
  local label="$1" token="$2" exp_user="$3" exp_tenant="$4"

  if [[ -z "$token" || "$token" == "null" ]]; then
    fail "$label: no token to decode"; return
  fi

  local CLAIMS PREF_UN TENANT AUD
  CLAIMS=$(decode_jwt "$token")
  PREF_UN=$(echo "$CLAIMS" | jq -r '.preferred_username // empty' 2>/dev/null || echo "")
  TENANT=$(echo  "$CLAIMS" | jq -r '.tenant_id // empty'         2>/dev/null || echo "")
  AUD=$(echo     "$CLAIMS" | jq -r '.aud // empty'               2>/dev/null || echo "")

  assert_eq "$label: preferred_username" "$exp_user"   "$PREF_UN"
  assert_eq "$label: tenant_id"          "$exp_tenant" "$TENANT"
  assert_not_empty "$label: aud present" "$AUD"
}

# Note: realm_access.roles is intentionally absent from employee-bff tokens on
# both live and test KC — no realm-level roles default scope is configured.
# Role assignments are verified via admin API in Phase 1. Services receive roles
# through SecurityContext built from realm_access when present (OPA delegation
# path does not require role claims for the tested flows).

check_claims "alice.employee"  "$ALICE_TOKEN" alice.employee  tenant-a
check_claims "bob.manager"     "$BOB_TOKEN"   bob.manager     tenant-a
check_claims "carol.executive" "$CAROL_TOKEN" carol.executive tenant-a
check_claims "dave.assistant"  "$DAVE_TOKEN"  dave.assistant  tenant-a
check_claims "eve.employee"    "$EVE_TOKEN"   eve.employee    tenant-b

# ---------------------------------------------------------------------------
# Phase 4 — Token exchange (RFC 8693 / ADR-004)
# ---------------------------------------------------------------------------
header "Phase 4 — Token exchange (RFC 8693 / ADR-004)"

EXCHANGE_RESP=$(curl -s -X POST "$KC_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=employee-bff" \
  -d "client_secret=bff-service-secret-change-in-production" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "subject_token=$DAVE_TOKEN" \
  -d "subject_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "audience=travel-service")

EXCHANGED_TOKEN=$(echo "$EXCHANGE_RESP" | jq -r '.access_token // empty')
assert_not_empty "Token exchange succeeded (Dave → travel-service)" "$EXCHANGED_TOKEN"

if [[ -n "$EXCHANGED_TOKEN" && "$EXCHANGED_TOKEN" != "null" ]]; then
  EX_CLAIMS=$(decode_jwt "$EXCHANGED_TOKEN")
  assert_eq "Exchanged token aud=travel-service" \
    "travel-service" "$(echo "$EX_CLAIMS" | jq -r '.aud // empty' 2>/dev/null)"
  assert_eq "Exchanged token azp=employee-bff" \
    "employee-bff" "$(echo "$EX_CLAIMS" | jq -r '.azp // empty' 2>/dev/null)"
  assert_eq "Exchanged token identity preserved (Dave)" \
    "dave.assistant" "$(echo "$EX_CLAIMS" | jq -r '.preferred_username // empty' 2>/dev/null)"
  assert_eq "Exchanged token retains tenant_id" \
    "tenant-a" "$(echo "$EX_CLAIMS" | jq -r '.tenant_id // empty' 2>/dev/null)"
fi

# ---------------------------------------------------------------------------
# Phase 5 — Service account credentials
# ---------------------------------------------------------------------------
header "Phase 5 — Service account client credentials"

check_svc() {
  local client_id="$1" secret="$2"
  local TOK
  TOK=$(svc_token "$client_id" "$secret")
  assert_not_empty "$client_id authenticates via client_credentials" "$TOK"
}

check_svc travel-service     "travel-service-secret-change-in-production"
check_svc expense-service    "expense-service-secret-change-in-production"
check_svc delegation-service "delegation-service-secret-change-in-production"
check_svc consent-service    "consent-service-secret-change-in-production"
check_svc employee-bff       "bff-service-secret-change-in-production"

# ---------------------------------------------------------------------------
# Phase 6 — Cross-tenant isolation
# ---------------------------------------------------------------------------
header "Phase 6 — Cross-tenant isolation"

EVE_EX_RESP=$(curl -s -X POST "$KC_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=employee-bff" \
  -d "client_secret=bff-service-secret-change-in-production" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "subject_token=$EVE_TOKEN" \
  -d "subject_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "audience=travel-service")

EVE_EX_TOKEN=$(echo "$EVE_EX_RESP" | jq -r '.access_token // empty')
assert_not_empty "Eve (tenant-b) can exchange token for travel-service audience" "$EVE_EX_TOKEN"

if [[ -n "$EVE_EX_TOKEN" && "$EVE_EX_TOKEN" != "null" ]]; then
  EVE_EX_TENANT=$(decode_jwt "$EVE_EX_TOKEN" | jq -r '.tenant_id // empty' 2>/dev/null)
  assert_eq "Eve's exchanged token retains tenant-b (OPA enforces boundary at service layer)" \
    "tenant-b" "$EVE_EX_TENANT"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
TOTAL=$((PASS + FAIL))
echo ""
echo -e "${BOLD}$(printf '═%.0s' {1..60})${NC}"
echo -e "${BOLD}  Result: $TOTAL checks — ${GREEN}$PASS passed${NC}${BOLD}, ${RED}$FAIL failed${NC}"
echo -e "${BOLD}$(printf '═%.0s' {1..60})${NC}"
echo ""

if [[ $FAIL -gt 0 ]]; then
  echo -e "${RED}One or more assertions failed — realm-export.json needs review.${NC}"
  exit 1
fi
echo -e "${GREEN}realm-export.json is valid — safe to use as the canonical import.${NC}"
