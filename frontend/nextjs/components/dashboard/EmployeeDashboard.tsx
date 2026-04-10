"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatCard } from "@/components/shared/StatCard";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { getDashboard, type DashboardResponse } from "@/lib/api/bff";
import { setAccessToken } from "@/lib/api/client";
import type { Booking } from "@/lib/types/booking";
import type { Expense } from "@/lib/types/expense";

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour >= 5 && hour < 12) return "Good morning";
  if (hour >= 12 && hour < 17) return "Good afternoon";
  if (hour >= 17 && hour < 21) return "Good evening";
  return "Good night";
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function formatAmount(amount: number, currency = "INR"): string {
  if (currency === "INR") {
    return `₹${amount.toLocaleString("en-IN")}`;
  }
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

const TYPE_BADGE: Record<string, string> = {
  FLIGHT: "text-sky-700 bg-sky-50 border border-sky-200",
  HOTEL: "text-violet-700 bg-violet-50 border border-violet-200",
  CAR: "text-teal-700 bg-teal-50 border border-teal-200",
};

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

function PlaneIcon() {
  return (
    <svg className="w-5 h-5 text-sky-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
    </svg>
  );
}
function CardIcon() {
  return (
    <svg className="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
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

function deriveStats(bookings: Booking[], expenses: Expense[]) {
  const today = new Date();
  const upcoming = bookings.filter(
    (b) => (b.status === "CONFIRMED" || b.status === "PENDING") && new Date(b.startDate) >= today
  );
  const openExpenses = expenses.filter((e) => e.status === "DRAFT" || e.status === "SUBMITTED");
  const approvedThisMonth = expenses.filter((e) => {
    if (e.status !== "APPROVED" && e.status !== "PAID") return false;
    if (!e.approvalDate) return false;
    const d = new Date(e.approvalDate);
    return d.getMonth() === today.getMonth() && d.getFullYear() === today.getFullYear();
  });
  const spentThisMonth = approvedThisMonth.reduce((sum, e) => sum + (e.totalAmount ?? 0), 0);

  const nextTrip = upcoming.sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime())[0];

  return { upcoming, openExpenses, spentThisMonth, nextTrip };
}

export function EmployeeDashboard() {
  const { data: session } = useSession();
  const { delegationActive, actorName, subjectName } = useDelegationContext();
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);

  const firstName = session?.user?.name?.split(" ")[0] ?? "there";

  useEffect(() => {
    // Guard: don't fetch until the session is ready and we have an access token.
    // This avoids the child-before-parent effect ordering issue where
    // AppShellInner's setAccessToken hasn't fired yet when this component mounts.
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);

    getDashboard()
      .then((res) => {
        setData(res);
        setUnavailable(false);
      })
      .catch(() => {
        setData({ bookings: [], expenses: [] });
        setUnavailable(true);
      })
      .finally(() => setLoading(false));
  }, [session?.accessToken]);

  const bookings: Booking[] = data?.bookings ?? [];
  const expenses: Expense[] = data?.expenses ?? [];
  const stats = deriveStats(bookings, expenses);

  const recentTrips = [...bookings]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);
  const recentExpenses = [...expenses]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);

  const displayName = delegationActive && subjectName ? subjectName : firstName;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">
            {getGreeting()}, {displayName}
          </h1>
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
        <div className="flex items-center gap-2.5">
          <Link
            href="/expense/submit"
            className="flex items-center gap-2 bg-white border border-slate-300 hover:border-slate-400 text-slate-700 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Expense
          </Link>
          <Link
            href="/travel/book"
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
            Book Trip
          </Link>
        </div>
      </div>

      {/* Delegation notice */}
      {delegationActive && actorName && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
          <svg className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
          <div className="min-w-0">
            <p className="text-sm font-medium text-amber-900">{actorName} can act on your behalf</p>
            <p className="text-xs text-amber-700 mt-0.5">
              Active delegation — all actions by {actorName} are attributed to your account.
            </p>
            <Link href="/delegation" className="text-xs font-medium text-amber-800 underline mt-1.5 inline-block hover:text-amber-900">
              Manage delegations →
            </Link>
          </div>
        </div>
      )}

      {/* Stat cards */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard
          label="Upcoming Trips"
          value={loading ? "—" : String(stats.upcoming.length)}
          subtitle={stats.nextTrip ? `Next: ${stats.nextTrip.destination}` : "No upcoming trips"}
          icon={<PlaneIcon />}
          iconBgClass="bg-sky-50"
        />
        <StatCard
          label="Open Expenses"
          value={loading ? "—" : String(stats.openExpenses.length)}
          subtitle={
            loading
              ? ""
              : `${stats.openExpenses.filter((e) => e.status === "DRAFT").length} draft${
                  stats.openExpenses.filter((e) => e.status === "SUBMITTED").length
                    ? ` · ${stats.openExpenses.filter((e) => e.status === "SUBMITTED").length} submitted`
                    : ""
                }`
          }
          icon={<CardIcon />}
          iconBgClass="bg-amber-50"
        />
        <StatCard
          label="Spent This Month"
          value={loading ? "—" : formatAmount(stats.spentThisMonth)}
          subtitle={`${stats.openExpenses.filter((e) => e.status === "APPROVED" || e.status === "PAID").length} approved report(s)`}
          icon={<CurrencyIcon />}
          iconBgClass="bg-emerald-50"
        />
        <StatCard
          label="Delegations"
          value={delegationActive ? "1" : "0"}
          subtitle={delegationActive && actorName ? `${actorName} — active` : "None active"}
          icon={<ShieldIcon />}
          iconBgClass="bg-amber-50"
        />
      </div>

      {/* Recent Trips */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-900">Recent Trips</h2>
          <div className="flex items-center gap-3">
            {unavailable && !loading && (
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Service unavailable
              </span>
            )}
            <Link href="/travel" className="text-xs text-blue-600 hover:text-blue-700 font-medium">View all →</Link>
          </div>
        </div>
        <table className="w-full" aria-label="Recent Trips">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Booking", "Destination", "Dates", "Type", "Amount", "Status"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <TableSkeleton cols={6} />
            ) : recentTrips.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center">
                  <p className="text-sm text-slate-400 mb-3">No trips booked yet.</p>
                  <Link
                    href="/travel/book"
                    className="inline-flex items-center gap-1.5 text-xs font-medium text-blue-600 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition-colors"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    Book your first trip
                  </Link>
                </td>
              </tr>
            ) : (
              recentTrips.map((trip) => (
                <tr
                  key={trip.id}
                  className={`hover:bg-slate-50/70 transition-colors ${trip.delegationId ? "bg-amber-50/10 hover:bg-amber-50/30" : ""}`}
                >
                  <td className="px-5 py-3.5">
                    <Link href={`/travel/${trip.id}`} className="text-xs font-mono text-blue-600 hover:text-blue-700 hover:underline">
                      {trip.id.slice(0, 8).toUpperCase()}
                    </Link>
                  </td>
                  <td className="px-5 py-3.5 text-sm font-medium text-slate-800">{trip.destination}</td>
                  <td className="px-5 py-3.5 text-sm text-slate-500">
                    {formatDate(trip.startDate)} – {formatDate(trip.endDate)}
                  </td>
                  <td className="px-5 py-3.5">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${TYPE_BADGE[trip.bookingType] ?? ""}`}>
                      {trip.bookingType.charAt(0) + trip.bookingType.slice(1).toLowerCase()}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-slate-800">{formatAmount(trip.totalAmount)}</td>
                  <td className="px-5 py-3.5"><StatusBadge status={trip.status} /></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Recent Expenses */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-900">Recent Expenses</h2>
          <div className="flex items-center gap-3">
            {unavailable && !loading && (
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Service unavailable
              </span>
            )}
            <Link href="/expense" className="text-xs text-blue-600 hover:text-blue-700 font-medium">View all →</Link>
          </div>
        </div>
        <table className="w-full" aria-label="Recent Expenses">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Report", "Description", "Submitted", "Amount", "Status"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <TableSkeleton cols={5} />
            ) : recentExpenses.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-5 py-10 text-center">
                  <p className="text-sm text-slate-400 mb-3">No expense reports yet.</p>
                  <Link
                    href="/expense/submit"
                    className="inline-flex items-center gap-1.5 text-xs font-medium text-blue-600 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition-colors"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    Submit your first expense
                  </Link>
                </td>
              </tr>
            ) : (
              recentExpenses.map((exp) => (
                <tr
                  key={exp.id}
                  className={`hover:bg-slate-50/70 transition-colors ${exp.delegationId ? "bg-amber-50/10 hover:bg-amber-50/30" : ""}`}
                >
                  <td className="px-5 py-3.5">
                    <Link href={`/expense/${exp.id}`} className="text-xs font-mono text-blue-600 hover:text-blue-700 hover:underline">
                      {exp.id.slice(0, 8).toUpperCase()}
                    </Link>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-slate-700">{exp.title}</td>
                  <td className="px-5 py-3.5 text-sm text-slate-500">
                    {exp.submissionDate ? formatDate(exp.submissionDate) : <span className="italic text-slate-400">Not submitted</span>}
                  </td>
                  <td className="px-5 py-3.5 text-sm font-medium text-slate-800">
                    {formatAmount(exp.totalAmount, exp.currency)}
                  </td>
                  <td className="px-5 py-3.5"><StatusBadge status={exp.status} /></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
