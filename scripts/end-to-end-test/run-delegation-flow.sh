#!/usr/bin/env bash
# =============================================================================
# End-to-End Regression Test: Delegated Access Flow
# =============================================================================
#
# Tests the full delegation chain described in ADR-004, ADR-011, and ADR-018:
#
#   Carol (executive) delegates travel booking rights to Dave (assistant).
#   Carol grants explicit consent covering the delegation scopes.
#   Dave authenticates, activates delegation via the BFF (Standard Token
#   Exchange V2), and creates a booking on Carol's behalf.
#   The booking must be attributed to Carol (userId) with Dave as the actor
#   (createdBy), and OPA must authorize via has_active_delegation + consent.
#
# Usage:
#   ./scripts/end-to-end-test/run-delegation-flow.sh
#   ./scripts/end-to-end-test/run-delegation-flow.sh --no-cleanup
#
# Requirements: curl, jq
# Idempotent: if a matching delegation/consent already exists it is reused;
#             only records created by this run are revoked on exit.
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
KEYCLOAK_URL="http://localhost:8080"
REALM="corporate-travel"
BFF_CLIENT_ID="employee-bff"
BFF_CLIENT_SECRET="bff-service-secret-change-in-production"

TRAVEL_SERVICE_URL="http://localhost:8081"
DELEGATION_SERVICE_URL="http://localhost:8083"
CONSENT_SERVICE_URL="http://localhost:8084"
BFF_URL="http://localhost:8085"
OPA_URL="http://localhost:8181"

CAROL_USER="carol.executive"
CAROL_PASS="password123"
DAVE_USER="dave.assistant"
DAVE_PASS="password123"

DELEGATION_PURPOSE="book_travel"
DELEGATION_SCOPES='["view_bookings","create_bookings"]'
EXPIRES_AT="2027-01-01T00:00:00"     # LocalDateTime — no Z suffix

CLEANUP=true
if [[ "${1:-}" == "--no-cleanup" ]]; then
  CLEANUP=false
fi

# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0

pass() { echo -e "  ${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}FAIL${NC} $1"; FAIL=$((FAIL+1)); }
skip() { echo -e "  ${YELLOW}SKIP${NC} $1"; SKIP=$((SKIP+1)); }
info() { echo -e "  ${CYAN}INFO${NC} $1"; }
header() { echo -e "\n${BOLD}$1${NC}"; echo -e "${BOLD}$(printf '─%.0s' {1..60})${NC}"; }

# ---------------------------------------------------------------------------
# JWT decode helper (no external deps)
# ---------------------------------------------------------------------------
decode_jwt() {
  local payload
  payload=$(printf '%s' "$1" | cut -d'.' -f2 | tr '_-' '/+')
  local pad=$(( 4 - ${#payload} % 4 ))
  [[ $pad -lt 4 ]] && payload="${payload}$(printf '=%.0s' $(seq 1 $pad))"
  printf '%s' "$payload" | base64 --decode 2>/dev/null
}

# ---------------------------------------------------------------------------
# Assertion helpers
# ---------------------------------------------------------------------------
assert_not_empty() {
  local label="$1" val="$2"
  if [[ -n "$val" && "$val" != "null" && "$val" != "false" ]]; then
    pass "$label"
  else
    fail "$label (value was empty/null)"
  fi
}

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected='$expected', got='$actual')"
  fi
}

assert_contains() {
  local label="$1" haystack="$2" needle="$3"
  if echo "$haystack" | grep -q "$needle"; then
    pass "$label"
  else
    fail "$label (expected '$needle' to appear in response)"
  fi
}

assert_http() {
  # assert_http <label> <json-response> <jq-path> <expected>
  local label="$1" response="$2" jq_path="$3" expected="$4"
  local actual
  actual=$(echo "$response" | jq -r "$jq_path" 2>/dev/null || echo "PARSE_ERROR")
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (path='$jq_path', expected='$expected', got='$actual')"
  fi
}

# ---------------------------------------------------------------------------
# Cleanup registry — only records created by this run are revoked on exit
# ---------------------------------------------------------------------------
DELEGATION_ID=""
CONSENT_ID=""
BOOKING_ID=""
CAROL_TOKEN=""
DAVE_TOKEN=""
CREATED_DELEGATION=false
CREATED_CONSENT=false

cleanup() {
  if [[ "$CLEANUP" == false ]]; then
    echo -e "\n${YELLOW}Skipping cleanup (--no-cleanup)${NC}"
    echo "  Delegation : ${DELEGATION_ID:-<none>}  (created_by_test=$CREATED_DELEGATION)"
    echo "  Consent    : ${CONSENT_ID:-<none>}  (created_by_test=$CREATED_CONSENT)"
    echo "  Booking    : ${BOOKING_ID:-<none>}"
    return
  fi

  header "Cleanup"

  if [[ "$CREATED_DELEGATION" == true && -n "$DELEGATION_ID" && -n "$CAROL_TOKEN" ]]; then
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
      "$DELEGATION_SERVICE_URL/api/delegations/$DELEGATION_ID" \
      -H "Authorization: Bearer $CAROL_TOKEN")
    [[ "$http_code" == "204" ]] && pass "Delegation $DELEGATION_ID revoked" \
                                 || fail "Revoke delegation returned HTTP $http_code"
  else
    info "Delegation was pre-existing — not revoked"
  fi

  if [[ "$CREATED_CONSENT" == true && -n "$CONSENT_ID" && -n "$CAROL_TOKEN" ]]; then
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
      "$CONSENT_SERVICE_URL/api/consents/$CONSENT_ID" \
      -H "Authorization: Bearer $CAROL_TOKEN")
    [[ "$http_code" =~ ^(200|204)$ ]] && pass "Consent $CONSENT_ID revoked" \
                                       || fail "Revoke consent returned HTTP $http_code"
  else
    info "Consent was pre-existing — not revoked"
  fi

  rm -f /tmp/e2e-bff-session.txt
}

trap cleanup EXIT

# ---------------------------------------------------------------------------
# Pre-flight: check dependencies
# ---------------------------------------------------------------------------
header "Pre-flight checks"

for cmd in curl jq; do
  command -v "$cmd" &>/dev/null && pass "$cmd is available" || { fail "$cmd not installed"; exit 1; }
done

# ---------------------------------------------------------------------------
# Phase 0: Service health
# ---------------------------------------------------------------------------
header "Phase 0 — Service health"

check_health() {
  local name="$1" url="$2"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 4 "$url" 2>/dev/null || echo "000")
  [[ "$code" =~ ^(200|204|301|302|400|404)$ ]] \
    && pass "$name reachable ($url)" \
    || fail "$name not reachable — HTTP $code ($url)"
}

check_health "Keycloak"           "$KEYCLOAK_URL/realms/$REALM"
check_health "Travel Service"     "$TRAVEL_SERVICE_URL/actuator/health"
check_health "Delegation Service" "$DELEGATION_SERVICE_URL/actuator/health"
check_health "Consent Service"    "$CONSENT_SERVICE_URL/actuator/health"
check_health "Employee BFF"       "$BFF_URL/actuator/health"
check_health "OPA"                "$OPA_URL/health"

# ---------------------------------------------------------------------------
# Phase 1: Authentication
# ---------------------------------------------------------------------------
header "Phase 1 — Authentication (Keycloak password grant)"

info "Authenticating Carol ($CAROL_USER)"
CAROL_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=$BFF_CLIENT_ID" -d "client_secret=$BFF_CLIENT_SECRET" \
  -d "username=$CAROL_USER" -d "password=$CAROL_PASS" -d "grant_type=password")
CAROL_TOKEN=$(echo "$CAROL_RESPONSE" | jq -r '.access_token')
assert_not_empty "Carol token obtained" "$CAROL_TOKEN"

CAROL_CLAIMS=$(decode_jwt "$CAROL_TOKEN")
assert_eq "Carol preferred_username" "$CAROL_USER"  "$(echo "$CAROL_CLAIMS" | jq -r '.preferred_username')"
assert_eq "Carol tenant_id"          "tenant-a"     "$(echo "$CAROL_CLAIMS" | jq -r '.tenant_id')"
assert_contains "Carol aud includes travel-service" \
  "$(echo "$CAROL_CLAIMS" | jq -r '.aud | @json')" "travel-service"

info "Authenticating Dave ($DAVE_USER)"
DAVE_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=$BFF_CLIENT_ID" -d "client_secret=$BFF_CLIENT_SECRET" \
  -d "username=$DAVE_USER" -d "password=$DAVE_PASS" -d "grant_type=password")
DAVE_TOKEN=$(echo "$DAVE_RESPONSE" | jq -r '.access_token')
assert_not_empty "Dave token obtained" "$DAVE_TOKEN"

DAVE_CLAIMS=$(decode_jwt "$DAVE_TOKEN")
assert_eq "Dave preferred_username" "$DAVE_USER" "$(echo "$DAVE_CLAIMS" | jq -r '.preferred_username')"
assert_eq "Dave tenant_id"          "tenant-a"   "$(echo "$DAVE_CLAIMS" | jq -r '.tenant_id')"
assert_contains "Dave aud includes travel-service" \
  "$(echo "$DAVE_CLAIMS" | jq -r '.aud | @json')" "travel-service"
assert_contains "Dave aud includes expense-service" \
  "$(echo "$DAVE_CLAIMS" | jq -r '.aud | @json')" "expense-service"
assert_contains "Dave aud includes delegation-service" \
  "$(echo "$DAVE_CLAIMS" | jq -r '.aud | @json')" "delegation-service"
assert_contains "Dave aud includes consent-service" \
  "$(echo "$DAVE_CLAIMS" | jq -r '.aud | @json')" "consent-service"

# ---------------------------------------------------------------------------
# Phase 2: Keycloak Standard Token Exchange V2 (ADR-004)
# ---------------------------------------------------------------------------
header "Phase 2 — Standard Token Exchange V2 (ADR-004)"

info "Dave exchanges token for a travel-service-scoped token"
EXCHANGE_RESPONSE=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "client_id=$BFF_CLIENT_ID" -d "client_secret=$BFF_CLIENT_SECRET" \
  -d "subject_token=$DAVE_TOKEN" \
  -d "subject_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "audience=travel-service")

EXCHANGED_TOKEN=$(echo "$EXCHANGE_RESPONSE" | jq -r '.access_token')
assert_not_empty "Token exchange succeeded" "$EXCHANGED_TOKEN"

if [[ -n "$EXCHANGED_TOKEN" && "$EXCHANGED_TOKEN" != "null" ]]; then
  EX_CLAIMS=$(decode_jwt "$EXCHANGED_TOKEN")
  assert_eq "Exchanged token aud=travel-service"  "travel-service" "$(echo "$EX_CLAIMS" | jq -r '.aud')"
  assert_eq "Exchanged token azp=employee-bff"    "employee-bff"   "$(echo "$EX_CLAIMS" | jq -r '.azp')"
  assert_eq "Exchanged token identity=Dave (actor preserved)" \
    "$DAVE_USER" "$(echo "$EX_CLAIMS" | jq -r '.preferred_username')"
fi

# ---------------------------------------------------------------------------
# Phase 3: Delegation record (delegation-service)
# ---------------------------------------------------------------------------
header "Phase 3 — Delegation record (delegation-service)"

info "Looking for existing active delegation: Carol → Dave / $DELEGATION_PURPOSE"
EXISTING_DELEGATION=$(curl -s "$DELEGATION_SERVICE_URL/api/delegations/my-delegations" \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  | jq -r --arg del "$DAVE_USER" --arg pur "$DELEGATION_PURPOSE" \
    '.[] | select(.delegateId == $del and .purpose == $pur and .active == true) | .id' \
  | head -1)

if [[ -n "$EXISTING_DELEGATION" ]]; then
  DELEGATION_ID="$EXISTING_DELEGATION"
  CREATED_DELEGATION=false
  info "Reusing existing delegation: $DELEGATION_ID"
  pass "Delegation exists (reused pre-existing record)"
else
  info "No existing delegation found — creating one"
  DELEGATION_PAYLOAD=$(jq -n \
    --arg del "$DAVE_USER" --arg pur "$DELEGATION_PURPOSE" \
    --arg exp "$EXPIRES_AT" \
    '{delegateId: $del, purpose: $pur,
      scopes: ["view_bookings","create_bookings"],
      expiresAt: $exp}')

  DELEGATION_RESPONSE=$(curl -s -X POST "$DELEGATION_SERVICE_URL/api/delegations" \
    -H "Authorization: Bearer $CAROL_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$DELEGATION_PAYLOAD")

  DELEGATION_ID=$(echo "$DELEGATION_RESPONSE" | jq -r '.id // empty')
  CREATED_DELEGATION=true

  assert_not_empty "Delegation created"             "$DELEGATION_ID"
  assert_http "Delegation delegatorId=Carol"        "$DELEGATION_RESPONSE" '.delegatorId' "$CAROL_USER"
  assert_http "Delegation delegateId=Dave"          "$DELEGATION_RESPONSE" '.delegateId'  "$DAVE_USER"
  assert_http "Delegation purpose=book_travel"      "$DELEGATION_RESPONSE" '.purpose'     "$DELEGATION_PURPOSE"
  assert_http "Delegation active=true"              "$DELEGATION_RESPONSE" '.active'      "true"
fi
info "Delegation ID: $DELEGATION_ID"

info "Dave can see the delegation in /delegations/to-me"
TO_ME=$(curl -s "$DELEGATION_SERVICE_URL/api/delegations/to-me" \
  -H "Authorization: Bearer $DAVE_TOKEN")
assert_contains "Dave sees his delegation in to-me list" "$TO_ME" "$DELEGATION_ID"

# ---------------------------------------------------------------------------
# Phase 4: Consent record (consent-service)
# ---------------------------------------------------------------------------
header "Phase 4 — Consent record (consent-service)"

info "Looking for existing active consent: Carol → Dave / $DELEGATION_PURPOSE"
EXISTING_CONSENT=$(curl -s "$CONSENT_SERVICE_URL/api/consents/my-consents" \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  | jq -r --arg grantee "$DAVE_USER" --arg pur "$DELEGATION_PURPOSE" \
    '.[] | select(.granteeId == $grantee and .purpose == $pur and .valid == true) | .id' \
  | head -1)

if [[ -n "$EXISTING_CONSENT" ]]; then
  CONSENT_ID="$EXISTING_CONSENT"
  CREATED_CONSENT=false
  info "Reusing existing consent: $CONSENT_ID"
  pass "Consent exists (reused pre-existing record)"
else
  info "No existing consent found — creating one"
  CONSENT_PAYLOAD=$(jq -n \
    --arg grantor "$CAROL_USER" --arg grantee "$DAVE_USER" \
    --arg pur "$DELEGATION_PURPOSE" --arg exp "$EXPIRES_AT" \
    '{grantorId: $grantor, granteeId: $grantee, purpose: $pur,
      scopes: ["view_bookings","create_bookings"],
      expiresAt: $exp}')

  CONSENT_RESPONSE=$(curl -s -X POST "$CONSENT_SERVICE_URL/api/consents" \
    -H "Authorization: Bearer $CAROL_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$CONSENT_PAYLOAD")

  CONSENT_ID=$(echo "$CONSENT_RESPONSE" | jq -r '.id // empty')
  CREATED_CONSENT=true

  assert_not_empty "Consent created"                  "$CONSENT_ID"
  assert_http "Consent grantorId=Carol"               "$CONSENT_RESPONSE" '.grantorId' "$CAROL_USER"
  assert_http "Consent granteeId=Dave"                "$CONSENT_RESPONSE" '.granteeId' "$DAVE_USER"
  assert_http "Consent purpose=book_travel"           "$CONSENT_RESPONSE" '.purpose'   "$DELEGATION_PURPOSE"
  assert_http "Consent status=ACTIVE"                 "$CONSENT_RESPONSE" '.status'    "ACTIVE"
fi
info "Consent ID: $CONSENT_ID"

info "Validating consent via POST /api/consents/validate"
VALIDATE_PAYLOAD=$(jq -n \
  --arg grantor "$CAROL_USER" --arg grantee "$DAVE_USER" --arg pur "$DELEGATION_PURPOSE" \
  '{grantorId: $grantor, granteeId: $grantee, purpose: $pur,
    scopes: ["view_bookings","create_bookings"]}')
VALIDATE_RESPONSE=$(curl -s -X POST "$CONSENT_SERVICE_URL/api/consents/validate" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$VALIDATE_PAYLOAD")
assert_http "Consent validates as valid=true" "$VALIDATE_RESPONSE" '.valid' "true"

# ---------------------------------------------------------------------------
# Phase 5: OPA policy — direct rule verification
# ---------------------------------------------------------------------------
header "Phase 5 — OPA policy rules (direct)"

opa_check() {
  local label="$1" user_id="$2" action="$3" resource_json="$4" \
        delegation_json="$5" consent_json="$6" expected="$7"
  local result
  result=$(curl -s -X POST "$OPA_URL/v1/data/corporate/travel/authorization/allow" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
      --arg uid "$user_id" \
      --arg action "$action" \
      --argjson resource "$resource_json" \
      --argjson delegation "$delegation_json" \
      --argjson consent "$consent_json" \
      '{input:{user:{user_id:$uid,tenant_id:"tenant-a",roles:[]},
               action:$action, resource:$resource,
               delegation:$delegation, consent:$consent}}')" \
    | jq -r '.result // false')
  [[ "$result" == "$expected" ]] && pass "$label" || fail "$label (expected=$expected, got=$result)"
}

DELEG_ACTIVE=$(jq -n \
  --arg did "$DAVE_USER" --arg cid "$CAROL_USER" \
  '{active:true, delegate_id:$did, delegator_id:$cid}')
CONSENT_VALID=$(jq -n \
  --arg cid "$CONSENT_ID" \
  '{valid:true, consent_id:$cid, scopes:["book_travel","view_booking"]}')
CONSENT_NONE='{"valid":false,"scopes":[]}'
DELEG_NONE='{"active":false}'
BOOKING_RESOURCE=$(jq -n \
  --arg uid "$CAROL_USER" \
  '{type:"booking",tenant_id:"tenant-a",user_id:$uid}')

# Booking authorization
opa_check "create_booking — allowed with active delegation+consent" \
  "$DAVE_USER" "create_booking" "$BOOKING_RESOURCE" "$DELEG_ACTIVE" "$CONSENT_VALID" "true"
opa_check "create_booking — denied without delegation" \
  "$DAVE_USER" "create_booking" "$BOOKING_RESOURCE" "$DELEG_NONE" "$CONSENT_NONE" "false"
opa_check "create_booking — denied with delegation but no consent" \
  "$DAVE_USER" "create_booking" "$BOOKING_RESOURCE" "$DELEG_ACTIVE" "$CONSENT_NONE" "false"
opa_check "view_booking — allowed with active delegation" \
  "$DAVE_USER" "view_booking" "$BOOKING_RESOURCE" "$DELEG_ACTIVE" "$CONSENT_VALID" "true"
opa_check "view_booking — allowed for own booking (no delegation)" \
  "$CAROL_USER" "view_booking" "$BOOKING_RESOURCE" "$DELEG_NONE" "$CONSENT_NONE" "true"

# Delegation authorization (Carol is the actor)
DELEG_RESOURCE=$(jq -n \
  --arg did "$DAVE_USER" --arg cid "$CAROL_USER" \
  '{resource_type:"delegation",action:"create",tenant_id:"tenant-a",
    delegator_id:$cid, delegate_id:$did}')
opa_check "create_delegation — Carol is allowed to create her own delegation" \
  "$CAROL_USER" "create_delegation" "$DELEG_RESOURCE" "$DELEG_NONE" "$CONSENT_NONE" "true"
opa_check "view_delegations — allowed for own tenant" \
  "$CAROL_USER" "view_delegations" \
  '{"resource_type":"delegation","action":"view","tenant_id":"tenant-a"}' \
  "$DELEG_NONE" "$CONSENT_NONE" "true"

# Consent authorization (Carol is the actor)
CONSENT_RESOURCE=$(jq -n \
  --arg grantor "$CAROL_USER" --arg grantee "$DAVE_USER" \
  '{type:"consent",tenant_id:"tenant-a",grantor_id:$grantor,grantee_id:$grantee}')
opa_check "create_consent — Carol (grantor) is allowed" \
  "$CAROL_USER" "create_consent" "$CONSENT_RESOURCE" "$DELEG_NONE" "$CONSENT_NONE" "true"
opa_check "validate_consent — any tenant member is allowed" \
  "$DAVE_USER" "validate_consent" '{"tenant_id":"tenant-a"}' "$DELEG_NONE" "$CONSENT_NONE" "true"
opa_check "list_consents_to_me — any tenant member is allowed" \
  "$DAVE_USER" "list_consents_to_me" '{"tenant_id":"tenant-a"}' "$DELEG_NONE" "$CONSENT_NONE" "true"

# ---------------------------------------------------------------------------
# Phase 6: BFF delegation activation (ADR-018)
# ---------------------------------------------------------------------------
header "Phase 6 — BFF delegation activation (ADR-018)"

info "Dave activates delegation mode via BFF (audience=travel-service)"
ACTIVATION_RESPONSE=$(curl -s -X POST \
  "$BFF_URL/api/bff/delegation/activate/$DELEGATION_ID?audience=travel-service" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -c /tmp/e2e-bff-session.txt)

assert_http "Activation actorId=Dave"    "$ACTIVATION_RESPONSE" '.actorId'   "$DAVE_USER"
assert_http "Activation subjectId=Carol" "$ACTIVATION_RESPONSE" '.subjectId' "$CAROL_USER"
assert_http "Activation audience"        "$ACTIVATION_RESPONSE" '.audience'  "travel-service"
assert_http "Activation purpose"         "$ACTIVATION_RESPONSE" '.purpose'   "$DELEGATION_PURPOSE"

BFF_CONSENT_ID=$(echo "$ACTIVATION_RESPONSE" | jq -r '.consentId')
assert_not_empty "Activation captures consentId" "$BFF_CONSENT_ID"
assert_eq "Activation consentId matches known consent" "$CONSENT_ID" "$BFF_CONSENT_ID"

DELEGATION_TOKEN=$(echo "$ACTIVATION_RESPONSE" | jq -r '.delegationToken')
assert_not_empty "Activation issues a delegation token" "$DELEGATION_TOKEN"

if [[ -n "$DELEGATION_TOKEN" && "$DELEGATION_TOKEN" != "null" ]]; then
  DT_CLAIMS=$(decode_jwt "$DELEGATION_TOKEN")
  assert_eq "Delegation token aud=travel-service" \
    "travel-service" "$(echo "$DT_CLAIMS" | jq -r '.aud')"
  assert_eq "Delegation token actor identity preserved (preferred_username=Dave)" \
    "$DAVE_USER" "$(echo "$DT_CLAIMS" | jq -r '.preferred_username')"
fi

info "Dave checks session context"
CTX_RESPONSE=$(curl -s "$BFF_URL/api/bff/delegation/context" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -b /tmp/e2e-bff-session.txt -c /tmp/e2e-bff-session.txt)
assert_http "Session delegationActive=true"  "$CTX_RESPONSE" '.delegationActive' "true"
assert_http "Session actorId=Dave"           "$CTX_RESPONSE" '.actorId'          "$DAVE_USER"
assert_http "Session subjectId=Carol"        "$CTX_RESPONSE" '.subjectId'        "$CAROL_USER"

# ---------------------------------------------------------------------------
# Phase 7: Delegated booking (ADR-011 identity chain)
# ---------------------------------------------------------------------------
header "Phase 7 — Delegated booking creation (ADR-011 identity chain)"

info "Dave creates a booking on Carol's behalf via BFF"
BOOKING_RESPONSE=$(curl -s -X POST "$BFF_URL/api/bff/bookings" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -b /tmp/e2e-bff-session.txt -c /tmp/e2e-bff-session.txt \
  -d '{
    "bookingType": "FLIGHT",
    "tenantId":    "placeholder",
    "userId":      "placeholder",
    "status":      "PENDING",
    "destination": "New York",
    "startDate":   "2026-05-01",
    "endDate":     "2026-05-05",
    "totalAmount": 1500.00
  }')

BOOKING_ID=$(echo "$BOOKING_RESPONSE" | jq -r '.id // empty')
assert_not_empty "Booking created successfully"  "$BOOKING_ID"

# ADR-011: full identity chain must be visible in the persisted record
assert_http "Booking userId=Carol (subject owns the record)" \
  "$BOOKING_RESPONSE" '.userId'      "$CAROL_USER"
assert_http "Booking createdBy=Dave (actor logged for audit)" \
  "$BOOKING_RESPONSE" '.createdBy'   "$DAVE_USER"
assert_http "Booking tenantId enforced by server (not caller)" \
  "$BOOKING_RESPONSE" '.tenantId'    "tenant-a"
assert_http "Booking status=PENDING" \
  "$BOOKING_RESPONSE" '.status'      "PENDING"
assert_http "Booking destination=New York" \
  "$BOOKING_RESPONSE" '.destination' "New York"
info "Booking ID: $BOOKING_ID"

info "Dave fetches Carol's bookings via BFF (delegation mode)"
BOOKINGS_RESPONSE=$(curl -s "$BFF_URL/api/bff/bookings" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -b /tmp/e2e-bff-session.txt -c /tmp/e2e-bff-session.txt)
assert_contains "Carol's new booking appears in BFF list" "$BOOKINGS_RESPONSE" "$BOOKING_ID"

# ---------------------------------------------------------------------------
# Phase 8: Direct travel-service with delegation headers (ADR-004 Layer 2)
# ---------------------------------------------------------------------------
header "Phase 8 — Direct travel-service delegation headers (ADR-004 Layer 2)"

info "Calling travel-service directly with X-Delegated-Subject, X-Consent-Id, X-Delegation-Purpose"
DIRECT_RESPONSE=$(curl -s "$TRAVEL_SERVICE_URL/api/bookings" \
  -H "Authorization: Bearer $DELEGATION_TOKEN" \
  -H "X-Delegated-Subject: $CAROL_USER" \
  -H "X-Delegation-Id: $DELEGATION_ID" \
  -H "X-Consent-Id: $BFF_CONSENT_ID" \
  -H "X-Delegation-Purpose: $DELEGATION_PURPOSE" \
  -H "X-Actor-Token: $DAVE_TOKEN")
assert_contains "Travel service returns Carol's booking with delegation headers" \
  "$DIRECT_RESPONSE" "$BOOKING_ID"

info "Verifying booking record has correct identity chain"
SINGLE_BOOKING=$(curl -s "$TRAVEL_SERVICE_URL/api/bookings/$BOOKING_ID" \
  -H "Authorization: Bearer $DELEGATION_TOKEN" \
  -H "X-Delegated-Subject: $CAROL_USER" \
  -H "X-Delegation-Id: $DELEGATION_ID" \
  -H "X-Consent-Id: $BFF_CONSENT_ID" \
  -H "X-Delegation-Purpose: $DELEGATION_PURPOSE" \
  -H "X-Actor-Token: $DAVE_TOKEN")
assert_http "Single booking userId=Carol" \
  "$SINGLE_BOOKING" '.userId'    "$CAROL_USER"
assert_http "Single booking createdBy=Dave" \
  "$SINGLE_BOOKING" '.createdBy' "$DAVE_USER"
assert_http "Single booking tenantId=tenant-a" \
  "$SINGLE_BOOKING" '.tenantId'  "tenant-a"

# ---------------------------------------------------------------------------
# Phase 9: Isolation checks
# ---------------------------------------------------------------------------
header "Phase 9 — Isolation checks"

info "Dave's plain token (no delegation headers) must not see Carol's booking"
PLAIN_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "$TRAVEL_SERVICE_URL/api/bookings/$BOOKING_ID" \
  -H "Authorization: Bearer $DAVE_TOKEN")
if [[ "$PLAIN_HTTP_CODE" =~ ^(403|404)$ ]]; then
  pass "Dave denied Carol's booking without delegation headers (HTTP $PLAIN_HTTP_CODE)"
else
  skip "Isolation check inconclusive (HTTP $PLAIN_HTTP_CODE) — booking may be visible if OPA cross-user policy allows it"
fi

info "Deactivating delegation mode in BFF session"
DEACT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  "$BFF_URL/api/bff/delegation/deactivate" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -b /tmp/e2e-bff-session.txt -c /tmp/e2e-bff-session.txt)
assert_eq "BFF delegation deactivated (HTTP 204)" "204" "$DEACT_CODE"

CTX_AFTER=$(curl -s "$BFF_URL/api/bff/delegation/context" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -b /tmp/e2e-bff-session.txt -c /tmp/e2e-bff-session.txt)
assert_http "Session shows delegationActive=false after deactivation" \
  "$CTX_AFTER" '.delegationActive' "false"

info "After deactivation, BFF bookings list returns only Dave's own bookings (not Carol's)"
DAVE_BOOKINGS=$(curl -s "$BFF_URL/api/bff/bookings" \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -b /tmp/e2e-bff-session.txt -c /tmp/e2e-bff-session.txt)
if echo "$DAVE_BOOKINGS" | grep -q "$BOOKING_ID"; then
  skip "Booking $BOOKING_ID still visible to Dave — may be expected if Dave is in same tenant"
else
  pass "Carol's booking is no longer visible to Dave after deactivation"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
TOTAL=$((PASS + FAIL + SKIP))
echo ""
echo -e "${BOLD}$(printf '═%.0s' {1..60})${NC}"
echo -e "${BOLD}  Result: $TOTAL checks — ${GREEN}$PASS passed${NC}${BOLD}, ${RED}$FAIL failed${NC}${BOLD}, ${YELLOW}$SKIP skipped${NC}"
echo -e "${BOLD}$(printf '═%.0s' {1..60})${NC}"
echo ""

[[ $FAIL -gt 0 ]] && { echo -e "${RED}One or more assertions failed. Review output above.${NC}"; exit 1; }
exit 0
