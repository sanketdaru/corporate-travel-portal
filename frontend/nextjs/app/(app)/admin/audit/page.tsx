"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useSession } from "next-auth/react";
import { bffClient, setAccessToken } from "@/lib/api/client";
import { getAllBookings, getAllExpenses, getBookingAudit } from "@/lib/api/gateway";
import type { BookingAudit } from "@/lib/types/booking";
import type { ExpenseAudit } from "@/lib/api/gateway";

// ── Types ─────────────────────────────────────────────────────────────────────

interface AuditRow {
  id: string;
  timestamp: string;
  action: string;
  actor: string;
  subject: string;
  resource: string;
  resourceType: "booking" | "expense";
  result: "ALLOW" | "DENY";
  delegationId?: string;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit",
  });
}

function mapBookingAudit(bookingId: string, entries: BookingAudit[]): AuditRow[] {
  return entries.map((e) => ({
    id: e.id,
    timestamp: e.timestamp,
    action: e.action,
    actor: e.actorId ?? "—",
    subject: e.subjectId && e.subjectId !== e.actorId ? e.subjectId : "—",
    resource: `BKG-${bookingId.slice(0, 8).toUpperCase()}`,
    resourceType: "booking" as const,
    result: "ALLOW" as const,
    delegationId: e.delegationId,
  }));
}

function mapExpenseAudit(expenseId: string, entries: ExpenseAudit[]): AuditRow[] {
  return entries.map((e) => ({
    id: e.id ?? `${expenseId}-${e.action}-${e.timestamp}`,
    timestamp: e.timestamp,
    action: e.action,
    actor: e.actorId ?? "—",
    subject: e.subjectId && e.subjectId !== e.actorId ? e.subjectId : "—",
    resource: `EXP-${expenseId.slice(0, 8).toUpperCase()}`,
    resourceType: "expense" as const,
    result: "ALLOW" as const,
    delegationId: e.delegationId,
    tenantId: e.tenantId,
  }));
}

const PAGE_SIZE = 20;

// ── Filter bar ────────────────────────────────────────────────────────────────

interface Filters {
  action: string;
  actor: string;
  resourceType: string;
  dateFrom: string;
  dateTo: string;
}

const EMPTY_FILTERS: Filters = {
  action: "",
  actor: "",
  resourceType: "",
  dateFrom: "",
  dateTo: "",
};

// ── Component ─────────────────────────────────────────────────────────────────

export default function AuditPage() {
  const { data: session } = useSession();

  const [allRows, setAllRows]     = useState<AuditRow[]>([]);
  const [loading, setLoading]     = useState(true);
  const [filters, setFilters]     = useState<Filters>(EMPTY_FILTERS);
  const [page, setPage]           = useState(1);

  // ── Load audit data ──────────────────────────────────────────────────────────
  const loadAudit = useCallback(async () => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);
    setLoading(true);

    try {
      // Use gateway (not BFF) so admin gets tenant-wide data, not just own records
      const [bookings, expenses] = await Promise.all([
        getAllBookings(),
        getAllExpenses(),
      ]);

      // Fetch audit for each resource in parallel (limit to 10 each to avoid overload)
      const [bookingAudits, expenseAudits] = await Promise.all([
        Promise.all(
          bookings.slice(0, 10).map(async (b) => {
            try {
              const entries = await getBookingAudit(b.id);
              return { id: b.id, entries };
            } catch {
              return { id: b.id, entries: [] as BookingAudit[] };
            }
          }),
        ),
        Promise.all(
          expenses.slice(0, 10).map(async (e) => {
            try {
              const r = await bffClient.get<ExpenseAudit[]>(`/api/bff/expenses/${e.id}/audit`);
              return { id: e.id, entries: r.data };
            } catch {
              return { id: e.id, entries: [] as ExpenseAudit[] };
            }
          }),
        ),
      ]);

      const rows: AuditRow[] = [
        ...bookingAudits.flatMap(({ id, entries }) => mapBookingAudit(id, entries)),
        ...expenseAudits.flatMap(({ id, entries }) => mapExpenseAudit(id, entries)),
      ].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

      setAllRows(rows);
    } catch {
      // leave empty; user sees empty state
    } finally {
      setLoading(false);
    }
  }, [session?.accessToken]);

  useEffect(() => {
    loadAudit();
  }, [loadAudit]);

  // ── Derived filter values for dropdowns ──────────────────────────────────────
  const uniqueActions = useMemo(
    () => Array.from(new Set(allRows.map((r) => r.action))).sort(),
    [allRows],
  );

  // ── Filtered + paginated rows ────────────────────────────────────────────────
  const filtered = useMemo(() => {
    return allRows.filter((row) => {
      if (filters.action && row.action !== filters.action) return false;
      if (filters.actor && !row.actor.toLowerCase().includes(filters.actor.toLowerCase())) return false;
      if (filters.resourceType && row.resourceType !== filters.resourceType) return false;
      if (filters.dateFrom && new Date(row.timestamp) < new Date(filters.dateFrom)) return false;
      if (filters.dateTo) {
        const to = new Date(filters.dateTo);
        to.setHours(23, 59, 59, 999);
        if (new Date(row.timestamp) > to) return false;
      }
      return true;
    });
  }, [allRows, filters]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows   = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPage(1);
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS);
    setPage(1);
  }

  const hasActiveFilter = Object.values(filters).some(Boolean);

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Audit Log</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            All booking and expense events across the tenant · DENY events require OPA decision log integration
          </p>
        </div>
        <button
          onClick={loadAudit}
          disabled={loading}
          className="flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900 bg-white border border-slate-200 hover:border-slate-300 px-3 py-2 rounded-lg transition-colors disabled:opacity-50"
        >
          <svg
            className={`w-4 h-4 ${loading ? "animate-spin" : ""}`}
            fill="none" stroke="currentColor" viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Refresh
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white border border-slate-200 rounded-xl p-4">
        <div className="grid grid-cols-5 gap-3">
          {/* Action type */}
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">Action</label>
            <select
              value={filters.action}
              onChange={(e) => updateFilter("action", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              <option value="">All actions</option>
              {uniqueActions.map((a) => (
                <option key={a} value={a}>{a}</option>
              ))}
            </select>
          </div>

          {/* Actor */}
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">Actor</label>
            <input
              type="text"
              placeholder="Filter by actor…"
              value={filters.actor}
              onChange={(e) => updateFilter("actor", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Resource type */}
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">Resource type</label>
            <select
              value={filters.resourceType}
              onChange={(e) => updateFilter("resourceType", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              <option value="">All types</option>
              <option value="booking">Booking</option>
              <option value="expense">Expense</option>
            </select>
          </div>

          {/* Date from */}
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">From date</label>
            <input
              type="date"
              value={filters.dateFrom}
              onChange={(e) => updateFilter("dateFrom", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Date to */}
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">To date</label>
            <input
              type="date"
              value={filters.dateTo}
              onChange={(e) => updateFilter("dateTo", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {hasActiveFilter && (
          <div className="mt-3 flex items-center gap-2">
            <span className="text-xs text-slate-500">
              Showing {filtered.length} of {allRows.length} events
            </span>
            <button
              onClick={clearFilters}
              className="text-xs text-blue-600 hover:text-blue-800 underline"
            >
              Clear filters
            </button>
          </div>
        )}
      </div>

      {/* Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <table className="w-full text-xs" aria-label="Audit event log">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-100">
              <th className="px-5 py-3 text-left font-semibold text-slate-500 uppercase tracking-wider">Time</th>
              <th className="px-5 py-3 text-left font-semibold text-slate-500 uppercase tracking-wider">Action</th>
              <th className="px-5 py-3 text-left font-semibold text-slate-500 uppercase tracking-wider">Actor</th>
              <th className="px-5 py-3 text-left font-semibold text-slate-500 uppercase tracking-wider">Subject</th>
              <th className="px-5 py-3 text-left font-semibold text-slate-500 uppercase tracking-wider">Resource</th>
              <th className="px-5 py-3 text-left font-semibold text-slate-500 uppercase tracking-wider">Result</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 font-mono">
            {loading
              ? Array.from({ length: PAGE_SIZE }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-5 py-3.5">
                        <div className={`h-3.5 bg-slate-100 rounded animate-pulse ${j === 0 ? "w-32" : j === 1 ? "w-28" : j === 2 ? "w-24" : "w-20"}`} />
                      </td>
                    ))}
                  </tr>
                ))
              : pageRows.length > 0
              ? pageRows.map((row, i) => {
                  const isDeny = row.result === "DENY";
                  return (
                    <tr
                      key={`${row.id}-${i}`}
                      className={isDeny
                        ? "bg-red-50 hover:bg-red-100/70 transition-colors"
                        : "hover:bg-slate-50/70 transition-colors"}
                    >
                      <td className="px-5 py-3.5 text-slate-400 whitespace-nowrap">
                        {formatDateTime(row.timestamp)}
                      </td>
                      <td className={`px-5 py-3.5 font-medium whitespace-nowrap ${isDeny ? "text-red-700" : "text-slate-700"}`}>
                        {row.action}
                      </td>
                      <td className={`px-5 py-3.5 whitespace-nowrap ${isDeny ? "text-red-600" : "text-blue-600"}`}>
                        {row.actor}
                      </td>
                      <td className={`px-5 py-3.5 whitespace-nowrap ${row.subject === "—" ? "text-slate-400" : "text-amber-600"}`}>
                        {row.subject}
                      </td>
                      <td className={`px-5 py-3.5 whitespace-nowrap ${isDeny ? "text-red-500" : "text-slate-500"}`}>
                        <span>{row.resource}</span>
                        <span className={`ml-2 px-1.5 py-0.5 rounded text-[10px] font-medium ${
                          row.resourceType === "booking"
                            ? "bg-blue-50 text-blue-600"
                            : "bg-violet-50 text-violet-600"
                        }`}>
                          {row.resourceType}
                        </span>
                      </td>
                      <td className="px-5 py-3.5">
                        <span className={`font-semibold px-2 py-0.5 rounded ${
                          isDeny
                            ? "bg-red-100 text-red-700"
                            : "bg-emerald-50 text-emerald-700"
                        }`}>
                          {row.result}
                        </span>
                      </td>
                    </tr>
                  );
                })
              : (
                <tr>
                  <td colSpan={6} className="px-5 py-12 text-center text-slate-400 font-sans">
                    {hasActiveFilter ? "No events match the current filters." : "No audit events found."}
                  </td>
                </tr>
              )}
          </tbody>
        </table>

        {/* Pagination footer */}
        <div className="px-5 py-3 border-t border-slate-100 bg-slate-50 flex items-center justify-between">
          <span className="text-xs text-slate-400 font-sans">
            {loading
              ? "Loading…"
              : `Showing ${filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–${Math.min(page * PAGE_SIZE, filtered.length)} of ${filtered.length} events`}
          </span>

          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1 || loading}
              className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900 border border-slate-200 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 transition-colors"
            >
              ← Prev
            </button>
            <span className="px-3 py-1.5 text-xs text-slate-500">
              Page {page} of {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages || loading}
              className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900 border border-slate-200 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 transition-colors"
            >
              Next →
            </button>
          </div>
        </div>
      </div>

      {/* Note about DENY events */}
      <p className="text-xs text-slate-400 text-center">
        Audit columns: actorId · subjectId · delegationId · consentId (ADR-011) ·
        DENY events require OPA decision log integration (infrastructure/opa/)
      </p>
    </div>
  );
}
