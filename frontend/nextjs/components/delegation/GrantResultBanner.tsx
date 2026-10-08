"use client";

import { useSearchParams } from "next/navigation";

const REASONS: Record<string, string> = {
  delegation_not_permitted: "Keycloak did not permit this delegation (the delegate must be in your tenant).",
  wrong_account: "You signed in to Keycloak as a different user than the delegator.",
  access_denied: "You declined the delegation on Keycloak's consent screen.",
  unknown_or_expired_request: "The authorization request expired. Please authorize the delegation again.",
};

// Outcome of the delegator's Keycloak grant, reported by the BFF as ?grant=success|error&reason=…
// Rendered inside a <Suspense> boundary because it reads search params.
export function GrantResultBanner() {
  const params = useSearchParams();
  const outcome = params.get("grant");
  if (!outcome) return null;

  if (outcome === "success") {
    return (
      <div className="bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3 text-xs text-emerald-800">
        Delegation authorized in Keycloak. Your delegate can now activate it.
      </div>
    );
  }

  const reason = params.get("reason") ?? "";
  return (
    <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 text-xs text-red-700">
      Keycloak authorization did not complete. {REASONS[reason] ?? `(${reason || "unknown error"})`}
    </div>
  );
}
