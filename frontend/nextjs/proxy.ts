import { auth } from "@/lib/auth/auth";
import { NextResponse } from "next/server";

// Next.js 16 uses "proxy.ts" (renamed from "middleware.ts").
// next-auth v5's `auth` function can wrap the proxy handler so that
// req.auth is populated with the current session.
export default auth((req) => {
  const { pathname } = req.nextUrl;

  // No session cookie at all → redirect to login
  if (!req.auth) {
    const loginUrl = new URL("/login", req.url);
    loginUrl.searchParams.set("callbackUrl", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Session cookie exists but the underlying Keycloak tokens have expired and
  // the refresh token could not renew them (e.g. user was idle > 30 min).
  // The jwt callback sets this flag; force a full sign-out here so the stale
  // NextAuth cookie is cleared before redirecting to login.
  if (req.auth.error === "RefreshAccessTokenError") {
    return NextResponse.redirect(new URL("/session-expired", req.url));
  }

  return NextResponse.next();
});

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/travel/:path*",
    "/expense/:path*",
    "/delegation/:path*",
    "/admin/:path*",
    "/approvals/:path*",
  ],
};
