"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatCard } from "@/components/shared/StatCard";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { getDashboard } from "@/lib/api/bff";
import { getExpenses, approveExpense, rejectExpense, getMyDelegations, type Delegation } from "@/lib/api/gateway";
import type { Expense } from "@/lib/types/expense";
import type { Booking } from "@/lib/types/booking";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function formatAmount(amount: number, currency = "INR"): string {
  if (currency === "INR") return `₹${amount.toLocaleString("en-IN")}`;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

function formatDisplayName(userId: string): string {
  return userId
    .split(".")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function getAvatarInitials(userId: string): string {
  return userId
    .split(".")
    .map((p) => p.charAt(0).toUpperCase())
    .join("")
    .slice(0, 2);
}

const AVATAR_COLORS = [
  "bg-blue-600", "bg-violet-500", "bg-teal-500",
  "bg-emerald-600", "bg-amber-500", "bg-indigo-500",
];

function avatarColor(userId: string): string {
  const hash = userId.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function TableSkeleton({ cols, rows = 3 }: { cols: number; rows?: number }) {
  return (
    <>
      {Array.from({ length: rows }).map((_, i) => (
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

function AlertIcon() {
  return (
    <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function PlaneIcon() {
  return (
    <svg className="w-5 h-5 text-sky-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
    </svg>
  );
}
function CurrencyIcon() {
  return (
    <svg className="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function ShieldIcon() {
  return (
    <svg className="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}

export function ManagerDashboard() {
  const { data: session } = useSession();
  const [ownBookings, setOwnBookings] = useState<Booking[]>([]);
  const [pendingApprovals, setPendingApprovals] = useState<Expense[]>([]);
  const [delegations, setDelegations] = useState<Delegation[]>([]);
  const [loading, setLoading] = useState(true);
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);

  useEffect(() => {
    Promise.allSettled([
      getDashboard(),
      getExpenses(),
      getMyDelegations(),
    ]).then(([dashResult, expResult, delResult]) => {
      if (dashResult.status === "fulfilled") {
        setOwnBookings(dashResult.value.bookings);
      }
      if (expResult.status === "fulfilled") {
        // Show only SUBMITTED expenses (pending manager action)
        setPendingApprovals(expResult.value.filter((e) => e.status === "SUBMITTED"));
      }
      if (delResult.status === "fulfilled") {
        setDelegations(delResult.value.filter((d) => d.status === "ACTIVE"));
      }
    }).finally(() => setLoading(false));
  }, []);

  const today = new Date();
  const teamTripsThisMonth = ownBookings.filter((b) => {
    const d = new Date(b.startDate);
    return d.getMonth() === today.getMonth() && d.getFullYear() === today.getFullYear();
  });
  const teamSpend = ownBookings.reduce((sum, b) => sum + (b.totalAmount ?? 0), 0);

  async function handleApprove(id: string) {
    setApprovingId(id);
    try {
      await approveExpense(id);
      setPendingApprovals((prev) => prev.filter((e) => e.id !== id));
    } catch {
      // silently fail for now — Phase 8 adds proper error toasts
    } finally {
      setApprovingId(null);
    }
  }

  async function handleReject(id: string) {
    setRejectingId(id);
    try {
      await rejectExpense(id);
      setPendingApprovals((prev) => prev.filter((e) => e.id !== id));
    } catch {
      // silently fail for now
    } finally {
      setRejectingId(null);
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Manager Dashboard</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            {new Date().toLocaleDateString("en-IN", {
              weekday: "long",
              day: "numeric",
              month: "short",
              year: "numeric",
            })}{" "}
            · {session?.user?.tenantId ?? ""}
          </p>
        </div>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard
          label="Pending Approvals"
          value={loading ? "—" : String(pendingApprovals.length)}
          subtitle={pendingApprovals.length > 0 ? "Requires your action" : "All caught up"}
          icon={<AlertIcon />}
          iconBgClass="bg-red-50"
          valueClassName={pendingApprovals.length > 0 ? "text-red-600" : undefined}
        />
        <StatCard
          label="Team Trips (Month)"
          value={loading ? "—" : String(teamTripsThisMonth.length)}
          subtitle={`${ownBookings.filter((b) => b.status === "CONFIRMED").length} upcoming`}
          icon={<PlaneIcon />}
          iconBgClass="bg-sky-50"
        />
        <StatCard
          label="Team Spend"
          value={loading ? "—" : formatAmount(teamSpend)}
          subtitle="Approved this month"
          icon={<CurrencyIcon />}
          iconBgClass="bg-emerald-50"
        />
        <StatCard
          label="Active Delegations"
          value={loading ? "—" : String(delegations.length)}
          subtitle={delegations.length > 0 ? `Across ${delegations.length} employee(s)` : "None active"}
          icon={<ShieldIcon />}
          iconBgClass="bg-amber-50"
        />
      </div>

      {/* Pending Approvals table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-2.5">
          <h2 className="text-sm font-semibold text-slate-800">Pending Approvals</h2>
          {pendingApprovals.length > 0 && (
            <span className="text-xs font-bold bg-red-100 text-red-600 w-5 h-5 rounded-full flex items-center justify-center">
              {pendingApprovals.length}
            </span>
          )}
        </div>
        <table className="w-full" aria-label="Pending Approvals">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Report", "Employee", "Description", "Amount", "Submitted", "Actions"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <TableSkeleton cols={6} rows={2} />
            ) : pendingApprovals.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-8 text-center text-sm text-slate-400">
                  No pending approvals
                </td>
              </tr>
            ) : (
              pendingApprovals.map((exp) => {
                const isDelegated = !!exp.delegationId || exp.createdBy !== exp.userId;
                return (
                  <tr
                    key={exp.id}
                    className={`transition-colors ${isDelegated ? "bg-amber-50/20 hover:bg-amber-50/40" : "hover:bg-slate-50/70"}`}
                  >
                    <td className="px-5 py-3.5">
                      <Link href={`/expense/${exp.id}`} className="text-xs font-mono text-blue-600 hover:underline">
                        {exp.id.slice(0, 8).toUpperCase()}
                      </Link>
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2">
                        <div
                          className={`w-6 h-6 rounded-full flex items-center justify-center text-white text-xs font-semibold flex-shrink-0 ${avatarColor(exp.userId)}`}
                          aria-hidden="true"
                        >
                          {getAvatarInitials(exp.userId)}
                        </div>
                        <div>
                          <span className="text-sm text-slate-800">{formatDisplayName(exp.userId)}</span>
                          {isDelegated && (
                            <span className="ml-1.5 inline-flex items-center gap-1 text-xs font-medium bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-md">
                              delegate
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3.5 text-sm text-slate-500 max-w-xs truncate">{exp.title}</td>
                    <td className="px-5 py-3.5 text-sm font-medium text-slate-800">
                      {formatAmount(exp.totalAmount, exp.currency)}
                    </td>
                    <td className="px-5 py-3.5 text-sm text-slate-500">
                      {exp.submissionDate ? formatDate(exp.submissionDate) : "—"}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => handleApprove(exp.id)}
                          disabled={approvingId === exp.id || rejectingId === exp.id}
                          className="text-xs font-medium bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white px-3 py-1.5 rounded-lg transition-colors"
                          aria-label={`Approve ${exp.id}`}
                        >
                          {approvingId === exp.id ? "…" : "Approve"}
                        </button>
                        <button
                          onClick={() => handleReject(exp.id)}
                          disabled={approvingId === exp.id || rejectingId === exp.id}
                          className="text-xs font-medium bg-white border border-red-200 hover:border-red-300 disabled:opacity-50 text-red-600 px-3 py-1.5 rounded-lg transition-colors"
                          aria-label={`Reject ${exp.id}`}
                        >
                          {rejectingId === exp.id ? "…" : "Reject"}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Active Delegations table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-800">Active Delegations — My Team</h2>
          <Link href="/delegation" className="text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors">
            View all →
          </Link>
        </div>
        <table className="w-full" aria-label="Active Delegations">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Subject (delegator)", "Actor (delegate)", "Purpose", "Scopes", "Expires", "Status"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <TableSkeleton cols={6} rows={2} />
            ) : delegations.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-8 text-center text-sm text-slate-400">
                  No active delegations in your team
                </td>
              </tr>
            ) : (
              delegations.map((d) => {
                const isNearExpiry =
                  new Date(d.expiresAt).getTime() - Date.now() < 24 * 60 * 60 * 1000;
                return (
                  <tr key={d.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-5 py-3.5 text-sm text-slate-800">{formatDisplayName(d.delegatorId)}</td>
                    <td className="px-5 py-3.5 text-sm text-slate-800">{formatDisplayName(d.delegateId)}</td>
                    <td className="px-5 py-3.5">
                      <span className="font-mono text-xs text-slate-600 bg-slate-100 px-2 py-0.5 rounded">
                        {d.purpose}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-xs text-slate-500">{d.scopes.join(" · ")}</td>
                    <td className={`px-5 py-3.5 text-xs font-medium ${isNearExpiry ? "text-amber-600" : "text-slate-500"}`}>
                      {formatDate(d.expiresAt)}
                    </td>
                    <td className="px-5 py-3.5">
                      <StatusBadge status={d.status} />
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
