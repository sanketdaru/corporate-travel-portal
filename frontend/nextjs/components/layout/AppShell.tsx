"use client";

import { useEffect } from "react";
import { useSession } from "next-auth/react";
import { DelegationContextProvider, useDelegationContext } from "@/lib/context/DelegationContext";
import { TopNav } from "@/components/layout/TopNav";
import { Sidebar } from "@/components/layout/Sidebar";
import { DelegationBanner } from "@/components/layout/DelegationBanner";
import { UserSession } from "@/lib/types/auth";
import { setAccessToken } from "@/lib/api/client";

function AppShellInner({ children }: { children: React.ReactNode }) {
  const { data: session, status } = useSession();
  const { delegationActive } = useDelegationContext();

  // Sync access token into the Axios client as soon as the session is ready
  useEffect(() => {
    setAccessToken(session?.accessToken ?? null);
  }, [session?.accessToken]);

  // If the jwt callback could not refresh the Keycloak tokens, the session
  // carries error="RefreshAccessTokenError". Navigate to the session-expired
  // page, which clears the cookie and shows a countdown before returning to login.
  useEffect(() => {
    if (session?.error === "RefreshAccessTokenError") {
      window.location.href = "/session-expired";
    }
  }, [session?.error]);

  const mainTopClass = delegationActive ? "pt-24" : "pt-14";

  // Build a UserSession from the NextAuth session (or fall back to a skeleton
  // while the session is loading so the shell doesn't flash empty).
  const user: UserSession | undefined = session?.user
    ? {
        id: session.user.id,
        name: session.user.name ?? "",
        email: session.user.email ?? "",
        roles: session.user.roles,
        tenantId: session.user.tenantId,
        accessToken: session.accessToken,
      }
    : undefined;

  if (status === "loading") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="text-sm text-slate-400">Loading…</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <TopNav user={user} />
      <DelegationBanner />
      <Sidebar user={user} />
      <main
        className={`ml-64 ${mainTopClass} min-h-screen bg-slate-50 transition-all duration-200`}
      >
        <div className="px-8 py-6">{children}</div>
      </main>
    </div>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <DelegationContextProvider>
      <AppShellInner>{children}</AppShellInner>
    </DelegationContextProvider>
  );
}
