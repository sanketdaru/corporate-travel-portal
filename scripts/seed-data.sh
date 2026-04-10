#!/usr/bin/env bash
# =============================================================================
# Phase 0 — Seed Data Script
# =============================================================================
#
# Seeds the backend with realistic travel and expense data across 9 geographies
# for all 5 test users, including delegated bookings and expenses created by
# dave.assistant on behalf of carol.executive via two separate BFF delegation
# sessions (audience=travel-service for bookings, audience=expense-service for
# expenses).
#
# Usage:
#   ./scripts/seed-data.sh
#
# Requirements: curl, jq
# Services must be running (docker compose up / podman compose up)
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
KEYCLOAK_URL="http://localhost:8080"
REALM="corporate-travel"
CLIENT_ID="employee-bff"
CLIENT_SECRET="bff-service-secret-change-in-production"

BFF_URL="http://localhost:8085"
GW_URL="http://localhost:8000"
DELEGATION_URL="http://localhost:8083"
CONSENT_URL="http://localhost:8084"

EXPIRES_AT="2027-06-01T00:00:00"

# Temp session cookie jar for dave's BFF delegation session
DAVE_COOKIE_JAR="/tmp/seed-dave-bff-session.txt"

# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()    { echo -e "  ${CYAN}INFO${NC}  $1"; }
ok()      { echo -e "  ${GREEN}OK${NC}    $1"; }
warn()    { echo -e "  ${YELLOW}WARN${NC}  $1"; }
err()     { echo -e "  ${RED}ERROR${NC} $1"; }
header()  { echo -e "\n${BOLD}$1${NC}"; echo -e "${BOLD}$(printf '─%.0s' {1..60})${NC}"; }

# ---------------------------------------------------------------------------
# Auth helper
# ---------------------------------------------------------------------------
get_token() {
  local user="$1" pass="${2:-password123}"
  local resp
  resp=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "username=$user" \
    -d "password=$pass" \
    -d "grant_type=password")
  local token
  token=$(echo "$resp" | jq -r '.access_token // empty')
  if [[ -z "$token" || "$token" == "null" ]]; then
    err "Failed to get token for $user — response: $resp"
    return 1
  fi
  echo "$token"
}

# ---------------------------------------------------------------------------
# BFF helpers (session cookie aware)
# ---------------------------------------------------------------------------
bff_post() {
  local token="$1" path="$2" body="$3" cookie_jar="${4:-}"
  if [[ -n "$cookie_jar" ]]; then
    curl -s -X POST "$BFF_URL$path" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -b "$cookie_jar" -c "$cookie_jar" \
      -d "$body"
  else
    curl -s -X POST "$BFF_URL$path" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -d "$body"
  fi
}

bff_delete() {
  local token="$1" path="$2" cookie_jar="${3:-}"
  if [[ -n "$cookie_jar" ]]; then
    curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BFF_URL$path" \
      -H "Authorization: Bearer $token" \
      -b "$cookie_jar" -c "$cookie_jar"
  else
    curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BFF_URL$path" \
      -H "Authorization: Bearer $token"
  fi
}

# ---------------------------------------------------------------------------
# Gateway helpers (no session cookies needed)
# ---------------------------------------------------------------------------
gw_post() {
  local token="$1" path="$2" body="${3:-{}}"
  curl -s -X POST "$GW_URL$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

gw_put() {
  local token="$1" path="$2" body="$3"
  curl -s -X PUT "$GW_URL$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

# ---------------------------------------------------------------------------
# Booking helpers
# ---------------------------------------------------------------------------
create_booking() {
  local token="$1" booking_type="$2" destination="$3" \
        start_date="$4" end_date="$5" amount="$6" status="$7"
  local cookie_jar="${8:-}"

  local body
  body=$(jq -n \
    --arg bt "$booking_type" \
    --arg dest "$destination" \
    --arg sd "$start_date" \
    --arg ed "$end_date" \
    --argjson amt "$amount" \
    --arg st "$status" \
    '{bookingType:$bt, tenantId:"placeholder", userId:"placeholder",
      destination:$dest, startDate:$sd, endDate:$ed,
      totalAmount:$amt, status:$st}')

  if [[ -n "$cookie_jar" ]]; then
    bff_post "$token" "/api/bff/bookings" "$body" "$cookie_jar"
  else
    bff_post "$token" "/api/bff/bookings" "$body"
  fi
}

update_booking_status() {
  local token="$1" booking_id="$2" status="$3"
  gw_put "$token" "/api/bookings/$booking_id/status" "{\"status\":\"$status\"}"
}

# ---------------------------------------------------------------------------
# Expense helpers
# ---------------------------------------------------------------------------
create_expense() {
  local token="$1" title="$2" description="$3" currency="${4:-INR}"
  local cookie_jar="${5:-}"

  local body
  body=$(jq -n \
    --arg title "$title" \
    --arg desc "$description" \
    --arg curr "$currency" \
    '{tenantId:"placeholder", userId:"placeholder",
      title:$title, description:$desc, currency:$curr, status:"DRAFT"}')

  if [[ -n "$cookie_jar" ]]; then
    bff_post "$token" "/api/bff/expenses" "$body" "$cookie_jar"
  else
    bff_post "$token" "/api/bff/expenses" "$body"
  fi
}

add_expense_item() {
  local token="$1" expense_id="$2" date="$3" \
        category="$4" description="$5" amount="$6"
  local body
  body=$(jq -n \
    --arg date "$date" \
    --arg cat "$category" \
    --arg desc "$description" \
    --argjson amt "$amount" \
    '{date:$date, category:$cat, description:$desc, amount:$amt, currency:"INR"}')
  gw_post "$token" "/api/expenses/$expense_id/items" "$body"
}

submit_expense() {
  local token="$1" expense_id="$2"
  gw_post "$token" "/api/expenses/$expense_id/submit"
}

approve_expense() {
  local token="$1" expense_id="$2" comments="${3:-Approved}"
  gw_post "$token" "/api/expenses/$expense_id/approve" "{\"comments\":\"$comments\"}"
}

reject_expense() {
  local token="$1" expense_id="$2" comments="${3:-Rejected}"
  gw_post "$token" "/api/expenses/$expense_id/reject" "{\"comments\":\"$comments\"}"
}

# ---------------------------------------------------------------------------
# Delegation helpers
# ---------------------------------------------------------------------------
create_delegation() {
  local token="$1" delegate_id="$2" purpose="$3" scopes="$4"
  local body
  body=$(jq -n \
    --arg del "$delegate_id" \
    --arg pur "$purpose" \
    --argjson sco "$scopes" \
    --arg exp "$EXPIRES_AT" \
    '{delegateId:$del, purpose:$pur, scopes:$sco, expiresAt:$exp}')
  curl -s -X POST "$DELEGATION_URL/api/delegations" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

create_consent() {
  local token="$1" grantor_id="$2" grantee_id="$3" purpose="$4" scopes="$5"
  local body
  body=$(jq -n \
    --arg grantor "$grantor_id" \
    --arg grantee "$grantee_id" \
    --arg pur "$purpose" \
    --argjson sco "$scopes" \
    --arg exp "$EXPIRES_AT" \
    '{grantorId:$grantor, granteeId:$grantee, purpose:$pur, scopes:$sco, expiresAt:$exp}')
  curl -s -X POST "$CONSENT_URL/api/consents" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

activate_delegation() {
  local token="$1" delegation_id="$2" cookie_jar="$3" audience="${4:-travel-service}"
  curl -s -X POST \
    "$BFF_URL/api/bff/delegation/activate/$delegation_id?audience=$audience" \
    -H "Authorization: Bearer $token" \
    -c "$cookie_jar" -b "$cookie_jar"
}

deactivate_delegation() {
  local token="$1" cookie_jar="$2"
  curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    "$BFF_URL/api/bff/delegation/deactivate" \
    -H "Authorization: Bearer $token" \
    -c "$cookie_jar" -b "$cookie_jar"
}

# ---------------------------------------------------------------------------
# Pre-flight
# ---------------------------------------------------------------------------
header "Pre-flight checks"

for cmd in curl jq; do
  command -v "$cmd" &>/dev/null && ok "$cmd available" || { err "$cmd not installed"; exit 1; }
done

check_health() {
  local name="$1" url="$2"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 4 "$url" 2>/dev/null || echo "000")
  [[ "$code" =~ ^(200|204|301|302|400)$ ]] \
    && ok "$name reachable" \
    || { err "$name not reachable (HTTP $code) — $url"; exit 1; }
}

check_health "Keycloak"           "$KEYCLOAK_URL/realms/$REALM"
check_health "Employee BFF"       "$BFF_URL/actuator/health"
check_health "API Gateway"        "$GW_URL/actuator/health"
check_health "Delegation Service" "$DELEGATION_URL/actuator/health"
check_health "Consent Service"    "$CONSENT_URL/actuator/health"

# ---------------------------------------------------------------------------
# Step 1: Obtain tokens
# ---------------------------------------------------------------------------
header "Step 1 — Obtaining JWT tokens"

info "alice.employee"
ALICE_TOKEN=$(get_token "alice.employee"); ok "Alice token OK"

info "bob.manager"
BOB_TOKEN=$(get_token "bob.manager"); ok "Bob token OK"

info "carol.executive"
CAROL_TOKEN=$(get_token "carol.executive"); ok "Carol token OK"

info "dave.assistant"
DAVE_TOKEN=$(get_token "dave.assistant"); ok "Dave token OK"

info "eve.employee"
EVE_TOKEN=$(get_token "eve.employee"); ok "Eve token OK"

# ---------------------------------------------------------------------------
# Step 2: Delegation + Consent — Carol grants Dave book_travel rights
# ---------------------------------------------------------------------------
header "Step 2 — Carol → Dave delegation and consent"

SCOPES='["view_bookings","create_bookings","view_expenses","create_expenses"]'

info "Checking for existing delegation carol.executive → dave.assistant"
EXISTING_DEL=$(curl -s "$DELEGATION_URL/api/delegations/my-delegations" \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  | jq -r '.[] | select(.delegateId == "dave.assistant" and .purpose == "book_travel" and .active == true) | .id' \
  | head -1)

if [[ -n "$EXISTING_DEL" ]]; then
  DELEGATION_ID="$EXISTING_DEL"
  ok "Reusing existing delegation: $DELEGATION_ID"
else
  DELEG_RESP=$(create_delegation "$CAROL_TOKEN" "dave.assistant" "book_travel" "$SCOPES")
  DELEGATION_ID=$(echo "$DELEG_RESP" | jq -r '.id // empty')
  if [[ -z "$DELEGATION_ID" || "$DELEGATION_ID" == "null" ]]; then
    err "Failed to create delegation: $DELEG_RESP"
    exit 1
  fi
  ok "Delegation created: $DELEGATION_ID"
fi

info "Checking for existing consent carol.executive → dave.assistant"
EXISTING_CON=$(curl -s "$CONSENT_URL/api/consents/my-consents" \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  | jq -r '.[] | select(.granteeId == "dave.assistant" and .purpose == "book_travel" and .valid == true) | .id' \
  | head -1)

if [[ -n "$EXISTING_CON" ]]; then
  CONSENT_ID="$EXISTING_CON"
  ok "Reusing existing consent: $CONSENT_ID"
else
  CON_RESP=$(create_consent "$CAROL_TOKEN" "carol.executive" "dave.assistant" "book_travel" "$SCOPES")
  CONSENT_ID=$(echo "$CON_RESP" | jq -r '.id // empty')
  if [[ -z "$CONSENT_ID" || "$CONSENT_ID" == "null" ]]; then
    err "Failed to create consent: $CON_RESP"
    exit 1
  fi
  ok "Consent created: $CONSENT_ID"
fi

# ---------------------------------------------------------------------------
# Step 3: alice.employee — 3 bookings, 2 expenses
# ---------------------------------------------------------------------------
header "Step 3 — alice.employee (tenant-a)"

# Booking 1: London FLIGHT → CONFIRMED
info "Creating London FLIGHT booking"
RESP=$(create_booking "$ALICE_TOKEN" "FLIGHT" "London, UK" "2026-05-10" "2026-05-17" 87500 "DRAFT")
BKG_ALICE_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_ALICE_1" || "$BKG_ALICE_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$ALICE_TOKEN" "$BKG_ALICE_1" "CONFIRMED" > /dev/null
ok "Alice Booking 1 (London, CONFIRMED): $BKG_ALICE_1"

# Booking 2: Tokyo FLIGHT → CONFIRMED
info "Creating Tokyo FLIGHT booking"
RESP=$(create_booking "$ALICE_TOKEN" "FLIGHT" "Tokyo, Japan" "2026-03-05" "2026-03-12" 112000 "DRAFT")
BKG_ALICE_2=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_ALICE_2" || "$BKG_ALICE_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$ALICE_TOKEN" "$BKG_ALICE_2" "CONFIRMED" > /dev/null
ok "Alice Booking 2 (Tokyo, CONFIRMED): $BKG_ALICE_2"

# Booking 3: Paris FLIGHT → DRAFT
info "Creating Paris FLIGHT booking"
RESP=$(create_booking "$ALICE_TOKEN" "FLIGHT" "Paris, France" "2026-07-20" "2026-07-27" 73000 "DRAFT")
BKG_ALICE_3=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_ALICE_3" || "$BKG_ALICE_3" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
ok "Alice Booking 3 (Paris, DRAFT): $BKG_ALICE_3"

# Expense 1: London Trip → SUBMITTED → APPROVED (by bob.manager)
info "Creating London expense (to be approved)"
RESP=$(create_expense "$ALICE_TOKEN" "London Business Trip — May 2026" "Flight and hotel for client meetings in London")
EXP_ALICE_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$EXP_ALICE_1" || "$EXP_ALICE_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
add_expense_item "$ALICE_TOKEN" "$EXP_ALICE_1" "2026-05-10" "TRAVEL" "Return flight Mumbai–London" 87500 > /dev/null
add_expense_item "$ALICE_TOKEN" "$EXP_ALICE_1" "2026-05-11" "ACCOMMODATION" "Hotel — 7 nights in London" 98000 > /dev/null
add_expense_item "$ALICE_TOKEN" "$EXP_ALICE_1" "2026-05-12" "MEALS" "Client dinner" 8500 > /dev/null
submit_expense "$ALICE_TOKEN" "$EXP_ALICE_1" > /dev/null
approve_expense "$BOB_TOKEN" "$EXP_ALICE_1" "Approved — valid business travel" > /dev/null
ok "Alice Expense 1 (London, APPROVED): $EXP_ALICE_1"

# Expense 2: Tokyo Trip → DRAFT
info "Creating Tokyo expense (draft)"
RESP=$(create_expense "$ALICE_TOKEN" "Tokyo Conference — March 2026" "Tech conference attendance")
EXP_ALICE_2=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$EXP_ALICE_2" || "$EXP_ALICE_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
add_expense_item "$ALICE_TOKEN" "$EXP_ALICE_2" "2026-03-05" "TRAVEL" "Return flight Mumbai–Tokyo" 112000 > /dev/null
add_expense_item "$ALICE_TOKEN" "$EXP_ALICE_2" "2026-03-06" "ACCOMMODATION" "Hotel — 7 nights in Tokyo" 56000 > /dev/null
ok "Alice Expense 2 (Tokyo, DRAFT): $EXP_ALICE_2"

# ---------------------------------------------------------------------------
# Step 4: bob.manager — 2 bookings, 1 expense
# ---------------------------------------------------------------------------
header "Step 4 — bob.manager (tenant-a)"

# Booking 1: New York FLIGHT → CONFIRMED
info "Creating New York FLIGHT booking"
RESP=$(create_booking "$BOB_TOKEN" "FLIGHT" "New York, USA" "2026-04-15" "2026-04-22" 95000 "DRAFT")
BKG_BOB_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_BOB_1" || "$BKG_BOB_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$BOB_TOKEN" "$BKG_BOB_1" "CONFIRMED" > /dev/null
ok "Bob Booking 1 (New York, CONFIRMED): $BKG_BOB_1"

# Booking 2: Sydney HOTEL → CONFIRMED
info "Creating Sydney HOTEL booking"
RESP=$(create_booking "$BOB_TOKEN" "HOTEL" "Sydney, Australia" "2026-06-01" "2026-06-05" 64000 "DRAFT")
BKG_BOB_2=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_BOB_2" || "$BKG_BOB_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$BOB_TOKEN" "$BKG_BOB_2" "CONFIRMED" > /dev/null
ok "Bob Booking 2 (Sydney, CONFIRMED): $BKG_BOB_2"

# Expense 1: NYC trip → SUBMITTED (pending approval)
info "Creating NYC expense (submitted, pending approval)"
RESP=$(create_expense "$BOB_TOKEN" "New York Strategy Summit — April 2026" "Annual leadership summit attendance")
EXP_BOB_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$EXP_BOB_1" || "$EXP_BOB_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
add_expense_item "$BOB_TOKEN" "$EXP_BOB_1" "2026-04-15" "TRAVEL" "Return flight Mumbai–New York" 95000 > /dev/null
add_expense_item "$BOB_TOKEN" "$EXP_BOB_1" "2026-04-16" "ACCOMMODATION" "Hotel — 7 nights in New York" 105000 > /dev/null
add_expense_item "$BOB_TOKEN" "$EXP_BOB_1" "2026-04-17" "TRANSPORTATION" "Airport transfers and local transport" 12000 > /dev/null
submit_expense "$BOB_TOKEN" "$EXP_BOB_1" > /dev/null
ok "Bob Expense 1 (NYC, SUBMITTED): $EXP_BOB_1"

# ---------------------------------------------------------------------------
# Step 5: carol.executive — 2 direct bookings, 1 direct expense
# ---------------------------------------------------------------------------
header "Step 5 — carol.executive direct records (tenant-a)"

# Booking 1: Berlin HOTEL → CONFIRMED
info "Creating Berlin HOTEL booking"
RESP=$(create_booking "$CAROL_TOKEN" "HOTEL" "Berlin, Germany" "2026-03-20" "2026-03-25" 48000 "DRAFT")
BKG_CAROL_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_CAROL_1" || "$BKG_CAROL_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$CAROL_TOKEN" "$BKG_CAROL_1" "CONFIRMED" > /dev/null
ok "Carol Booking 1 (Berlin, CONFIRMED): $BKG_CAROL_1"

# Booking 2: Dubai FLIGHT → DRAFT
info "Creating Dubai FLIGHT booking (draft)"
RESP=$(create_booking "$CAROL_TOKEN" "FLIGHT" "Dubai, UAE" "2026-08-10" "2026-08-14" 62000 "DRAFT")
BKG_CAROL_2=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_CAROL_2" || "$BKG_CAROL_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
ok "Carol Booking 2 (Dubai, DRAFT): $BKG_CAROL_2"

# Expense 1: Berlin → SUBMITTED → APPROVED
info "Creating Berlin expense (to be approved)"
RESP=$(create_expense "$CAROL_TOKEN" "Berlin Executive Summit — March 2026" "European leadership conference")
EXP_CAROL_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$EXP_CAROL_1" || "$EXP_CAROL_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_1" "2026-03-20" "TRAVEL" "Return flight Mumbai–Berlin" 78000 > /dev/null
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_1" "2026-03-21" "ACCOMMODATION" "Hotel — 5 nights in Berlin" 60000 > /dev/null
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_1" "2026-03-22" "MEALS" "Business dinners" 15000 > /dev/null
submit_expense "$CAROL_TOKEN" "$EXP_CAROL_1" > /dev/null
approve_expense "$BOB_TOKEN" "$EXP_CAROL_1" "Approved — executive travel" > /dev/null
ok "Carol Expense 1 (Berlin, APPROVED): $EXP_CAROL_1"

# ---------------------------------------------------------------------------
# Step 6: dave.assistant acts on behalf of carol.executive (delegation flow)
# ---------------------------------------------------------------------------
header "Step 6 — dave.assistant delegated actions for carol.executive"

rm -f "$DAVE_COOKIE_JAR"

# --- Phase A: audience=travel-service — delegated bookings ---
info "Activating delegation (audience=travel-service)"
ACT_RESP=$(activate_delegation "$DAVE_TOKEN" "$DELEGATION_ID" "$DAVE_COOKIE_JAR" "travel-service")
ACT_ACTOR=$(echo "$ACT_RESP" | jq -r '.actorId // empty')
ACT_SUBJECT=$(echo "$ACT_RESP" | jq -r '.subjectId // empty')
if [[ "$ACT_ACTOR" != "dave.assistant" || "$ACT_SUBJECT" != "carol.executive" ]]; then
  err "Delegation activation failed: $ACT_RESP"; exit 1
fi
ok "Delegation activated (travel-service) — actor=dave.assistant subject=carol.executive"

# Delegated Booking 1: Shanghai FLIGHT → DRAFT (for carol)
info "Creating Shanghai FLIGHT booking on Carol's behalf"
RESP=$(create_booking "$DAVE_TOKEN" "FLIGHT" "Shanghai, China" "2026-09-05" "2026-09-10" 89000 "DRAFT" "$DAVE_COOKIE_JAR")
BKG_CAROL_DEL_1=$(echo "$RESP" | jq -r '.id // empty')
BKG_CAROL_DEL_1_USER=$(echo "$RESP" | jq -r '.userId // empty')
BKG_CAROL_DEL_1_ACTOR=$(echo "$RESP" | jq -r '.createdBy // empty')
if [[ -z "$BKG_CAROL_DEL_1" || "$BKG_CAROL_DEL_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
ok "Carol Booking 3 via Dave (Shanghai FLIGHT, DRAFT): $BKG_CAROL_DEL_1 [userId=$BKG_CAROL_DEL_1_USER createdBy=$BKG_CAROL_DEL_1_ACTOR]"

# Delegated Booking 2: Bengaluru CAR → CONFIRMED (for carol)
info "Creating Bengaluru CAR booking on Carol's behalf"
RESP=$(create_booking "$DAVE_TOKEN" "CAR" "Bengaluru, India" "2026-06-15" "2026-06-18" 14000 "DRAFT" "$DAVE_COOKIE_JAR")
BKG_CAROL_DEL_2=$(echo "$RESP" | jq -r '.id // empty')
BKG_CAROL_DEL_2_USER=$(echo "$RESP" | jq -r '.userId // empty')
BKG_CAROL_DEL_2_ACTOR=$(echo "$RESP" | jq -r '.createdBy // empty')
if [[ -z "$BKG_CAROL_DEL_2" || "$BKG_CAROL_DEL_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$CAROL_TOKEN" "$BKG_CAROL_DEL_2" "CONFIRMED" > /dev/null
ok "Carol Booking 4 via Dave (Bengaluru CAR, CONFIRMED): $BKG_CAROL_DEL_2 [userId=$BKG_CAROL_DEL_2_USER createdBy=$BKG_CAROL_DEL_2_ACTOR]"

info "Deactivating travel-service delegation session"
DEACT_CODE=$(deactivate_delegation "$DAVE_TOKEN" "$DAVE_COOKIE_JAR")
[[ "$DEACT_CODE" == "204" ]] && ok "Delegation deactivated (HTTP 204)" || warn "Deactivate returned HTTP $DEACT_CODE"

# --- Phase B: audience=expense-service — delegated expenses ---
rm -f "$DAVE_COOKIE_JAR"
info "Re-activating delegation (audience=expense-service)"
ACT_RESP=$(activate_delegation "$DAVE_TOKEN" "$DELEGATION_ID" "$DAVE_COOKIE_JAR" "expense-service")
ACT_ACTOR=$(echo "$ACT_RESP" | jq -r '.actorId // empty')
ACT_SUBJECT=$(echo "$ACT_RESP" | jq -r '.subjectId // empty')
if [[ "$ACT_ACTOR" != "dave.assistant" || "$ACT_SUBJECT" != "carol.executive" ]]; then
  err "Delegation activation (expense-service) failed: $ACT_RESP"; exit 1
fi
ok "Delegation activated (expense-service) — actor=dave.assistant subject=carol.executive"

# Delegated Expense 2: Shanghai Trip → DRAFT (for carol)
# Dave creates, leaves as draft for carol to complete later
info "Creating Shanghai expense on Carol's behalf (DRAFT)"
RESP=$(create_expense "$DAVE_TOKEN" "Shanghai Business Trip — Sept 2026" "Pre-trip expenses filed by assistant" "INR" "$DAVE_COOKIE_JAR")
EXP_CAROL_2=$(echo "$RESP" | jq -r '.id // empty')
EXP_CAROL_2_USER=$(echo "$RESP" | jq -r '.userId // empty')
EXP_CAROL_2_ACTOR=$(echo "$RESP" | jq -r '.createdBy // empty')
if [[ -z "$EXP_CAROL_2" || "$EXP_CAROL_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
ok "Carol Expense 2 via Dave (Shanghai, DRAFT created): $EXP_CAROL_2 [userId=$EXP_CAROL_2_USER createdBy=$EXP_CAROL_2_ACTOR]"

# Delegated Expense 3: Bengaluru Trip → to be SUBMITTED (for carol)
# Dave creates and will also submit on carol's behalf
info "Creating Bengaluru expense on Carol's behalf (to be submitted)"
RESP=$(create_expense "$DAVE_TOKEN" "Bengaluru Team Offsite — June 2026" "Team coordination travel arranged by assistant" "INR" "$DAVE_COOKIE_JAR")
EXP_CAROL_3=$(echo "$RESP" | jq -r '.id // empty')
EXP_CAROL_3_USER=$(echo "$RESP" | jq -r '.userId // empty')
EXP_CAROL_3_ACTOR=$(echo "$RESP" | jq -r '.createdBy // empty')
if [[ -z "$EXP_CAROL_3" || "$EXP_CAROL_3" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
ok "Carol Expense 3 via Dave (Bengaluru, DRAFT created): $EXP_CAROL_3 [userId=$EXP_CAROL_3_USER createdBy=$EXP_CAROL_3_ACTOR]"

info "Deactivating expense-service delegation session"
DEACT_CODE=$(deactivate_delegation "$DAVE_TOKEN" "$DAVE_COOKIE_JAR")
[[ "$DEACT_CODE" == "204" ]] && ok "Delegation deactivated (HTTP 204)" || warn "Deactivate returned HTTP $DEACT_CODE"

# Add items and finalise delegated expenses as carol (she owns them)
info "Adding items to Carol's delegated Shanghai expense"
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_2" "2026-09-05" "TRAVEL" "Return flight Mumbai–Shanghai" 89000 > /dev/null
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_2" "2026-09-06" "ACCOMMODATION" "Hotel — 5 nights in Shanghai" 75000 > /dev/null
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_2" "2026-09-08" "MEALS" "Client entertainment" 12000 > /dev/null
ok "Carol Expense 2 (Shanghai, DRAFT with items): $EXP_CAROL_2"

info "Adding items to Carol's delegated Bengaluru expense and submitting"
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_3" "2026-06-15" "TRAVEL" "Return flight Mumbai–Bengaluru" 14000 > /dev/null
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_3" "2026-06-15" "ACCOMMODATION" "Hotel — 3 nights in Bengaluru" 24000 > /dev/null
add_expense_item "$CAROL_TOKEN" "$EXP_CAROL_3" "2026-06-16" "TRANSPORTATION" "Local cab for team" 4500 > /dev/null
submit_expense "$CAROL_TOKEN" "$EXP_CAROL_3" > /dev/null
ok "Carol Expense 3 (Bengaluru, SUBMITTED): $EXP_CAROL_3"

# ---------------------------------------------------------------------------
# Step 7: dave.assistant — 1 own booking
# ---------------------------------------------------------------------------
header "Step 7 — dave.assistant own booking (tenant-a)"

info "Creating Dave's own Sydney FLIGHT booking"
RESP=$(create_booking "$DAVE_TOKEN" "FLIGHT" "Sydney, Australia" "2026-05-20" "2026-05-25" 105000 "DRAFT")
BKG_DAVE_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_DAVE_1" || "$BKG_DAVE_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$DAVE_TOKEN" "$BKG_DAVE_1" "CONFIRMED" > /dev/null
ok "Dave Booking 1 (Sydney, CONFIRMED): $BKG_DAVE_1"

# ---------------------------------------------------------------------------
# Step 8: eve.employee — 2 bookings (tenant-b isolation test)
# ---------------------------------------------------------------------------
header "Step 8 — eve.employee (tenant-b)"

info "Creating Singapore FLIGHT booking"
RESP=$(create_booking "$EVE_TOKEN" "FLIGHT" "Singapore" "2026-04-20" "2026-04-25" 55000 "DRAFT")
BKG_EVE_1=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_EVE_1" || "$BKG_EVE_1" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
update_booking_status "$EVE_TOKEN" "$BKG_EVE_1" "CONFIRMED" > /dev/null
ok "Eve Booking 1 (Singapore, CONFIRMED): $BKG_EVE_1"

info "Creating Bengaluru HOTEL booking"
RESP=$(create_booking "$EVE_TOKEN" "HOTEL" "Bengaluru, India" "2026-05-12" "2026-05-15" 27000 "DRAFT")
BKG_EVE_2=$(echo "$RESP" | jq -r '.id // empty')
if [[ -z "$BKG_EVE_2" || "$BKG_EVE_2" == "null" ]]; then err "Failed: $RESP"; exit 1; fi
ok "Eve Booking 2 (Bengaluru, DRAFT): $BKG_EVE_2"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
header "Seed Complete"

echo ""
echo -e "${BOLD}Bookings seeded:${NC}"
echo "  alice.employee  : London (CONFIRMED), Tokyo (CONFIRMED), Paris (DRAFT)"
echo "  bob.manager     : New York (CONFIRMED), Sydney (CONFIRMED)"
echo "  carol.executive : Berlin (CONFIRMED), Dubai (DRAFT)"
echo "  carol via dave  : Shanghai (DRAFT), Bengaluru CAR (CONFIRMED)"
echo "  dave.assistant  : Sydney (CONFIRMED)"
echo "  eve.employee    : Singapore (CONFIRMED), Bengaluru (DRAFT)  [tenant-b]"
echo ""
echo -e "${BOLD}Expenses seeded:${NC}"
echo "  alice.employee  : London (APPROVED — self), Tokyo (DRAFT — self)"
echo "  bob.manager     : New York (SUBMITTED — self, pending approval)"
echo "  carol.executive : Berlin (APPROVED — self)"
echo "  carol via dave  : Shanghai (DRAFT — delegated, createdBy=dave.assistant)"
echo "  carol via dave  : Bengaluru (SUBMITTED — delegated, createdBy=dave.assistant)"
echo ""
echo -e "${BOLD}Delegation/Consent:${NC}"
echo "  carol.executive → dave.assistant  purpose=book_travel"
echo "  Delegation ID : $DELEGATION_ID"
echo "  Consent ID    : $CONSENT_ID"
echo ""
echo -e "${GREEN}${BOLD}Phase 0 seeding complete.${NC}"
echo ""
echo "Verification checklist:"
echo "  [ ] alice.employee → booking list shows 3 bookings across 3 destinations"
echo "  [ ] bob.manager → dashboard Pending Approvals shows alice's submitted expense"
echo "  [ ] dave.assistant → Delegations tab shows Carol's delegation; activate → booking list shows Carol's bookings"
echo ""

# Cleanup temp cookie jar
rm -f "$DAVE_COOKIE_JAR"
