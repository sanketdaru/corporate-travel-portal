# Authentication and Session Management

How a user's identity travels from the browser through Next.js, the Employee BFF and the API Gateway into the services. Delegation (Dave acting for Carol) builds on this; see [`journeys/DELEGATE-JOURNEY.md`](journeys/DELEGATE-JOURNEY.md).

| Component | Role |
|---|---|
| Keycloak (`:8080`, realm `corporate-travel`) | Issues access, refresh and ID tokens |
| Auth.js / next-auth 5 (`lib/auth/auth.ts`) | OIDC client running server-side in Next.js; JWT session cookie; token refresh |
| `proxy.ts` | Redirects unauthenticated requests for `/dashboard`, `/travel`, `/expense`, `/delegation`, `/admin`, `/approvals` to `/login` |
| `AppShell` + `lib/api/client.ts` | Copies `session.accessToken` into a module-level variable; `bffClient` and `gatewayClient` (Axios) attach it as `Authorization: Bearer` |
| Next.js rewrites (`next.config.ts`) | `/api/bff/*` → BFF `:8085`; `/api/bookings\|expenses\|delegations\|consents/*` → gateway `:8000`. Same origin, so no CORS and the BFF cookie round-trips |
| Employee BFF (`:8085`) | Resource server; keeps a server-side session (`JSESSIONID`) for delegation state; calls services directly |
| API Gateway (`:8000`) | Resource server; routes to travel, expense, delegation and consent services |
| `security-commons` | Shared `JwtAuthenticationConverter`, `DelegatedActorFilter`, `OpaClient` used by every service |

---

## Prerequisites

- `KEYCLOAK_CLIENT_ID=employee-portal`, `KEYCLOAK_ISSUER=http://localhost:8080/realms/corporate-travel`, `NEXTAUTH_URL` and `AUTH_SECRET` set for Next.js (see `frontend/nextjs/.env.example`).
- `employee-portal` is a public client: no secret, PKCE `S256`, direct access grants disabled.
- Demo users (`alice.employee`, `bob.manager`, `carol.executive`, `dave.assistant`, `eve.employee`) use password `password123` (`KC_DEMO_USER_PASSWORD`).

---

## 1. Login (Authorization Code + PKCE)

1. A protected route without a session is redirected by `proxy.ts` to `/login`, which calls `signIn("keycloak")`.
2. Auth.js (`/api/auth/*`, served by Next.js and never rewritten) redirects to Keycloak with `client_id=employee-portal`, `scope=openid`, `code_challenge_method=S256`.
3. Keycloak redirects back to `/api/auth/callback/keycloak`. Auth.js redeems the code server-side with the `code_verifier`.
4. The `jwt` callback stores `accessToken`, `refreshToken`, `idToken`, `expiresAt`; decodes `realm_access.roles` from the access token; takes `tenant_id` from the profile (default `tenant-a`); and sets `name` by title-casing `preferred_username` (`alice.employee` → `Alice Employee`).
5. Everything is stored in the encrypted, HttpOnly session cookie (`authjs.session-token`, `__Secure-` prefixed on HTTPS). There is no server-side session store in Next.js.

The client only requests `openid`, but Keycloak always adds the client's default scopes (`basic`, `profile`, `email`, `roles`, `web-origins`, `acr`, `user-attributes`).

### Access token contents

| Claim | Example | Source |
|---|---|---|
| `sub` | Keycloak UUID | `basic` scope |
| `preferred_username` | `alice.employee` | `profile` scope; the platform's user id |
| `name`, `given_name`, `family_name`, `email` | `Alice Employee`, `alice@tenant-a.com` | `profile`, `email` scopes |
| `tenant_id`, `employee_id` | `tenant-a`, `emp-001` | `user-attributes` scope (declared in the user profile) |
| `realm_access.roles` | `employee`, `default-roles-corporate-travel`, `offline_access`, `uma_authorization` | `roles` scope |
| `aud` | `employee-bff`, `travel-service`, `expense-service`, `delegation-service`, `consent-service`, `account` | audience mappers on `employee-portal` |

Lifetimes (realm): access token 300 s, SSO idle 30 min, SSO max 10 h.

### Session shape (`useSession()`)

```ts
{
  accessToken: string,            // Keycloak access token
  error?: "RefreshAccessTokenError",
  user: { id /* sub */, name, email, roles: string[], tenantId: string }
}
```

`refreshToken`, `idToken` and `expiresAt` stay inside the cookie and are not exposed to client code.

---

## 2. Calling the BFF

`bffClient` sends `Authorization: Bearer <access token>` and, with `withCredentials: true`, the `JSESSIONID` cookie. Next.js rewrites `/api/bff/:path*` to `http://localhost:8085/api/bff/:path*` with headers intact.

BFF `SecurityConfig`:

- JWT validation: issuer `corporate-travel`, `aud` must contain `employee-bff`.
- `SessionCreationPolicy.IF_REQUIRED`: a session is created only when the BFF stores something (active delegation context, pending delegation grant).
- `sessionFixation().none()`: JWT auth re-authenticates on every request; the default fixation strategy would rotate `JSESSIONID` each time and orphan the stored delegation context.
- CSRF disabled: the credential is the Bearer token; the cookie only points to server-side state.
- `/api/bff/delegation/grant/callback` is public (it is Keycloak's browser redirect); it is bound to the initiating session by the `state` parameter.

The BFF calls services directly (not through the gateway) with the user's own token, or with delegated tokens when delegation mode is active.

## 3. Calling the gateway

`gatewayClient` sends the same Bearer token to `/api/bookings|expenses|delegations|consents/*`; Next.js rewrites to `:8000`. No BFF session is involved. Used for delegation and consent management, approvals, booking audit and admin lists.

## 4. Token validation in services

Every resource server validates issuer, signature, expiry and audience (`spring.security.oauth2.resourceserver.jwt.audiences`):

| Server | Accepted `aud` |
|---|---|
| API Gateway | any of `travel-service`, `expense-service`, `delegation-service`, `consent-service` |
| Each service | its own client id |
| Employee BFF | `employee-bff` |

`JwtAuthenticationConverter` maps the token to:

- Spring authorities: `ROLE_<ROLE>` for each realm role. Delegated tokens (with an `act` claim) get **no** authorities.
- `SecurityContext` (used for OPA and audit): `userId` = `preferred_username` (fallback `sub`), `tenantId`, `roles`, attributes `employee_id` and `email`. For a non-delegated request `actorId == subjectId == userId`.

For delegated requests the context describes the actor, verified from `X-Actor-Token`; see the delegate journey. `X-Delegated-Subject` is not read anywhere.

## 5. Token refresh

The `jwt` callback runs on every server-side session read. If `now < expiresAt - 60 s` the token is returned unchanged; otherwise it posts `grant_type=refresh_token` with `client_id=employee-portal` (no secret). Keycloak rotates the refresh token; roles are re-read from the new access token.

On failure the token gets `error: "RefreshAccessTokenError"`. `proxy.ts` (server) and `AppShell` (client) both send the user to `/session-expired`, which calls `signOut` and returns to `/login` after 5 s.

## 6. Sign-out

`signOut()` clears the cookie. The `signOut` event then calls Keycloak's end-session endpoint with `id_token_hint`, ending the SSO session so the next login prompts for credentials.

---

## Error Scenarios

| Scenario | Result |
|---|---|
| Access token near expiry, refresh succeeds | Transparent |
| Refresh token expired or Keycloak unreachable | `RefreshAccessTokenError` → `/session-expired` → `/login` |
| Missing, invalid or expired Bearer token at BFF or gateway | 401; the Axios interceptor calls `signOut({ callbackUrl: "/login" })` |
| Token without the receiving server's audience | 401 |
| `JSESSIONID` missing or expired | New session; any active delegation context is gone and must be re-activated |
| Downstream 4xx (e.g. OPA deny 403) behind the BFF | Passed through with status and body |
| Downstream 5xx behind the BFF | 502 |

---

## curl Example

`employee-portal` does not allow the password grant. For scripting use the confidential `employee-bff` client (the same approach as `scripts/get-token.sh`); its tokens carry the same claims and audiences.

```bash
KC=http://localhost:8080/realms/corporate-travel/protocol/openid-connect

RESP=$(curl -s "$KC/token" -d grant_type=password -d scope=openid \
  -d client_id=employee-bff -d client_secret=bff-service-secret-change-in-production \
  -d username=alice.employee -d password=password123)
TOKEN=$(echo "$RESP" | jq -r .access_token)

# Inspect claims
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{sub, preferred_username, email, tenant_id, employee_id, aud, roles: .realm_access.roles}'

# BFF (keep the session cookie, as the browser does)
curl -s -c /tmp/bff.txt -b /tmp/bff.txt -H "Authorization: Bearer $TOKEN" \
  http://localhost:8085/api/bff/dashboard | jq '{bookings: (.bookings|length), expenses: (.expenses|length)}'

# Gateway
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/bookings | jq length

# Refresh
curl -s "$KC/token" -d grant_type=refresh_token \
  -d client_id=employee-bff -d client_secret=bff-service-secret-change-in-production \
  -d refresh_token="$(echo "$RESP" | jq -r .refresh_token)" | jq '{expires_in}'

# End the Keycloak session (200 = logged out)
curl -s -o /dev/null -w '%{http_code}\n' -G "$KC/logout" \
  --data-urlencode "id_token_hint=$(echo "$RESP" | jq -r .id_token)"
```
