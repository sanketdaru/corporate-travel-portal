#!/bin/bash
# =============================================================================
# FGAP v2 delegation permissions (ADR-024)
# =============================================================================
#
# keycloak-config-cli cannot manage the system-managed admin-permissions client,
# so this one-shot step creates the permissions with kcadm.sh (shipped in the
# Keycloak image). Idempotent: objects are looked up by name and only created
# when missing.
#
# Model: within each tenant group, any member may be named as a delegate by any
# other member (Groups resource type, "delegate-members" scope). Keycloak
# enforces the tenant boundary; delegation-service and consent-service decide
# the specific delegator/delegate pair, purpose and validity window.
#
# Env: KEYCLOAK_URL, KEYCLOAK_USER, KEYCLOAK_PASSWORD, REALM, TENANT_GROUPS
# =============================================================================
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
REALM="${REALM:-corporate-travel}"
TENANT_GROUPS="${TENANT_GROUPS:-TenantA TenantB}"

for _ in $(seq 1 60); do
  $KCADM config credentials --server "$KEYCLOAK_URL" --realm master \
    --user "$KEYCLOAK_USER" --password "$KEYCLOAK_PASSWORD" >/dev/null 2>&1 && break
  sleep 2
done

# id_of <path> <name> — exact-name lookup of an object id under <path>.
# Pure bash: the Keycloak image (UBI micro) has no awk/grep/jq.
id_of() {
  local id name
  while IFS=, read -r id name; do
    if [[ "$name" == "$2" ]]; then echo "$id"; return; fi
  done < <($KCADM get "$1" -r "$REALM" --fields id,name --format csv --noquotes 2>/dev/null)
}

AP=$($KCADM get clients -r "$REALM" -q clientId=admin-permissions --fields id --format csv --noquotes)
if [[ -z "$AP" ]]; then
  echo "admin-permissions client not found — is adminPermissionsEnabled set on realm $REALM?" >&2
  exit 1
fi
RS="clients/$AP/authz/resource-server"

for GROUP in $TENANT_GROUPS; do
  GID=$(id_of groups "$GROUP")
  [[ -n "$GID" ]] || { echo "group $GROUP not found" >&2; exit 1; }

  POLICY="$GROUP-members-may-act"
  PID=$(id_of "$RS/policy" "$POLICY")
  if [[ -z "$PID" ]]; then
    $KCADM create "$RS/policy/group" -r "$REALM" -s name="$POLICY" \
      -s description="Actors: members of $GROUP" -s "groups=[{\"id\":\"$GID\",\"extendChildren\":false}]" >/dev/null
    PID=$(id_of "$RS/policy" "$POLICY")
    echo "created policy $POLICY"
  else
    echo "policy $POLICY exists"
  fi

  PERMISSION="$GROUP-delegation"
  if [[ -z "$(id_of "$RS/permission" "$PERMISSION")" ]]; then
    $KCADM create "$RS/permission/scope" -r "$REALM" -s name="$PERMISSION" \
      -s description="Members of $GROUP may delegate to other members of $GROUP" \
      -s resourceType=Groups -s "resources=[\"$GID\"]" -s 'scopes=["delegate-members"]' \
      -s "policies=[\"$PID\"]" >/dev/null
    echo "created permission $PERMISSION"
  else
    echo "permission $PERMISSION exists"
  fi
done
