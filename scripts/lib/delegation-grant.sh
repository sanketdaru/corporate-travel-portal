#!/usr/bin/env bash
# Shared helper (sourced): the delegator's Keycloak grant for a delegation, as a browser would do it
# (ADR-024). Requires curl.
#
#   bff_authorize_delegation <bff_url> <delegator_username> <delegator_password> <delegator_token> <delegation_id>
#
# 1. POST /api/bff/delegation/{id}/grant           -> Keycloak authorization URL (state kept in BFF session)
# 2. Keycloak login + consent screen ("Delegate token to administrator <delegate>")
# 3. Keycloak redirects to the registered callback (http://localhost:3000/... via Next.js); the request
#    is sent to the BFF directly with the same session cookie
# 4. The BFF redirects to the frontend with ?grant=success|error
#
# Prints the final redirect URL. Returns 0 only when it reports grant=success.

bff_authorize_delegation() {
  local bff="$1" user="$2" password="$3" token="$4" delegation_id="$5"
  local work bff_jar kc_jar start auth page action loc
  work=$(mktemp -d); bff_jar="$work/bff"; kc_jar="$work/kc"

  start=$(curl -s -X POST -c "$bff_jar" -b "$bff_jar" -H "Authorization: Bearer $token" \
    "$bff/api/bff/delegation/$delegation_id/grant")
  auth=$(printf '%s' "$start" | sed -n 's/.*"authorizationUrl":"\([^"]*\)".*/\1/p')
  if [[ -z "$auth" ]]; then echo "grant start failed: $start"; rm -rf "$work"; return 1; fi

  page=$(curl -s -c "$kc_jar" -b "$kc_jar" "$auth")
  action=$(printf '%s' "$page" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')
  curl -s -c "$kc_jar" -b "$kc_jar" -D "$work/h" -o /dev/null --data-urlencode "username=$user" \
    --data-urlencode "password=$password" "$action"
  loc=$(grep -i '^location:' "$work/h" | tr -d '\r' | sed 's/^[Ll]ocation: //')
  if [[ "$loc" == *"execution=OAUTH_GRANT"* ]]; then
    page=$(curl -s -c "$kc_jar" -b "$kc_jar" "$loc")
    action=$(printf '%s' "$page" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')
    [[ "$action" == /* ]] && action="$(printf '%s' "$auth" | sed -E 's#^(https?://[^/]+).*#\1#')$action"
    curl -s -c "$kc_jar" -b "$kc_jar" -D "$work/h" -o /dev/null -d accept=Yes "$action"
    loc=$(grep -i '^location:' "$work/h" | tr -d '\r' | sed 's/^[Ll]ocation: //')
  fi
  if [[ "$loc" != *"/api/bff/delegation/grant/callback"* ]]; then
    echo "no callback redirect from Keycloak: ${loc:0:200}"; rm -rf "$work"; return 1
  fi

  # Keycloak redirects to the frontend origin; Next.js would proxy this path to the BFF
  loc="$bff/api/bff/delegation/grant/callback?${loc#*\?}"
  curl -s -c "$bff_jar" -b "$bff_jar" -D "$work/h" -o /dev/null "$loc"
  loc=$(grep -i '^location:' "$work/h" | tr -d '\r' | sed 's/^[Ll]ocation: //')
  rm -rf "$work"
  echo "$loc"
  [[ "$loc" == *"grant=success"* ]]
}
