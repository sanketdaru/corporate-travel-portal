#!/usr/bin/env bash
# =============================================================================
# Spike: Keycloak RFC 8693 delegation (token-exchange-delegation, preview)
# =============================================================================
#
# Question: can employee-bff obtain a token with sub=Carol and act.sub=Dave so
# downstream services see a real RFC 8693 delegation instead of the
# X-Delegated-Subject header model (ADR-004)?
#
# Runs entirely against a throwaway Keycloak (default :8090) built from the
# realm-as-code definition, plus the delegation-specific settings:
#   - features token-exchange-delegation,parameterized-scopes
#   - FGAP v2 enabled, Users/"delegate" permission for dave.assistant
#   - employee-portal "Consent required" (delegation scopes demand consent)
#
# Findings are recorded in docs/UPGRADE-PLAN.md (Phase 5) and ADR-004.
# Usage: ./scripts/spikes/keycloak-delegation-spike.sh
# Requires: podman (or docker), curl, jq, openssl
# =============================================================================

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KC_PORT="${KC_PORT:-8090}"
KC="http://localhost:$KC_PORT"
REALM=corporate-travel
KC_IMAGE="$(sed -n 's#^ *image: \(quay.io/keycloak/keycloak:.*\)#\1#p' "$ROOT/docker-compose.yml")"
KCC_IMAGE="$(sed -n 's#^ *image: \(.*keycloak-config-cli:.*\)#\1#p' "$ROOT/docker-compose.yml")"
BFF_SECRET=bff-service-secret-change-in-production
CONTAINER="ctp-delegation-spike-$$"
REDIRECT=http://localhost:3000/api/auth/callback/keycloak
if command -v podman >/dev/null 2>&1; then CTR=podman; HOST_ALIAS=host.containers.internal; else CTR=docker; HOST_ALIAS=host.docker.internal; fi

WORK=$(mktemp -d); trap '[[ -n "${KEEP:-}" ]] || $CTR rm -f "$CONTAINER" >/dev/null 2>&1; rm -rf "$WORK"' EXIT
step()   { echo; echo "== $*"; }
result() { echo "   -> $*"; }
decode() { cut -d. -f2 | tr '_-' '/+' | awk '{l=length($0)%4; if(l==2)$0=$0"=="; else if(l==3)$0=$0"="; print}' | { base64 -d 2>/dev/null || base64 -D; }; }
admin()  { curl -s -d client_id=admin-cli -d username=admin -d password=admin123 -d grant_type=password "$KC/realms/master/protocol/openid-connect/token" | jq -r .access_token; }
api()    { local m="$1" p="$2"; shift 2; curl -s -X "$m" -H "Authorization: Bearer $(admin)" -H 'Content-Type: application/json' "$KC/admin/realms/$REALM$p" "$@"; }

# PKCE login as $1 on employee-portal with scope $2; approves the consent screen if shown.
# Prints "<access_token> <refresh_token>".
portal_login() {
  local user="$1" scope="$2" jar="$WORK/jar-$1" verifier challenge page action loc code
  rm -f "$jar"
  verifier=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
  challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
  page=$(curl -s -c "$jar" -b "$jar" -D "$WORK/h0" -G "$KC/realms/$REALM/protocol/openid-connect/auth" \
    --data-urlencode client_id=employee-portal --data-urlencode response_type=code --data-urlencode "scope=$scope" \
    --data-urlencode "redirect_uri=$REDIRECT" --data-urlencode "code_challenge=$challenge" \
    --data-urlencode code_challenge_method=S256 --data-urlencode state=s)
  action=$(printf '%s' "$page" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')
  if [[ -z "$action" ]]; then
    echo "   (no login form for $user / '$scope': $(head -1 "$WORK/h0" | tr -d '\r') $(grep -i '^location:' "$WORK/h0" | tr -d '\r' | sed 's/.*[?&]\(error=[^&]*\).*\(error_description=[^&]*\).*/\1 \2/'))" >&2
    echo "- -"; return
  fi
  page=$(curl -s -c "$jar" -b "$jar" -D "$WORK/h" -d "username=$user" -d password=password123 "$action")
  loc=$(grep -i '^location:' "$WORK/h" | tr -d '\r' | sed 's/^[Ll]ocation: //')
  if [[ "$loc" == *"execution=OAUTH_GRANT"* ]]; then   # consent screen (required action)
    page=$(curl -s -c "$jar" -b "$jar" "$loc")
    echo "   (consent screen for $user lists: $(printf '%s' "$page" | sed 's/<[^>]*>/ /g' | tr -s ' \n' ' ' | grep -oiE 'delegat[^.]{0,80}' | head -1))" >&2
    action=$(printf '%s' "$page" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')
    [[ "$action" == /* ]] && action="$KC$action"
    [[ -n "${DEBUG:-}" ]] && { echo "   [debug] consent action=$action" >&2; printf '%s' "$page" | grep -oE '<(input|button)[^>]*>' >&2; }
    curl -s -c "$jar" -b "$jar" -D "$WORK/h" -o /dev/null -d accept=Yes "$action"
    loc=$(grep -i '^location:' "$WORK/h" | tr -d '\r' | sed 's/^[Ll]ocation: //')
  fi
  code=$(printf '%s' "$loc" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
  if [[ -z "$code" ]]; then
    echo "   (no code for $user: status=$(head -1 "$WORK/h" | tr -d '\r') location=${loc:0:160})" >&2
    echo "- -"; return
  fi
  local tokens
  tokens=$(curl -s -d grant_type=authorization_code -d client_id=employee-portal -d "code=$code" \
    -d "redirect_uri=$REDIRECT" -d "code_verifier=$verifier" "$KC/realms/$REALM/protocol/openid-connect/token")
  if [[ "$(printf '%s' "$tokens" | jq -r 'has("access_token")' 2>/dev/null)" != "true" ]]; then
    echo "   (token request failed for $user: ${tokens:0:200})" >&2
    echo "- -"; return
  fi
  printf '%s' "$tokens" | jq -r '"\(.access_token) \(.refresh_token)"'
}

exchange() {   # exchange <subject> <actor|-> [audience]
  local args=(-d grant_type=urn:ietf:params:oauth:grant-type:token-exchange -d client_id=employee-bff -d "client_secret=$BFF_SECRET"
              -d "subject_token=$1" -d subject_token_type=urn:ietf:params:oauth:token-type:access_token)
  [[ "$2" != "-" ]] && args+=(-d "actor_token=$2" -d actor_token_type=urn:ietf:params:oauth:token-type:access_token)
  [[ -n "${3:-}" ]] && args+=(-d "audience=$3")
  curl -s "${args[@]}" "$KC/realms/$REALM/protocol/openid-connect/token"
}

# -----------------------------------------------------------------------------
step "Start $KC_IMAGE with token-exchange-delegation,parameterized-scopes on :$KC_PORT"
$CTR run -d --name "$CONTAINER" -p "$KC_PORT:8080" -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin123 \
  "$KC_IMAGE" start-dev --features=token-exchange-delegation,parameterized-scopes >/dev/null
for _ in $(seq 1 90); do curl -sf -o /dev/null "$KC/realms/master" && break; sleep 2; done

step "Apply realm-as-code definition ($KCC_IMAGE)"
$CTR run --rm $([[ $CTR == docker ]] && echo --add-host=host.docker.internal:host-gateway) \
  -e KEYCLOAK_URL="http://$HOST_ALIAS:$KC_PORT" -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin123 \
  -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true -e IMPORT_FILES_LOCATIONS='/config/*.json' -e IMPORT_VARSUBSTITUTION_ENABLED=true \
  -e KC_FRONTEND_URL="$KC" -e KC_DEMO_USER_PASSWORD=password123 \
  -e KC_CLIENT_SECRET_APPROVAL_SERVICE=x -e KC_CLIENT_SECRET_CONSENT_SERVICE=x -e KC_CLIENT_SECRET_DELEGATION_SERVICE=x \
  -e KC_CLIENT_SECRET_EMPLOYEE_BFF="$BFF_SECRET" -e KC_CLIENT_SECRET_EXPENSE_SERVICE=x -e KC_CLIENT_SECRET_TRAVEL_SERVICE=x \
  -v "$ROOT/infrastructure/keycloak/config:/config:ro" "$KCC_IMAGE" > "$WORK/kcc.log" 2>&1 \
  && result "applied" || { result "config-cli failed"; tail -5 "$WORK/kcc.log"; exit 1; }
result "delegation scopes present: $(api GET /client-scopes | jq -c '[.[].name | select(startswith("delegation"))]')"

step "Delegation prerequisites: FGAP v2, portal consent, delegate permission for dave.assistant"
api PUT "" -d '{"adminPermissionsEnabled": true}' -o /dev/null -w '   realm adminPermissionsEnabled: HTTP %{http_code}\n'
PORTAL_ID=$(api GET "/clients?clientId=employee-portal" | jq -r '.[0].id')
api GET "/clients/$PORTAL_ID" | jq '.consentRequired = true' | api PUT "/clients/$PORTAL_ID" -d @- -o /dev/null -w '   employee-portal consentRequired: HTTP %{http_code}\n'
# Keycloak adds delegation:* as realm-default optional scopes, but clients whose scope list is managed
# explicitly (realm-as-code) do not inherit them — attach to the client that performs the login
DS_ID=$(api GET /client-scopes | jq -r '.[] | select(.name=="delegation:user") | .id')
api PUT "/clients/$PORTAL_ID/optional-client-scopes/$DS_ID" -o /dev/null -w '   employee-portal optional scope delegation:user: HTTP %{http_code}\n'
AP_ID=$(api GET "/clients?clientId=admin-permissions" | jq -r '.[0].id')
DAVE_ID=$(api GET "/users?username=dave.assistant&exact=true" | jq -r '.[0].id')
CAROL_ID=$(api GET "/users?username=carol.executive&exact=true" | jq -r '.[0].id')
POLICY_ID=$(api POST "/clients/$AP_ID/authz/resource-server/policy/user" -d "{\"name\":\"dave-may-delegate\",\"users\":[\"$DAVE_ID\"]}" | jq -r .id)
api POST "/clients/$AP_ID/authz/resource-server/permission/scope" \
  -d "{\"name\":\"dave-delegate-for-carol\",\"resourceType\":\"Users\",\"resources\":[\"$CAROL_ID\"],\"scopes\":[\"delegate\"],\"policies\":[\"$POLICY_ID\"]}" \
  -o /dev/null -w '   permission Users/delegate (carol -> dave): HTTP %{http_code}\n'
result "dave=$DAVE_ID carol=$CAROL_ID"

# -----------------------------------------------------------------------------
step "Q1: Carol logs in requesting delegation:user:dave.assistant — does her token carry may_act?"
read -r CAROL_AT CAROL_RT <<<"$(portal_login carol.executive 'openid delegation:user:dave.assistant')"
result "Carol token: $(printf '%s' "$CAROL_AT" | decode | jq -c '{sub, preferred_username, may_act, scope, aud}')"

step "Dave obtains his own token (employee-bff, same as the BFF sees)"
DAVE_AT=$(curl -s -d client_id=employee-bff -d "client_secret=$BFF_SECRET" -d username=dave.assistant -d password=password123 \
  -d grant_type=password -d scope=openid "$KC/realms/$REALM/protocol/openid-connect/token" | jq -r .access_token)
result "Dave token: $(printf '%s' "$DAVE_AT" | decode | jq -c '{sub, preferred_username, azp}')"

step "Q2: employee-bff exchanges subject_token=Carol + actor_token=Dave"
R=$(exchange "$CAROL_AT" "$DAVE_AT"); T=$(printf '%s' "$R" | jq -r '.access_token // empty')
if [[ -n "$T" ]]; then result "issued: $(printf '%s' "$T" | decode | jq -c '{sub, preferred_username, act, aud, azp, sid, exp_in: (.exp - .iat)}') refresh_token=$(printf '%s' "$R" | jq -r 'has("refresh_token")')"
else result "FAILED: $R"; fi

step "Q3: same exchange with audience=travel-service — is the delegated token audience-scoped?"
R=$(exchange "$CAROL_AT" "$DAVE_AT" travel-service); T=$(printf '%s' "$R" | jq -r '.access_token // empty')
[[ -n "$T" ]] && result "aud=$(printf '%s' "$T" | decode | jq -c .aud)" || result "FAILED: $R"

step "Q4: Standard exchange (no actor_token) with Carol's may_act token"
result "$(exchange "$CAROL_AT" - | jq -c '{error, error_description, has_token: has("access_token")}')"

step "Q5: Carol logs in WITHOUT the delegation scope — any may_act?"
read -r PLAIN_AT _ <<<"$(portal_login carol.executive 'openid')"
result "may_act=$(printf '%s' "$PLAIN_AT" | decode | jq -c .may_act)"

step "Q6: Carol's refreshed token — does may_act survive a refresh?"
REFRESHED=$(curl -s -d grant_type=refresh_token -d client_id=employee-portal -d "refresh_token=$CAROL_RT" "$KC/realms/$REALM/protocol/openid-connect/token" | jq -r '.access_token // empty')
[[ -n "$REFRESHED" ]] && result "may_act after refresh=$(printf '%s' "$REFRESHED" | decode | jq -c .may_act)" || result "refresh failed"

step "Q7: Carol's sessions end (logout) — can her still-unexpired token be exchanged?"
api POST "/users/$CAROL_ID/logout" -o /dev/null -w '   logout carol: HTTP %{http_code}\n'
result "$(exchange "$CAROL_AT" "$DAVE_AT" | jq -c '{error, error_description, has_token: has("access_token")}')"

step "Q8: Without the FGAP delegate permission — is the scope honoured?"
api GET "/users?username=alice.employee&exact=true" >/dev/null
read -r ALICE_AT _ <<<"$(portal_login alice.employee 'openid delegation:user:dave.assistant')"
result "alice may_act=$(printf '%s' "$ALICE_AT" | decode | jq -c .may_act) scope=$(printf '%s' "$ALICE_AT" | decode | jq -r .scope)"

step "Q9: Offline token — does delegation outlive Carol's SSO session (our delegations last days)?"
OA_ID=$(api GET /client-scopes | jq -r '.[] | select(.name=="offline_access") | .id')
api PUT "/clients/$PORTAL_ID/optional-client-scopes/$OA_ID" -o /dev/null -w '   employee-portal optional scope offline_access: HTTP %{http_code}\n'
read -r OFF_AT OFF_RT <<<"$(portal_login carol.executive 'openid offline_access delegation:user:dave.assistant')"
result "offline login: refresh token typ=$(printf '%s' "$OFF_RT" | decode | jq -r .typ 2>/dev/null) may_act=$(printf '%s' "$OFF_AT" | decode | jq -c .may_act 2>/dev/null)"
api POST "/users/$CAROL_ID/logout" -o /dev/null -w '   logout carol (ends SSO sessions): HTTP %{http_code}\n'
OFF_RESP=$(curl -s -d grant_type=refresh_token -d client_id=employee-portal -d "refresh_token=$OFF_RT" "$KC/realms/$REALM/protocol/openid-connect/token")
OFF_NEW=$(printf '%s' "$OFF_RESP" | jq -r '.access_token // empty')
if [[ -n "$OFF_NEW" ]]; then
  result "offline refresh after logout: may_act=$(printf '%s' "$OFF_NEW" | decode | jq -c .may_act)"
  result "exchange with offline-derived token: $(exchange "$OFF_NEW" "$DAVE_AT" travel-service | jq -c '{error, error_description, sub_is_carol: ((.access_token // "") | length > 0)}')"
else
  result "offline refresh after logout failed: $OFF_RESP"
fi

echo; echo "Spike complete."
