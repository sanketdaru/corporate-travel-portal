import { NextResponse } from "next/server";

const KEYCLOAK_URL    = process.env.KEYCLOAK_ADMIN_URL    ?? "http://localhost:8080";
const ADMIN_USER      = process.env.KEYCLOAK_ADMIN_USER   ?? "admin";
const ADMIN_PASSWORD  = process.env.KEYCLOAK_ADMIN_PASSWORD ?? "admin123";
const REALM           = process.env.KEYCLOAK_REALM        ?? "corporate-travel";

async function getAdminToken(): Promise<string> {
  const res = await fetch(
    `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id:  "admin-cli",
        username:   ADMIN_USER,
        password:   ADMIN_PASSWORD,
        grant_type: "password",
      }),
      cache: "no-store",
      signal: AbortSignal.timeout(4000),
    },
  );
  if (!res.ok) throw new Error(`Keycloak token error: ${res.status}`);
  const data = await res.json() as { access_token: string };
  return data.access_token;
}

export interface AdminStats {
  activeUsers: number | null;
  opaViolations: null;           // OPA decision log plugin not enabled
}

export async function GET() {
  try {
    const token = await getAdminToken();
    const res   = await fetch(
      `${KEYCLOAK_URL}/admin/realms/${REALM}/users/count`,
      {
        headers: { Authorization: `Bearer ${token}` },
        cache: "no-store",
        signal: AbortSignal.timeout(4000),
      },
    );
    const activeUsers: number = res.ok ? await res.json() : null;
    return NextResponse.json({ activeUsers, opaViolations: null } satisfies AdminStats);
  } catch {
    return NextResponse.json({ activeUsers: null, opaViolations: null } satisfies AdminStats);
  }
}
