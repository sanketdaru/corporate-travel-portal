"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { getBookings } from "@/lib/api/bff";
import { setAccessToken } from "@/lib/api/client";
import type { Booking, BookingStatus } from "@/lib/types/booking";

const PAGE_SIZE = 10;

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

function formatBudget(amount: number, currency = "INR"): string {
  if (currency === "INR") return `₹${amount.toLocaleString("en-IN")}`;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <tr key={i}>
          {Array.from({ length: 7 }).map((_, j) => (
            <td key={j} className="px-5 py-3.5">
              <div className="h-4 bg-slate-100 rounded animate-pulse w-3/4" />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

export default function TravelPage() {
  const { data: session } = useSession();
  const { delegationActive, subjectName } = useDelegationContext();

  const [allBookings, setAllBookings] = useState<Booking[]>([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState(false);

  // Pending filter state — applied on "Filter" click
  const [pendingStatus,   setPendingStatus]   = useState("");
  const [pendingDateFrom, setPendingDateFrom] = useState("");
  const [pendingDateTo,   setPendingDateTo]   = useState("");

  // Applied filter state — drives actual filtering
  const [appliedStatus,   setAppliedStatus]   = useState("");
  const [appliedDateFrom, setAppliedDateFrom] = useState("");
  const [appliedDateTo,   setAppliedDateTo]   = useState("");
  const [filtersActive,   setFiltersActive]   = useState(false);

  const [page, setPage] = useState(1);

  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);
    setLoading(true);
    setError(false);
    getBookings()
      .then((data) => setAllBookings(data))
      .catch(() => { setError(true); setAllBookings([]); })
      .finally(() => setLoading(false));
  }, [session?.accessToken]);

  function applyFilters() {
    setAppliedStatus(pendingStatus);
    setAppliedDateFrom(pendingDateFrom);
    setAppliedDateTo(pendingDateTo);
    setFiltersActive(!!(pendingStatus || pendingDateFrom || pendingDateTo));
    setPage(1);
  }

  function resetFilters() {
    setPendingStatus(""); setPendingDateFrom(""); setPendingDateTo("");
    setAppliedStatus(""); setAppliedDateFrom(""); setAppliedDateTo("");
    setFiltersActive(false);
    setPage(1);
  }

  const bookings = allBookings.filter((b) => {
    if (appliedStatus   && b.status    !== (appliedStatus as BookingStatus)) return false;
    if (appliedDateFrom && b.startDate <  appliedDateFrom)                   return false;
    if (appliedDateTo   && b.endDate   >  appliedDateTo)                     return false;
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(bookings.length / PAGE_SIZE));
  const paginated  = bookings.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Travel Authorizations</h1>
          {delegationActive && subjectName ? (
            <p className="text-sm text-amber-700 mt-0.5 flex items-center gap-1.5">
              <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Showing authorizations for <strong className="mx-1">{subjectName}</strong>
            </p>
          ) : (
            <p className="text-sm text-slate-400 mt-0.5">Your approved travel authorizations</p>
          )}
        </div>
        <Link
          href="/travel/book"
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-3.5 py-2 rounded-lg transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          {delegationActive && subjectName ? `Authorize for ${subjectName}` : "New Authorization"}
        </Link>
      </div>

      {/* Filters */}
      <div className="bg-white border border-slate-200 rounded-xl p-4 flex flex-wrap gap-3 items-center">
        <select
          value={pendingStatus}
          onChange={(e) => setPendingStatus(e.target.value)}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="PENDING">Pending</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>

        <span className="text-xs text-slate-400">Travel from</span>
        <input
          type="date"
          value={pendingDateFrom}
          onChange={(e) => setPendingDateFrom(e.target.value)}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <span className="text-xs text-slate-400">to</span>
        <input
          type="date"
          value={pendingDateTo}
          onChange={(e) => setPendingDateTo(e.target.value)}
          className="border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />

        <button
          onClick={applyFilters}
          className="bg-slate-900 hover:bg-slate-800 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors"
        >
          Filter
        </button>
        {filtersActive && (
          <button onClick={resetFilters} className="text-sm text-slate-500 hover:text-slate-700 transition-colors">
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
            Could not load travel authorizations — service may be unavailable.
          </div>
        )}

        <table className="w-full" aria-label="Travel Authorizations">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Authorization", "Destination", "Travel Dates", "Purpose", "Budget", "Created By", "Status", ""].map((h) => (
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
                  <p className="text-sm text-slate-400 mb-3">No travel authorizations found.</p>
                  <Link
                    href="/travel/book"
                    className="inline-flex items-center gap-1.5 text-xs font-medium text-blue-600 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition-colors"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    Request your first authorization
                  </Link>
                </td>
              </tr>
            ) : (
              paginated.map((b) => {
                const isDelegate = b.createdBy && b.userId && b.createdBy !== b.userId;
                return (
                  <tr
                    key={b.id}
                    className={`transition-colors ${isDelegate ? "bg-amber-50/10 hover:bg-amber-50/30" : "hover:bg-slate-50/70"}`}
                  >
                    <td className="px-5 py-3.5">
                      <Link href={`/travel/${b.id}`} className="text-xs font-mono text-blue-600 hover:underline">
                        {b.id.slice(0, 12).toUpperCase()}
                      </Link>
                    </td>
                    <td className="px-5 py-3.5 text-sm font-medium text-slate-800">{b.destination}</td>
                    <td className="px-5 py-3.5 text-sm text-slate-500 whitespace-nowrap">
                      {formatDate(b.startDate)} – {formatDate(b.endDate)}
                    </td>
                    <td className="px-5 py-3.5 text-sm text-slate-600 max-w-[200px] truncate">
                      {b.businessPurpose ?? "—"}
                    </td>
                    <td className="px-5 py-3.5 text-sm font-medium text-slate-800">
                      {formatBudget(b.budget, b.budgetCurrency)}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-1.5">
                        <span className="text-sm text-slate-700">
                          {b.createdBy?.split(".").map((p: string) => p.charAt(0).toUpperCase() + p.slice(1)).join(" ") ?? "—"}
                        </span>
                        {isDelegate && (
                          <span className="inline-flex items-center gap-1 text-xs font-medium bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-md">
                            <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                            </svg>
                            delegate
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3.5"><StatusBadge status={b.status} /></td>
                    <td className="px-5 py-3.5">
                      <Link href={`/travel/${b.id}`} className="text-xs font-medium text-blue-600 hover:text-blue-700">
                        View →
                      </Link>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        {/* Pagination */}
        <div className="flex items-center justify-between px-5 py-3.5 border-t border-slate-100 bg-slate-50/50">
          <span className="text-xs text-slate-500">
            {loading
              ? "Loading…"
              : bookings.length === 0
              ? "No authorizations match the current filters"
              : `Showing ${Math.min((page - 1) * PAGE_SIZE + 1, bookings.length)}–${Math.min(page * PAGE_SIZE, bookings.length)} of ${bookings.length}${filtersActive ? " filtered" : ""} authorization${bookings.length !== 1 ? "s" : ""}${delegationActive && subjectName ? ` for ${subjectName}` : ""}`}
          </span>
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
                    page === i + 1 ? "bg-blue-600 text-white" : "border border-slate-300 hover:bg-white text-slate-600"
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
  );
}
