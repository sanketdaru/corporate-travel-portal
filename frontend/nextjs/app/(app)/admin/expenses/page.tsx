"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { setAccessToken } from "@/lib/api/client";
import { getAllExpenses } from "@/lib/api/gateway";
import { StatusBadge } from "@/components/shared/StatusBadge";
import type { Expense } from "@/lib/types/expense";

const PAGE_SIZE = 20;

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
  });
}

function formatAmount(amount: number, currency = "INR"): string {
  if (currency === "INR") return `₹${amount.toLocaleString("en-IN")}`;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

interface Filters {
  status: string;
  search: string;
}

const EMPTY_FILTERS: Filters = { status: "", search: "" };

export default function AdminExpensesPage() {
  const { data: session } = useSession();

  const [allExpenses, setAllExpenses] = useState<Expense[]>([]);
  const [loading, setLoading]         = useState(true);
  const [filters, setFilters]         = useState<Filters>(EMPTY_FILTERS);
  const [page, setPage]               = useState(1);

  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);
    setLoading(true);

    getAllExpenses()
      .then(setAllExpenses)
      .catch(() => setAllExpenses([]))
      .finally(() => setLoading(false));
  }, [session?.accessToken]);

  const uniqueStatuses = useMemo(
    () => Array.from(new Set(allExpenses.map((e) => e.status))).sort(),
    [allExpenses],
  );

  const filtered = useMemo(() => {
    return allExpenses.filter((e) => {
      if (filters.status && e.status !== filters.status) return false;
      if (filters.search && !e.title.toLowerCase().includes(filters.search.toLowerCase())) return false;
      return true;
    });
  }, [allExpenses, filters]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows   = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPage(1);
  }

  const hasActiveFilter = Object.values(filters).some(Boolean);

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">All Expenses</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            All expense reports across the tenant · OPA enforces tenant scope
          </p>
        </div>
        <button
          onClick={() => {
            if (!session?.accessToken) return;
            setLoading(true);
            getAllExpenses()
              .then(setAllExpenses)
              .catch(() => setAllExpenses([]))
              .finally(() => setLoading(false));
          }}
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
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">Status</label>
            <select
              value={filters.status}
              onChange={(e) => updateFilter("status", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              <option value="">All statuses</option>
              {uniqueStatuses.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1.5">Search</label>
            <input
              type="text"
              placeholder="Filter by title…"
              value={filters.search}
              onChange={(e) => updateFilter("search", e.target.value)}
              className="w-full text-sm border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div className="flex items-end">
            {hasActiveFilter && (
              <button
                onClick={() => { setFilters(EMPTY_FILTERS); setPage(1); }}
                className="text-sm text-blue-600 hover:text-blue-800 underline pb-2"
              >
                Clear filters
              </button>
            )}
          </div>
        </div>
        {hasActiveFilter && (
          <p className="text-xs text-slate-500 mt-2">
            Showing {filtered.length} of {allExpenses.length} expenses
          </p>
        )}
      </div>

      {/* Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <table className="w-full text-sm" aria-label="All tenant expenses">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-100">
              {["ID", "Title", "Owner", "Submitted", "Amount", "Status"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading
              ? Array.from({ length: PAGE_SIZE }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-5 py-3.5">
                        <div className="h-4 bg-slate-100 rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))
              : pageRows.length > 0
              ? pageRows.map((e) => {
                  const isDelegated = e.createdBy && e.userId && e.createdBy !== e.userId;
                  return (
                    <tr key={e.id} className={`hover:bg-slate-50/70 transition-colors ${isDelegated ? "bg-amber-50/10" : ""}`}>
                      <td className="px-5 py-3.5 font-mono text-xs">
                        <Link href={`/expense/${e.id}`} className="text-blue-600 hover:underline">
                          {e.id.slice(0, 8).toUpperCase()}
                        </Link>
                        {isDelegated && (
                          <span className="ml-2 text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-sans font-medium">
                            delegated
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 font-medium text-slate-800">{e.title}</td>
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-500">{e.userId?.slice(0, 12) ?? "—"}</td>
                      <td className="px-5 py-3.5 text-slate-500 text-xs">
                        {e.submissionDate ? formatDate(e.submissionDate) : <span className="italic text-slate-400">Not submitted</span>}
                      </td>
                      <td className="px-5 py-3.5 font-medium text-emerald-700">{formatAmount(e.totalAmount, e.currency)}</td>
                      <td className="px-5 py-3.5"><StatusBadge status={e.status} /></td>
                    </tr>
                  );
                })
              : (
                <tr>
                  <td colSpan={6} className="px-5 py-12 text-center text-slate-400">
                    {hasActiveFilter ? "No expenses match the current filters." : "No expenses found."}
                  </td>
                </tr>
              )}
          </tbody>
        </table>

        {/* Pagination footer */}
        <div className="px-5 py-3 border-t border-slate-100 bg-slate-50 flex items-center justify-between">
          <span className="text-xs text-slate-400">
            {loading
              ? "Loading…"
              : `Showing ${filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–${Math.min(page * PAGE_SIZE, filtered.length)} of ${filtered.length} expenses`}
          </span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1 || loading}
              className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900 border border-slate-200 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-100 transition-colors"
            >
              ← Prev
            </button>
            <span className="px-3 py-1.5 text-xs text-slate-500">Page {page} of {totalPages}</span>
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
    </div>
  );
}
