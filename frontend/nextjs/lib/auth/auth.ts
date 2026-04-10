import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";
import type { JWT } from "next-auth/jwt";

/**
 * Attempts to obtain a new access token from Keycloak using the stored
 * refresh token. employee-portal is a public client (no secret, PKCE flow).
 *
 * Returns updated token fields on success, or throws on failure so the
 * caller can set error="RefreshAccessTokenError".
 */
async function refreshAccessToken(token: JWT): Promise<JWT> {
  const issuer = process.env.KEYCLOAK_ISSUER!;
  const tokenEndpoint = `${issuer}/protocol/openid-connect/token`;

  const params = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: process.env.KEYCLOAK_CLIENT_ID!,
    refresh_token: token.refreshToken ?? "",
  });

  const response = await fetch(tokenEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params.toString(),
  });

  if (!response.ok) {
    throw new Error(`Token refresh failed: ${response.status}`);
  }

  const refreshed = await response.json() as {
    access_token: string;
    refresh_token?: string;
    expires_in: number;
    id_token?: string;
  };

  // Decode roles from the new access token
  let roles: string[] = token.roles ?? [];
  try {
    const payload = JSON.parse(
      Buffer.from(refreshed.access_token.split(".")[1], "base64").toString()
    ) as Record<string, unknown>;
    const realmAccess = payload.realm_access as { roles?: string[] } | undefined;
    roles = realmAccess?.roles ?? roles;
  } catch {
    // keep existing roles if decode fails
  }

  return {
    ...token,
    accessToken: refreshed.access_token,
    // Keycloak rotates the refresh token on each use
    refreshToken: refreshed.refresh_token ?? token.refreshToken,
    idToken: refreshed.id_token ?? token.idToken,
    // expires_in is seconds from now
    expiresAt: Math.floor(Date.now() / 1000) + refreshed.expires_in,
    roles,
    error: undefined,
  };
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      // No clientSecret — employee-portal is a public client (PKCE flow)
      issuer: process.env.KEYCLOAK_ISSUER!,
      // The realm has no `profile` or `email` scopes — only request `openid`.
      // Keycloak automatically includes the `user-attributes` default scope
      // which provides preferred_username, realm_access.roles, and tenant_id.
      authorization: { params: { scope: "openid" } },
    }),
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      // ── Initial sign-in: populate token from the OAuth response ──────────
      if (account) {
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
        token.idToken = account.id_token;
        token.expiresAt = account.expires_at;

        // realm_access.roles is only in the access token (id_token.claim not set
        // on the realm-roles mapper in the current Keycloak instance). Decode it
        // here so we don't depend on the ID token carrying roles.
        if (account.access_token) {
          try {
            const payload = JSON.parse(
              Buffer.from(account.access_token.split(".")[1], "base64").toString()
            ) as Record<string, unknown>;
            const realmAccess = payload.realm_access as { roles?: string[] } | undefined;
            token.roles = realmAccess?.roles ?? [];
          } catch {
            token.roles = [];
          }
        }
      }
      if (profile) {
        const p = profile as Record<string, unknown>;
        token.tenantId = (p.tenant_id as string) ?? "tenant-a";
        // No `profile` scope in this realm — derive display name from preferred_username
        // e.g. "alice.employee" → "Alice Employee"
        const username = (p.preferred_username as string) ?? "";
        token.name = username
          .split(".")
          .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
          .join(" ");
      }

      // ── Subsequent calls: check expiry and refresh if needed ─────────────
      // expiresAt is a Unix timestamp in seconds. Refresh 60 s early to avoid
      // using a token that expires mid-request.
      const expiresAt = token.expiresAt ?? 0;
      if (Date.now() < (expiresAt - 60) * 1000) {
        // Token is still valid — return unchanged
        return token;
      }

      // Access token has expired (or will within 60 s). Attempt refresh.
      try {
        return await refreshAccessToken(token);
      } catch {
        // Refresh token is also expired or Keycloak is unreachable.
        // Signal the session layer so the proxy and client can force logout.
        return { ...token, error: "RefreshAccessTokenError" as const };
      }
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken as string;
      session.user.id = (token.sub as string) ?? "";
      session.user.roles = (token.roles as string[]) ?? [];
      session.user.tenantId = (token.tenantId as string) ?? "tenant-a";
      // Propagate the error flag so the client and proxy can react
      if (token.error) {
        session.error = token.error;
      }
      return session;
    },
  },
  events: {
    // On sign-out, also end the Keycloak session so the user is fully logged out
    // from the IdP and won't be silently re-authenticated on next login.
    async signOut(message) {
      const idToken = (message as { token?: { idToken?: string } }).token?.idToken;
      if (idToken && process.env.KEYCLOAK_ISSUER) {
        const params = new URLSearchParams({
          id_token_hint: idToken,
          post_logout_redirect_uri: process.env.NEXTAUTH_URL ?? "http://localhost:3000",
        });
        const logoutUrl = `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/logout?${params}`;
        // Fire-and-forget — Keycloak invalidates the SSO session server-side
        await fetch(logoutUrl).catch(() => {});
      }
    },
  },
});
