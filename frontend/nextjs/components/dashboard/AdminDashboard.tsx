"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatCard } from "@/components/shared/StatCard";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { bffClient, setAccessToken } from "@/lib/api/client";
import { getAllBookings, getAllExpenses, getBookingAudit } from "@/lib/api/gateway";
import { getDashboard } from "@/lib/api/bff";
import type { ServiceHealth } from "@/app/api/health/route";
import type { BookingAudit } from "@/lib/types/booking";
import type { ExpenseAudit } from "@/lib/api/gateway";
import type { Booking } from "@/lib/types/booking";
import type { Expense } from "@/lib/types/expense";

// ── Unified audit event for the recent events table ──────────────────────────

interface AuditRow {
  timestamp: string;
  action: string;
  actor: string;
  subject: string;
  resource: string;
  result: "ALLOW" | "DENY";
}

function toRows(
  bookingAudits: { bookingId: string; entries: BookingAudit[] }[],
  expenseAudits: { expenseId: string; entries: ExpenseAudit[] }[],
): AuditRow[] {
  const rows: AuditRow[] = [];

  for (const { bookingId, entries } of bookingAudits) {
    for (const e of entries) {
      rows.push({
        timestamp: e.timestamp,
        action: e.action,
        actor: e.actorId ?? "—",
        subject: e.subjectId && e.subjectId !== e.actorId ? e.subjectId : "—",
        resource: `BKG-${bookingId.slice(0, 8).toUpperCase()}`,
        result: "ALLOW",
      });
    }
  }

  for (const { expenseId, entries } of expenseAudits) {
    for (const e of entries) {
      rows.push({
        timestamp: e.timestamp,
        action: e.action,
        actor: e.actorId ?? "—",
        subject: e.subjectId && e.subjectId !== e.actorId ? e.subjectId : "—",
        resource: `EXP-${expenseId.slice(0, 8).toUpperCase()}`,
        result: "ALLOW",
      });
    }
  }

  return rows.sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
  );
}

// ── Icons ─────────────────────────────────────────────────────────────────────

function UserIcon() {
  return (
    <svg className="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}
function ShieldCheckIcon() {
  return (
    <svg className="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}
function CheckCircleIcon() {
  return (
    <svg className="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function AlertTriangleIcon() {
  return (
    <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>
  );
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
  });
}

function formatAmount(amount: number, currency = "INR"): string {
  if (currency === "INR") return `₹${amount.toLocaleString("en-IN")}`;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

// ── Component ─────────────────────────────────────────────────────────────────

export function AdminDashboard() {
  const { data: session } = useSession();

  // ── Personal data (own trips/expenses via BFF) ───────────────────────────────
  const [myBookings, setMyBookings]   = useState<Booking[]>([]);
  const [myExpenses, setMyExpenses]   = useState<Expense[]>([]);
  const [myLoading, setMyLoading]     = useState(true);

  // ── System data (realm-wide) ─────────────────────────────────────────────────
  const [services, setServices]           = useState<ServiceHealth[]>([]);
  const [healthLoading, setHealthLoading] = useState(true);
  const [auditRows, setAuditRows]         = useState<AuditRow[]>([]);
  const [auditLoading, setAuditLoading]   = useState(true);
  const [activeDelegations, setActiveDelegations] = useState<number | null>(null);
  const [activeUsers, setActiveUsers]     = useState<number | null>(null);

  // ── Own trips/expenses via BFF ───────────────────────────────────────────────
  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);

    getDashboard()
      .then((res) => {
        setMyBookings(res.bookings ?? []);
        setMyExpenses(res.expenses ?? []);
      })
      .catch(() => {
        setMyBookings([]);
        setMyExpenses([]);
      })
      .finally(() => setMyLoading(false));
  }, [session?.accessToken]);

  // ── Live health from Next.js /api/health route ──────────────────────────────
  useEffect(() => {
    async function loadHealth() {
      try {
        const res = await fetch("/api/health", { cache: "no-store" });
        const data = await res.json() as { services: ServiceHealth[] };
        setServices(data.services);
      } catch {
        // leave empty; UI shows "—"
      } finally {
        setHealthLoading(false);
      }
    }
    loadHealth();
    const timer = setInterval(loadHealth, 30_000);
    return () => clearInterval(timer);
  }, []);

  // ── Admin stats (Keycloak user count) ────────────────────────────────────────
  useEffect(() => {
    fetch("/api/admin/stats", { cache: "no-store" })
      .then((r) => r.json())
      .then((d: { activeUsers: number | null }) => setActiveUsers(d.activeUsers))
      .catch(() => {});
  }, []);

  // ── Aggregate audit from BFF ─────────────────────────────────────────────────
  const loadAudit = useCallback(async () => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);

    try {
      // Use gateway (not BFF) — admin needs tenant-wide data, not just own records
      const [bookings, expenses] = await Promise.all([
        getAllBookings(),
        getAllExpenses(),
      ]);

      // Fetch audit for first 3 bookings and first 3 expenses in parallel
      const [bookingAudits, expenseAudits] = await Promise.all([
        Promise.all(
          bookings.slice(0, 3).map(async (b) => {
            try {
              const entries = await getBookingAudit(b.id);
              return { bookingId: b.id, entries };
            } catch {
              return { bookingId: b.id, entries: [] as BookingAudit[] };
            }
          }),
        ),
        Promise.all(
          expenses.slice(0, 3).map(async (e) => {
            try {
              const r = await bffClient.get<ExpenseAudit[]>(`/api/bff/expenses/${e.id}/audit`);
              return { expenseId: e.id, entries: r.data };
            } catch {
              return { expenseId: e.id, entries: [] as ExpenseAudit[] };
            }
          }),
        ),
      ]);

      const rows = toRows(bookingAudits, expenseAudits);
      setAuditRows(rows);

      // Active delegations: count bookings with a delegationId as a proxy
      const delegated = bookings.filter((b) => b.delegationId).length;
      setActiveDelegations(delegated);
    } catch {
      // leave empty
    } finally {
      setAuditLoading(false);
    }
  }, [session?.accessToken]);

  useEffect(() => {
    loadAudit();
  }, [loadAudit]);

  const upCount = services.filter((s) => s.status === "UP").length;

  const recentMyTrips = [...myBookings]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 3);
  const recentMyExpenses = [...myExpenses]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 3);

  const myUpcomingCount = myBookings.filter(
    (b) => (b.status === "CONFIRMED" || b.status === "PENDING") && new Date(b.startDate) >= new Date()
  ).length;
  const myOpenExpenseCount = myExpenses.filter(
    (e) => e.status === "DRAFT" || e.status === "SUBMITTED"
  ).length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Admin Dashboard</h1>
        <p className="text-sm text-slate-400 mt-0.5">
          {new Date().toLocaleDateString("en-IN", {
            weekday: "long",
            day: "numeric",
            month: "short",
            year: "numeric",
          })}{" "}
          · Tenant: {session?.user?.tenantId ?? ""}
        </p>
      </div>

      {/* ── My Activity ───────────────────────────────────────────────────────── */}
      <div>
        <div className="flex items-center gap-3 mb-4">
          <h2 className="text-sm font-semibold text-slate-700 uppercase tracking-widest">My Activity</h2>
          <div className="flex-1 h-px bg-slate-200" />
          <span className="text-xs text-slate-400">Your own bookings &amp; expenses</span>
        </div>

        <div className="grid grid-cols-2 gap-4 mb-4">
          <StatCard
            label="My Upcoming Trips"
            value={myLoading ? "—" : String(myUpcomingCount)}
            subtitle={recentMyTrips[0] ? `Next: ${recentMyTrips[0].destination}` : "No upcoming trips"}
            icon={<svg className="w-5 h-5 text-sky-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" /></svg>}
            iconBgClass="bg-sky-50"
          />
          <StatCard
            label="My Open Expenses"
            value={myLoading ? "—" : String(myOpenExpenseCount)}
            subtitle={myOpenExpenseCount === 0 ? "All clear" : `${myExpenses.filter(e => e.status === "DRAFT").length} draft · ${myExpenses.filter(e => e.status === "SUBMITTED").length} submitted`}
            icon={<svg className="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" /></svg>}
            iconBgClass="bg-amber-50"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          {/* My Recent Trips */}
          <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
              <h3 className="text-xs font-semibold text-slate-700">My Recent Trips</h3>
              <Link href="/travel" className="text-xs font-medium text-blue-600 hover:text-blue-700">View all →</Link>
            </div>
            <table className="w-full" aria-label="My Recent Trips">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50">
                  {["Destination", "Dates", "Status"].map((h) => (
                    <th key={h} className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {myLoading
                  ? Array.from({ length: 3 }).map((_, i) => (
                      <tr key={i}>
                        {[1, 2, 3].map((j) => (
                          <td key={j} className="px-4 py-3"><div className="h-3 bg-slate-100 rounded animate-pulse" /></td>
                        ))}
                      </tr>
                    ))
                  : recentMyTrips.length === 0
                  ? (
                    <tr>
                      <td colSpan={3} className="px-4 py-6 text-center text-slate-400">
                        No trips yet.{" "}
                        <Link href="/travel/book" className="text-blue-600 hover:underline">Book one →</Link>
                      </td>
                    </tr>
                  )
                  : recentMyTrips.map((trip) => (
                    <tr key={trip.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-4 py-3 font-medium text-slate-800">
                        <Link href={`/travel/${trip.id}`} className="hover:text-blue-600 hover:underline">{trip.destination}</Link>
                      </td>
                      <td className="px-4 py-3 text-slate-500">{formatDate(trip.startDate)}</td>
                      <td className="px-4 py-3"><StatusBadge status={trip.status} /></td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>

          {/* My Recent Expenses */}
          <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
              <h3 className="text-xs font-semibold text-slate-700">My Recent Expenses</h3>
              <Link href="/expense" className="text-xs font-medium text-blue-600 hover:text-blue-700">View all →</Link>
            </div>
            <table className="w-full" aria-label="My Recent Expenses">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50">
                  {["Title", "Amount", "Status"].map((h) => (
                    <th key={h} className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {myLoading
                  ? Array.from({ length: 3 }).map((_, i) => (
                      <tr key={i}>
                        {[1, 2, 3].map((j) => (
                          <td key={j} className="px-4 py-3"><div className="h-3 bg-slate-100 rounded animate-pulse" /></td>
                        ))}
                      </tr>
                    ))
                  : recentMyExpenses.length === 0
                  ? (
                    <tr>
                      <td colSpan={3} className="px-4 py-6 text-center text-slate-400">
                        No expenses yet.{" "}
                        <Link href="/expense/submit" className="text-blue-600 hover:underline">Submit one →</Link>
                      </td>
                    </tr>
                  )
                  : recentMyExpenses.map((exp) => (
                    <tr key={exp.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-4 py-3 font-medium text-slate-800">
                        <Link href={`/expense/${exp.id}`} className="hover:text-blue-600 hover:underline">{exp.title}</Link>
                      </td>
                      <td className="px-4 py-3 text-emerald-700">{formatAmount(exp.totalAmount, exp.currency)}</td>
                      <td className="px-4 py-3"><StatusBadge status={exp.status} /></td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* ── System Overview ───────────────────────────────────────────────────── */}
      <div>
        <div className="flex items-center gap-3 mb-4">
          <h2 className="text-sm font-semibold text-slate-700 uppercase tracking-widest">System Overview</h2>
          <div className="flex-1 h-px bg-slate-200" />
          <span className="text-xs text-slate-400">All users across the tenant</span>
        </div>

      {/* Stat cards */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard
          label="Active Users"
          value={activeUsers !== null ? String(activeUsers) : "—"}
          subtitle="Keycloak · corporate-travel realm"
          icon={<UserIcon />}
          iconBgClass="bg-blue-50"
        />
        <StatCard
          label="Active Delegations"
          value={activeDelegations !== null ? String(activeDelegations) : "—"}
          subtitle="Delegated authorizations"
          icon={<ShieldCheckIcon />}
          iconBgClass="bg-amber-50"
        />
        <StatCard
          label="Services UP"
          value={healthLoading ? "—" : `${upCount}/${services.length}`}
          subtitle="Polled every 30s"
          icon={<CheckCircleIcon />}
          iconBgClass="bg-emerald-50"
        />
        <StatCard
          label="OPA Violations"
          value="—"
          subtitle="Enable OPA status plugin to track"
          icon={<AlertTriangleIcon />}
          iconBgClass="bg-red-50"
        />
      </div>

      {/* Service Health — live */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-800">Service Health</h2>
          <Link href="/admin/health" className="text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors">
            View details →
          </Link>
        </div>
        <div className="divide-y divide-slate-100">
          {healthLoading
            ? Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="flex items-center justify-between px-5 py-3">
                  <div className="flex items-center gap-3">
                    <div className="w-2 h-2 rounded-full bg-slate-200 animate-pulse" />
                    <div className="w-36 h-4 bg-slate-100 rounded animate-pulse" />
                  </div>
                  <div className="w-12 h-4 bg-slate-100 rounded animate-pulse" />
                </div>
              ))
            : services.map((svc) => {
                const isUp = svc.status === "UP";
                return (
                  <div key={svc.name} className="flex items-center justify-between px-5 py-3 hover:bg-slate-50/70 transition-colors">
                    <div className="flex items-center gap-3">
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${isUp ? "bg-emerald-500" : "bg-red-500 animate-pulse"}`} aria-hidden="true" />
                      <span className="text-sm font-medium text-slate-800">{svc.name}</span>
                      <span className="text-xs text-slate-400 font-mono">:{svc.port}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-mono text-slate-400">{svc.latencyMs}ms</span>
                      <span className={`text-xs font-semibold ${isUp ? "text-emerald-600" : "text-red-600"}`}>
                        {svc.status}{svc.note ? ` · ${svc.note}` : ""}
                      </span>
                    </div>
                  </div>
                );
              })}
        </div>
      </div>

      {/* Recent Audit Events — aggregated from BFF */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <h2 className="text-sm font-semibold text-slate-800">Recent Audit Events</h2>
          <Link href="/admin/audit" className="text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors">
            Full audit log →
          </Link>
        </div>
        <table className="w-full" aria-label="Recent Audit Events">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Time", "Action", "Actor", "Subject", "Resource", "Result"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 font-mono text-xs">
            {auditLoading
              ? Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-5 py-3.5">
                        <div className="h-3.5 bg-slate-100 rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))
              : auditRows.slice(0, 5).map((ev, i) => (
                  <tr
                    key={i}
                    className={ev.result === "DENY"
                      ? "bg-red-50/50 hover:bg-red-50/80 transition-colors"
                      : "hover:bg-slate-50/70 transition-colors"}
                  >
                    <td className="px-5 py-3.5 text-slate-400">
                      {new Date(ev.timestamp).toLocaleTimeString("en-IN", {
                        hour: "2-digit", minute: "2-digit", second: "2-digit",
                      })}
                    </td>
                    <td className={`px-5 py-3.5 ${ev.result === "DENY" ? "text-red-700" : "text-slate-700"}`}>
                      {ev.action}
                    </td>
                    <td className={`px-5 py-3.5 ${ev.result === "DENY" ? "text-red-600" : "text-blue-600"}`}>
                      {ev.actor}
                    </td>
                    <td className={`px-5 py-3.5 ${ev.subject === "—" ? "text-slate-400" : "text-amber-600"}`}>
                      {ev.subject}
                    </td>
                    <td className={`px-5 py-3.5 ${ev.result === "DENY" ? "text-red-500" : "text-slate-500"}`}>
                      {ev.resource}
                    </td>
                    <td className="px-5 py-3.5">
                      <span className={`font-semibold ${ev.result === "ALLOW" ? "text-emerald-600" : "text-red-600"}`}>
                        {ev.result}
                      </span>
                    </td>
                  </tr>
                ))}
            {!auditLoading && auditRows.length === 0 && (
              <tr>
                <td colSpan={6} className="px-5 py-8 text-center text-slate-400">
                  No audit events found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <div className="px-5 py-3 border-t border-slate-100 text-xs text-slate-400 bg-slate-50">
          Showing last 5 events · Columns: actorId · subjectId · delegationId · consentId (ADR-011)
        </div>
      </div>

      </div>{/* end System Overview */}
    </div>
  );
}
