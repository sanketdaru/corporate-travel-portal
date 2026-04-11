"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { getExpenses } from "@/lib/api/bff";
import { setAccessToken } from "@/lib/api/client";
import type { Expense, ExpenseStatus } from "@/lib/types/expense";

const PAGE_SIZE = 10;

type StatusFilter = "ALL" | ExpenseStatus;
const STATUS_TABS: { value: StatusFilter; label: string }[] = [
  { value: "ALL",       label: "All" },
  { value: "DRAFT",     label: "Draft" },
  { value: "SUBMITTED", label: "Submitted" },
  { value: "APPROVED",  label: "Approved" },
  { value: "REJECTED",  label: "Rejected" },
];

function formatDate(iso: string | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

function formatAmount(amount: number, currency = "INR"): string {
  if (currency === "INR") return `₹${amount.toLocaleString("en-IN")}`;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 4 }).map((_, i) => (
        <tr key={i}>
          {Array.from({ length: 8 }).map((_, j) => (
            <td key={j} className="px-5 py-3.5">
              <div className="h-4 bg-slate-100 rounded animate-pulse w-3/4" />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

export default function ExpensePage() {
  const { data: session } = useSession();
  const { delegationActive, subjectName } = useDelegationContext();

  const [allExpenses, setAllExpenses] = useState<Expense[]>([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState(false);

  // Filters
  const [statusTab, setStatusTab]   = useState<StatusFilter>("ALL");
  const [search, setSearch]         = useState("");
  const [monthFilter, setMonthFilter] = useState("");

  // Pagination
  const [page, setPage] = useState(1);

  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);

    setLoading(true);
    setError(false);
    getExpenses()
      .then((data) => setAllExpenses(data))
      .catch(() => { setError(true); setAllExpenses([]); })
      .finally(() => setLoading(false));
  }, [session?.accessToken]);

  const expenses = useMemo(() => {
    return allExpenses.filter((e) => {
      if (statusTab !== "ALL" && e.status !== statusTab) return false;
      if (search && !e.title.toLowerCase().includes(search.toLowerCase())) return false;
      if (monthFilter) {
        const month = (e.submissionDate ?? e.createdAt)?.slice(0, 7);
        if (month !== monthFilter) return false;
      }
      return true;
    });
  }, [allExpenses, statusTab, search, monthFilter]);

  const totalPages = Math.max(1, Math.ceil(expenses.length / PAGE_SIZE));
  const paginated  = expenses.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  // Summary totals over ALL expenses (not filtered)
  const totalSubmitted  = allExpenses.reduce((s, e) => s + e.totalAmount, 0);
  const totalApproved   = allExpenses.filter((e) => e.status === "APPROVED" || e.status === "PAID").reduce((s, e) => s + e.totalAmount, 0);
  const totalPending    = allExpenses.filter((e) => e.status === "SUBMITTED").reduce((s, e) => s + e.totalAmount, 0);
  const totalDraft      = allExpenses.filter((e) => e.status === "DRAFT").reduce((s, e) => s + e.totalAmount, 0);

  const delegatedCount = allExpenses.filter((e) => e.createdBy && e.userId && e.createdBy !== e.userId).length;

  function handleStatusTab(v: StatusFilter) {
    setStatusTab(v);
    setPage(1);
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">My Expenses</h1>
          {delegationActive && subjectName ? (
            <p className="text-sm text-amber-700 mt-0.5 flex items-center gap-1.5">
              <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Showing expenses for <strong className="mx-1">{subjectName}</strong>
            </p>
          ) : (
            <p className="text-sm text-slate-400 mt-0.5">
              {new Date().toLocaleDateString("en-IN", { month: "long", year: "numeric" })}
            </p>
          )}
        </div>
        <Link
          href="/expense/submit"
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Report
        </Link>
      </div>

      {/* Summary strip */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-white border border-slate-200 rounded-xl p-4 text-center">
          <p className="text-xl font-bold text-slate-900">
            {loading ? <span className="inline-block h-6 w-24 bg-slate-100 rounded animate-pulse" /> : formatAmount(totalSubmitted)}
          </p>
          <p className="text-xs text-slate-400 mt-1">Total submitted</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 text-center">
          <p className="text-xl font-bold text-emerald-600">
            {loading ? <span className="inline-block h-6 w-24 bg-slate-100 rounded animate-pulse" /> : formatAmount(totalApproved)}
          </p>
          <p className="text-xs text-slate-400 mt-1">Approved &amp; paid</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 text-center">
          <p className="text-xl font-bold text-amber-600">
            {loading ? <span className="inline-block h-6 w-24 bg-slate-100 rounded animate-pulse" /> : formatAmount(totalPending)}
          </p>
          <p className="text-xs text-slate-400 mt-1">Pending approval</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 text-center">
          <p className="text-xl font-bold text-slate-400">
            {loading ? <span className="inline-block h-6 w-24 bg-slate-100 rounded animate-pulse" /> : formatAmount(totalDraft)}
          </p>
          <p className="text-xs text-slate-400 mt-1">Draft</p>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white border border-slate-200 rounded-xl p-4 flex flex-wrap gap-3 items-center">
        {/* Segmented status toggle */}
        <div className="flex rounded-lg border border-slate-300 overflow-hidden text-xs">
          {STATUS_TABS.map((tab) => (
            <button
              key={tab.value}
              onClick={() => handleStatusTab(tab.value)}
              className={`px-3 py-1.5 font-medium transition-colors ${
                statusTab === tab.value
                  ? "bg-slate-900 text-white"
                  : "text-slate-600 hover:bg-slate-50"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <input
          type="text"
          placeholder="Search reports…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1); }}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent w-48 transition"
        />

        <input
          type="month"
          value={monthFilter}
          onChange={(e) => { setMonthFilter(e.target.value); setPage(1); }}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
        />

        {(search || monthFilter || statusTab !== "ALL") && (
          <button
            onClick={() => { setSearch(""); setMonthFilter(""); setStatusTab("ALL"); setPage(1); }}
            className="text-sm text-slate-400 hover:text-slate-600 transition-colors"
          >
            Reset
          </button>
        )}
      </div>

      {/* Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        {error && (
          <div className="px-5 py-3 bg-red-50 border-b border-red-100 text-xs text-red-600 flex items-center gap-1.5">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Could not load expenses — service may be unavailable.
          </div>
        )}

        <table className="w-full" aria-label="Expense reports">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Report", "Title", "Trip", "Items", "Total", "Submitted", "Status", ""].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <TableSkeleton />
            ) : paginated.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-5 py-10 text-center">
                  <p className="text-sm text-slate-400 mb-3">No expense reports found.</p>
                  <Link
                    href="/expense/submit"
                    className="inline-flex items-center gap-1.5 text-xs font-medium text-blue-600 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition-colors"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    Submit your first report
                  </Link>
                </td>
              </tr>
            ) : (
              paginated.map((exp) => {
                const isDelegated = Boolean(exp.createdBy && exp.userId && exp.createdBy !== exp.userId);
                return (
                  <tr
                    key={exp.id}
                    className={`transition-colors ${isDelegated ? "bg-amber-50/10 hover:bg-amber-50/30" : "hover:bg-slate-50/70"}`}
                  >
                    <td className="px-5 py-3.5">
                      <Link href={`/expense/${exp.id}`} className="text-xs font-mono text-blue-600 hover:underline">
                        {exp.id.length > 12 ? exp.id.slice(0, 12).toUpperCase() : exp.id}
                      </Link>
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-slate-800">{exp.title}</span>
                        {isDelegated && (
                          <span className="inline-flex items-center gap-1 text-xs font-medium bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-md flex-shrink-0">
                            <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                            </svg>
                            via delegate
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      {exp.bookingId ? (
                        <Link href={`/travel/${exp.bookingId}`} className="text-xs font-mono text-blue-600 hover:underline">
                          {exp.bookingId.length > 12 ? exp.bookingId.slice(0, 12).toUpperCase() : exp.bookingId}
                        </Link>
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </td>
                    <td className="px-5 py-3.5 text-sm text-slate-500">{exp.items?.length ?? 0}</td>
                    <td className="px-5 py-3.5 text-sm font-medium text-slate-800">
                      {formatAmount(exp.totalAmount, exp.currency)}
                    </td>
                    <td className="px-5 py-3.5 text-sm text-slate-500">
                      {exp.submissionDate ? formatDate(exp.submissionDate) : (
                        <span className="italic text-slate-400 text-xs">Not submitted</span>
                      )}
                    </td>
                    <td className="px-5 py-3.5"><StatusBadge status={exp.status} /></td>
                    <td className="px-5 py-3.5">
                      {exp.status === "DRAFT" ? (
                        <div className="flex items-center gap-2">
                          <Link href={`/expense/${exp.id}`} className="text-xs font-medium text-blue-600 hover:text-blue-700">Edit</Link>
                          <span className="text-slate-200">|</span>
                          <Link href={`/expense/${exp.id}`} className="text-xs font-medium text-emerald-600 hover:text-emerald-700">Submit</Link>
                        </div>
                      ) : (
                        <Link href={`/expense/${exp.id}`} className="text-xs font-medium text-blue-600 hover:text-blue-700">
                          View →
                        </Link>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        {/* Footer */}
        <div className="px-5 py-3.5 border-t border-slate-100 bg-slate-50/50 flex items-center justify-between">
          <span className="text-xs text-slate-500">
            {loading
              ? "Loading…"
              : `${expenses.length} expense report${expenses.length !== 1 ? "s" : ""}`}
          </span>
          <div className="flex items-center gap-4">
            {!loading && delegatedCount > 0 && (
              <span className="text-xs text-amber-600">
                {delegatedCount} report{delegatedCount !== 1 ? "s" : ""} submitted via delegation
              </span>
            )}
            {totalPages > 1 && (
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page === 1}
                  className="px-3 py-1.5 text-xs font-medium border border-slate-300 rounded-lg hover:bg-white transition-colors text-slate-600 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Previous
                </button>
                {Array.from({ length: totalPages }).map((_, i) => (
                  <button
                    key={i}
                    onClick={() => setPage(i + 1)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                      page === i + 1
                        ? "bg-blue-600 text-white"
                        : "border border-slate-300 hover:bg-white text-slate-600"
                    }`}
                  >
                    {i + 1}
                  </button>
                ))}
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page === totalPages}
                  className="px-3 py-1.5 text-xs font-medium border border-slate-300 rounded-lg hover:bg-white transition-colors text-slate-600 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
