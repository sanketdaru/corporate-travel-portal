"use client";

import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import { useSession } from "next-auth/react";
import { setAccessToken } from "@/lib/api/client";
import { activateDelegation } from "@/lib/api/bff";
import {
  getMyDelegations,
  getDelegationsToMe,
  getMyConsents,
  revokeDelegation,
  revokeConsent,
  delegationStatus,
  type Delegation,
  type Consent,
} from "@/lib/api/gateway";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { GrantDelegationModal } from "@/components/delegation/GrantDelegationModal";

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDisplayName(userId: string): string {
  return userId
    .split(".")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function getInitials(name: string): string {
  return name.split(" ").map((w) => w[0]).join("").toUpperCase().slice(0, 2);
}

function formatDate(iso: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
  });
}

function formatDateTime(iso: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function ExpiryCell({ expiresAt, status }: { expiresAt?: string | null; status: string }) {
  if (!expiresAt) return <span className="text-xs text-slate-400">No expiry</span>;
  const cls = status === "ACTIVE" ? "text-xs font-medium text-amber-700" : "text-xs text-slate-400";
  return <span className={cls}>{formatDateTime(expiresAt)}</span>;
}

function Avatar({ name, className = "" }: { name: string; className?: string }) {
  return (
    <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-semibold flex-shrink-0 ${className}`}>
      {getInitials(name)}
    </div>
  );
}

function SkeletonRows({ cols }: { cols: number }) {
  return (
    <>
      {Array.from({ length: 3 }).map((_, i) => (
        <tr key={i}>
          {Array.from({ length: cols }).map((_, j) => (
            <td key={j} className="px-5 py-3.5">
              <div className="h-4 bg-slate-100 rounded animate-pulse w-3/4" />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

function EmptyTableRow({ colSpan, message, sub }: { colSpan: number; message: string; sub?: string }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-5 py-10 text-center">
        <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center mx-auto mb-3">
          <svg className="w-5 h-5 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
        </div>
        <p className="text-sm text-slate-500">{message}</p>
        {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
      </td>
    </tr>
  );
}

// Looks up the active consent for a delegation (by grantee + purpose).
// Since the system enforces exactly one delegation per user pair, and one
// consent per grantor+grantee+purpose, this is a 1-to-1 lookup in practice.
function findConsent(d: Delegation, consents: Consent[]): Consent | undefined {
  return consents.find(
    (c) => c.granteeId === d.delegateId && c.purpose === d.purpose && c.status === "ACTIVE"
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function DelegationPage() {
  const { data: session } = useSession();
  const { refreshContext } = useDelegationContext();

  const [myDelegations,   setMyDelegations]   = useState<Delegation[]>([]);
  const [toMeDelegations, setToMeDelegations] = useState<Delegation[]>([]);
  const [consents,        setConsents]        = useState<Consent[]>([]);
  const [loading,         setLoading]         = useState(true);
  const [delegationsError, setDelegationsError] = useState(false);
  const [consentsError,    setConsentsError]    = useState(false);

  const [grantModalOpen, setGrantModalOpen] = useState(false);
  const [revokingId,     setRevokingId]     = useState<string | null>(null);
  const [activatingId,   setActivatingId]   = useState<string | null>(null);
  const [activationError, setActivationError] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setDelegationsError(false);
    setConsentsError(false);

    const [mineResult, toMeResult, consentsResult] = await Promise.allSettled([
      getMyDelegations(),
      getDelegationsToMe(),
      getMyConsents(),
    ]);

    if (mineResult.status === "fulfilled") setMyDelegations(mineResult.value);
    else setDelegationsError(true);

    if (toMeResult.status === "fulfilled") setToMeDelegations(toMeResult.value);
    else setDelegationsError(true);

    if (consentsResult.status === "fulfilled") setConsents(consentsResult.value);
    else {
      setConsentsError(true);
      console.error("[DelegationPage] getMyConsents failed:", (consentsResult as PromiseRejectedResult).reason);
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);
    load();
  }, [session?.accessToken, load]);

  // Revoke a delegation and cascade-revoke its paired consent.
  // The cascade uses granteeId+purpose (not delegationId) so it works even for
  // seeded consents where delegationId is null. Since the system allows only one
  // active delegation per user pair, there is exactly one consent to revoke.
  async function handleRevoke(delegation: Delegation) {
    setRevokingId(delegation.id);
    try {
      await revokeDelegation(delegation.id);

      // Cascade: find the active consent for this grantee+purpose and revoke it.
      // This ensures a clean state so re-granting later will succeed without
      // hitting the "active consent already exists" guard on the consent service.
      const paired = consents.find(
        (c) =>
          c.granteeId === delegation.delegateId &&
          c.purpose === delegation.purpose &&
          c.status === "ACTIVE"
      );
      if (paired) {
        await revokeConsent(paired.id);
      }

      await load();
    } catch {
      await load(); // refresh regardless so UI reflects actual state
    } finally {
      setRevokingId(null);
    }
  }

  async function handleActivate(delegationId: string) {
    setActivatingId(delegationId);
    setActivationError((prev) => ({ ...prev, [delegationId]: "" }));
    try {
      await activateDelegation(delegationId);
      await refreshContext();
    } catch (err) {
      // Extract the most useful error detail from the BFF response.
      // BFF wraps all errors as { message: string } in the response body.
      let message = "Activation failed — check the browser console for details.";
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as { message?: string; error?: string } | undefined;
        message = data?.message ?? data?.error ?? `HTTP ${err.response?.status ?? "error"}: activation request failed.`;
      }
      setActivationError((prev) => ({ ...prev, [delegationId]: message }));
    } finally {
      setActivatingId(null);
    }
  }

  const myActiveCount   = myDelegations.filter((d) => delegationStatus(d) === "ACTIVE").length;
  const toMeActiveCount = toMeDelegations.filter((d) => delegationStatus(d) === "ACTIVE").length;

  return (
    <>
      <div className="space-y-6">

        {/* Page header */}
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-xl font-semibold text-slate-900">Delegations</h1>
            <p className="text-sm text-slate-500 mt-0.5">
              Manage who can act on your behalf, and delegations granted to you
            </p>
          </div>
          <button
            onClick={() => setGrantModalOpen(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-4 py-2.5 rounded-lg transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Grant Delegation
          </button>
        </div>

        {/* Error banners */}
        {delegationsError && (
          <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 text-xs text-red-700 flex items-center gap-2">
            <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Could not load delegation records — the delegation service may be unavailable.
          </div>
        )}
        {consentsError && (
          <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 text-xs text-amber-800 flex items-center gap-2">
            <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Could not load consent records — the Consent column below may be incomplete.
          </div>
        )}

        {/* How it works */}
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex items-start gap-3">
          <svg className="w-4 h-4 text-blue-500 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div>
            <p className="text-sm font-medium text-blue-900 mb-0.5">Delegation + Consent are a paired bundle</p>
            <p className="text-xs text-blue-700 leading-relaxed">
              You can have <strong>one delegation per colleague per purpose</strong>. Granting a delegation
              automatically creates a paired consent record. Revoking the delegation revokes its consent
              in the same operation — cleaning the slate so you can re-grant later.
              An <strong>expired</strong> delegation must also be explicitly revoked before a new one can be granted.
            </p>
          </div>
        </div>

        {/* ── Section 1: Granted by Me ─────────────────────────────────────── */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Granted by Me</h2>
              <span className="text-xs text-slate-400">— I am the subject; others act on my behalf</span>
            </div>
            {!loading && (
              <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
                myActiveCount > 0
                  ? "text-amber-700 bg-amber-50 border border-amber-200"
                  : "text-slate-500 bg-slate-100"
              }`}>
                <span className={`w-1.5 h-1.5 rounded-full ${myActiveCount > 0 ? "bg-amber-400" : "bg-slate-400"}`} />
                {myActiveCount} active
              </span>
            )}
          </div>

          <table className="w-full text-sm" aria-label="Delegations granted by me">
            <thead className="bg-slate-50 text-xs text-slate-400 uppercase tracking-wide">
              <tr>
                {["ID", "Delegate (Actor)", "Purpose", "Scopes", "Granted", "Expires", "Delegation", "Consent", ""].map((h) => (
                  <th key={h} className="px-5 py-3 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <SkeletonRows cols={9} />
              ) : myDelegations.length === 0 ? (
                <EmptyTableRow
                  colSpan={9}
                  message="You haven't granted any delegations yet."
                  sub="Use 'Grant Delegation' to allow a colleague to act on your behalf."
                />
              ) : (
                myDelegations.map((d) => {
                  const delegateName = formatDisplayName(d.delegateId);
                  const status = delegationStatus(d);
                  const isInactive = status !== "ACTIVE";
                  const pairedConsent = findConsent(d, consents);
                  // Show Revoke for ACTIVE and EXPIRED — both need cleanup before re-granting.
                  // REVOKED delegations are terminal; nothing further to do.
                  const canRevoke = status !== "REVOKED";

                  return (
                    <tr
                      key={d.id}
                      className={`transition-colors ${
                        isInactive ? "opacity-60 hover:opacity-80" : "bg-amber-50/40 hover:bg-amber-50/60"
                      }`}
                    >
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-700">
                        {d.id.slice(0, 14).toUpperCase()}
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <Avatar name={delegateName} className="bg-violet-100 text-violet-700" />
                          <div>
                            <p className="text-sm font-medium text-slate-800">{delegateName}</p>
                            <p className="text-xs text-slate-400">{d.delegateId}@acme-corp</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <span className={`font-mono text-xs px-1.5 py-0.5 rounded-md border ${
                          isInactive
                            ? "bg-slate-50 text-slate-500 border-slate-200"
                            : "bg-amber-50 text-amber-700 border-amber-200"
                        }`}>
                          {d.purpose}
                        </span>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="flex flex-col gap-1">
                          {(d.scopes ?? []).map((s) => (
                            <span key={s} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md w-fit">
                              {s}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-500">{formatDate(d.createdAt)}</td>
                      <td className="px-5 py-3.5">
                        <ExpiryCell expiresAt={d.expiresAt} status={status} />
                      </td>

                      {/* Delegation status */}
                      <td className="px-5 py-3.5">
                        <StatusBadge status={status} />
                      </td>

                      {/* Paired consent status — inline so relationship is obvious */}
                      <td className="px-5 py-3.5">
                        {consentsError ? (
                          <span className="text-xs text-slate-400 italic">unavailable</span>
                        ) : pairedConsent ? (
                          <StatusBadge status={pairedConsent.status} />
                        ) : status === "REVOKED" ? (
                          <StatusBadge status="REVOKED" />
                        ) : (
                          <span className="text-xs text-amber-600 font-medium">Missing</span>
                        )}
                      </td>

                      {/* Revoke — available for ACTIVE and EXPIRED */}
                      <td className="px-5 py-3.5">
                        {canRevoke ? (
                          <button
                            onClick={() => handleRevoke(d)}
                            disabled={revokingId === d.id}
                            className="text-xs font-medium text-red-500 hover:text-red-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title={
                              status === "EXPIRED"
                                ? "Revoke this expired delegation to clean the slate and re-grant"
                                : "Revoke delegation and its paired consent"
                            }
                          >
                            {revokingId === d.id ? "Revoking…" : "Revoke"}
                          </button>
                        ) : (
                          <span className="text-slate-300 text-xs">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* ── Section 2: Granted to Me ──────────────────────────────────────── */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Granted to Me</h2>
              <span className="text-xs text-slate-400">— I am the actor; I can act on their behalf</span>
            </div>
            {!loading && (
              <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
                toMeActiveCount > 0
                  ? "text-emerald-700 bg-emerald-50 border border-emerald-200"
                  : "text-slate-500 bg-slate-100"
              }`}>
                <span className={`w-1.5 h-1.5 rounded-full ${toMeActiveCount > 0 ? "bg-emerald-500" : "bg-slate-400"}`} />
                {toMeActiveCount} active
              </span>
            )}
          </div>

          <table className="w-full text-sm" aria-label="Delegations granted to me">
            <thead className="bg-slate-50 text-xs text-slate-400 uppercase tracking-wide">
              <tr>
                {["ID", "Delegator (Subject)", "Purpose", "Scopes", "Granted", "Expires", "Status", ""].map((h) => (
                  <th key={h} className="px-5 py-3 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <SkeletonRows cols={8} />
              ) : toMeDelegations.length === 0 ? (
                <EmptyTableRow
                  colSpan={8}
                  message="No delegations have been granted to you."
                  sub="When a colleague grants you delegation, it will appear here."
                />
              ) : (
                toMeDelegations.map((d) => {
                  const delegatorName = formatDisplayName(d.delegatorId);
                  const status = delegationStatus(d);
                  const isInactive = status !== "ACTIVE";
                  const err = activationError[d.id];
                  return (
                    <tr
                      key={d.id}
                      className={`transition-colors ${isInactive ? "opacity-60 hover:opacity-80" : "hover:bg-slate-50/70"}`}
                    >
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-700">
                        {d.id.slice(0, 14).toUpperCase()}
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <Avatar name={delegatorName} className="bg-emerald-100 text-emerald-700" />
                          <div>
                            <p className="text-sm font-medium text-slate-800">{delegatorName}</p>
                            <p className="text-xs text-slate-400">{d.delegatorId}@acme-corp</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <span className={`font-mono text-xs px-1.5 py-0.5 rounded-md border ${
                          isInactive
                            ? "bg-slate-50 text-slate-500 border-slate-200"
                            : "bg-amber-50 text-amber-700 border-amber-200"
                        }`}>
                          {d.purpose}
                        </span>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="flex flex-col gap-1">
                          {(d.scopes ?? []).map((s) => (
                            <span key={s} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md w-fit">
                              {s}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-500">{formatDate(d.createdAt)}</td>
                      <td className="px-5 py-3.5">
                        <ExpiryCell expiresAt={d.expiresAt} status={status} />
                      </td>
                      <td className="px-5 py-3.5">
                        <StatusBadge status={status} />
                      </td>
                      <td className="px-5 py-3.5">
                        {status === "ACTIVE" ? (
                          <div className="flex flex-col gap-1">
                            <button
                              onClick={() => handleActivate(d.id)}
                              disabled={activatingId === d.id}
                              className="text-xs font-medium text-blue-600 hover:text-blue-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                            >
                              {activatingId === d.id ? "Activating…" : "Activate"}
                            </button>
                            {err && <p className="text-xs text-red-500 max-w-[120px] leading-tight">{err}</p>}
                          </div>
                        ) : (
                          <span className="text-slate-300 text-xs">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* ── Section 3: Consent Records (read-only audit) ──────────────────── */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
            <div>
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Consent Records</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Read-only audit view. Consent lifecycle is managed by revoking the paired delegation above.
              </p>
            </div>
          </div>

          <table className="w-full text-sm" aria-label="Consent records">
            <thead className="bg-slate-50 text-xs text-slate-400 uppercase tracking-wide">
              <tr>
                {["Consent ID", "Grantee", "Purpose", "Paired Delegation", "Valid Until", "Status"].map((h) => (
                  <th key={h} className="px-5 py-3 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <SkeletonRows cols={6} />
              ) : consents.length === 0 ? (
                <EmptyTableRow
                  colSpan={6}
                  message="No consent records."
                  sub="Consent records are created automatically when you grant a delegation."
                />
              ) : (
                consents.map((c) => {
                  const isActive = c.status === "ACTIVE";
                  // Find the delegation this consent is paired with (by grantee+purpose).
                  const paired = myDelegations.find(
                    (d) => d.delegateId === c.granteeId && d.purpose === c.purpose
                  );
                  const pairedStatus = paired ? delegationStatus(paired) : null;

                  return (
                    <tr
                      key={c.id}
                      className={`transition-colors ${isActive ? "hover:bg-slate-50" : "opacity-50 hover:opacity-70"}`}
                    >
                      <td className="px-5 py-3.5 font-mono text-xs text-blue-600">
                        {c.id.slice(0, 14).toUpperCase()}
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-700">
                        {formatDisplayName(c.granteeId)}
                      </td>
                      <td className="px-5 py-3.5">
                        <span className={`font-mono text-xs px-1.5 py-0.5 rounded-md border ${
                          isActive
                            ? "bg-amber-50 text-amber-700 border-amber-200"
                            : "bg-slate-50 text-slate-500 border-slate-200"
                        }`}>
                          {c.purpose}
                        </span>
                      </td>

                      {/* Paired delegation — the delegation whose revoke would cascade to this consent */}
                      <td className="px-5 py-3.5">
                        {paired ? (
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-xs text-slate-700">
                              {paired.id.slice(0, 14).toUpperCase()}
                            </span>
                            {pairedStatus && <StatusBadge status={pairedStatus} />}
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400 italic">no current delegation</span>
                        )}
                      </td>

                      <td className="px-5 py-3.5">
                        {c.expiresAt ? (
                          <span className={`text-xs font-medium ${isActive ? "text-amber-700" : "text-slate-400"}`}>
                            {formatDateTime(c.expiresAt)}
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">No expiry</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5">
                        <StatusBadge status={c.status} />
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

      </div>

      <GrantDelegationModal
        open={grantModalOpen}
        onClose={() => setGrantModalOpen(false)}
        onSuccess={async () => {
          setGrantModalOpen(false);
          await load();
        }}
        currentUserName={session?.user?.name ?? undefined}
      />
    </>
  );
}
