"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { AuditTrail, type AuditEvent } from "@/components/shared/AuditTrail";
import { IdentityContextPanel } from "@/components/shared/IdentityContextPanel";
import { getExpense } from "@/lib/api/bff";
import { approveExpense, rejectExpense, submitExpense, getExpenseAudit, type ExpenseAudit } from "@/lib/api/gateway";
import { setAccessToken } from "@/lib/api/client";
import type { Expense, ExpenseCategory } from "@/lib/types/expense";

const CATEGORY_BADGE: Record<ExpenseCategory, string> = {
  TRAVEL:         "bg-sky-50 text-sky-700",
  ACCOMMODATION:  "bg-violet-50 text-violet-700",
  MEALS:          "bg-emerald-50 text-emerald-700",
  TRANSPORTATION: "bg-blue-50 text-blue-700",
  OTHER:          "bg-slate-100 text-slate-600",
};

const CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  TRAVEL:         "Travel",
  ACCOMMODATION:  "Accommodation",
  MEALS:          "Meals",
  TRANSPORTATION: "Transportation",
  OTHER:          "Other",
};

function formatDate(iso: string | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit",
  });
}

function formatAmount(amount: number, currency = "INR"): string {
  if (currency === "INR") return `₹${amount.toLocaleString("en-IN")}`;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
}

function auditEntryToEvent(entry: ExpenseAudit): AuditEvent {
  const actor   = entry.actorId ?? "unknown";
  const subject = entry.subjectId ?? entry.actorId ?? "unknown";
  const hasSub  = entry.subjectId && entry.subjectId !== actor;
  const ts      = formatDateTime(entry.timestamp);

  switch (entry.action) {
    case "CREATE":
      return {
        label:     "Expense report created",
        timestamp: ts,
        detail:    `Actor: ${actor}${hasSub ? ` · Subject: ${subject}` : ""}`,
        color:     "blue",
      };
    case "SUBMIT":
      return {
        label:     "Submitted for approval",
        timestamp: ts,
        detail:    `Actor: ${actor}${hasSub ? ` · Subject: ${subject}` : ""}`,
        color:     "amber",
      };
    case "APPROVE":
      return {
        label:     "Expense approved",
        timestamp: ts,
        detail:    `Approver: ${actor}`,
        color:     "emerald",
      };
    case "REJECT":
      return {
        label:     "Expense rejected",
        timestamp: ts,
        detail:    `Reviewer: ${actor}`,
        color:     "red",
      };
    case "ACTIVATE_DELEGATION":
      return {
        label:     "Delegation activated",
        timestamp: ts,
        detail:    entry.delegationId ? `${entry.delegationId} · Token exchange completed` : "Token exchange completed",
        color:     "violet",
      };
    default:
      return {
        label:     entry.action.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
        timestamp: ts,
        detail:    `Actor: ${actor}`,
        color:     "slate",
      };
  }
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-3 px-5 py-3 text-sm">
      <span className="text-slate-500">{label}</span>
      <span className="col-span-2">{children}</span>
    </div>
  );
}

function SkeletonBlock({ className }: { className?: string }) {
  return <div className={`bg-slate-100 rounded animate-pulse ${className ?? "h-4 w-3/4"}`} />;
}

function getInitials(name: string): string {
  return name
    .split(/[\s.]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

export default function ExpenseDetailPage() {
  const params = useParams<{ id: string }>();
  const { data: session } = useSession();

  const [expense, setExpense]       = useState<Expense | null>(null);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading]       = useState(true);
  const [notFound, setNotFound]     = useState(false);
  const [actionError, setActionError] = useState("");
  const [actionLoading, setActionLoading] = useState<"approve" | "reject" | "submit" | null>(null);

  const roles: string[] = (session as { user?: { roles?: string[] } })?.user?.roles ?? [];
  const isManager = roles.includes("manager") || roles.includes("admin");

  useEffect(() => {
    if (!session?.accessToken || !params.id) return;
    setAccessToken(session.accessToken);

    async function load() {
      try {
        const exp = await getExpense(params.id);
        setExpense(exp);

        try {
          const audit = await getExpenseAudit(params.id);
          const events = audit
            .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
            .map(auditEntryToEvent);
          setAuditEvents(events);
        } catch {
          // Audit unavailable — show empty timeline
        }
      } catch (err: unknown) {
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 404) setNotFound(true);
      } finally {
        setLoading(false);
      }
    }

    load();
  }, [session?.accessToken, params.id]);

  async function handleApprove() {
    if (!session?.accessToken || !expense) return;
    setAccessToken(session.accessToken);
    setActionError("");
    setActionLoading("approve");
    try {
      const updated = await approveExpense(expense.id);
      setExpense(updated);
    } catch {
      setActionError("Failed to approve expense. Please try again.");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleReject() {
    if (!session?.accessToken || !expense) return;
    setAccessToken(session.accessToken);
    setActionError("");
    setActionLoading("reject");
    try {
      const updated = await rejectExpense(expense.id);
      setExpense(updated);
    } catch {
      setActionError("Failed to reject expense. Please try again.");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleSubmit() {
    if (!session?.accessToken || !expense) return;
    setAccessToken(session.accessToken);
    setActionError("");
    setActionLoading("submit");
    try {
      const updated = await submitExpense(expense.id);
      setExpense(updated);
    } catch {
      setActionError("Failed to submit expense. Please try again.");
    } finally {
      setActionLoading(null);
    }
  }

  if (notFound) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <p className="text-slate-500 text-sm">Expense report not found.</p>
        <Link href="/expense" className="text-sm text-blue-600 hover:underline">← Back to My Expenses</Link>
      </div>
    );
  }

  const isDelegated = Boolean(expense?.createdBy && expense?.userId && expense?.createdBy !== expense?.userId);

  return (
    <div className="space-y-5">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-slate-400">
        <Link href="/expense" className="hover:text-slate-700 transition-colors">My Expenses</Link>
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-slate-700 font-mono font-medium">
          {loading ? "…" : expense?.id ?? params.id}
        </span>
      </nav>

      <div className="max-w-3xl space-y-5">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2.5 flex-wrap mb-1">
              {loading ? (
                <SkeletonBlock className="h-6 w-64" />
              ) : (
                <>
                  <h1 className="text-xl font-semibold text-slate-900">{expense?.title}</h1>
                  {expense && <StatusBadge status={expense.status} />}
                  {isDelegated && (
                    <span className="inline-flex items-center gap-1 text-xs font-medium bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-md">
                      <svg className="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                      </svg>
                      via delegate
                    </span>
                  )}
                </>
              )}
            </div>
            {loading ? (
              <SkeletonBlock className="h-4 w-64 mt-1" />
            ) : expense ? (
              <p className="text-xs text-slate-400 font-mono">
                {expense.id}
                {expense.totalAmount ? ` · ${formatAmount(expense.totalAmount, expense.currency)}` : ""}
                {expense.bookingId ? ` · Linked: ${expense.bookingId.length > 12 ? expense.bookingId.slice(0, 12).toUpperCase() : expense.bookingId}` : ""}
              </p>
            ) : null}
          </div>
        </div>

        {actionError && (
          <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
            {actionError}
          </div>
        )}

        {/* Report Details */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100">
            <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Report Details</h2>
          </div>
          <div className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="grid grid-cols-3 px-5 py-3">
                  <SkeletonBlock className="h-4 w-24" />
                  <div className="col-span-2"><SkeletonBlock className="h-4 w-48" /></div>
                </div>
              ))
            ) : expense ? (
              <>
                <DetailRow label="Title">
                  <span className="font-medium text-slate-800">{expense.title}</span>
                </DetailRow>
                {expense.description && (
                  <DetailRow label="Description">
                    <span className="text-slate-600">{expense.description}</span>
                  </DetailRow>
                )}
                {expense.bookingId && (
                  <DetailRow label="Linked Trip">
                    <Link href={`/travel/${expense.bookingId}`} className="text-xs font-mono text-blue-600 hover:underline">
                      {expense.bookingId.length > 12 ? expense.bookingId.slice(0, 12).toUpperCase() : expense.bookingId}
                    </Link>
                  </DetailRow>
                )}
                <DetailRow label="Total Amount">
                  <span className="font-semibold text-slate-900">{formatAmount(expense.totalAmount, expense.currency)}</span>
                </DetailRow>
                {expense.submissionDate && (
                  <DetailRow label="Submitted">
                    <span className="text-slate-700">{formatDateTime(expense.submissionDate)}</span>
                  </DetailRow>
                )}
                {expense.approverId && (
                  <DetailRow label="Approver">
                    <span className="text-slate-700">
                      {expense.approverId}
                      {expense.status === "SUBMITTED" && <span className="ml-2 text-xs text-slate-400">(pending)</span>}
                    </span>
                  </DetailRow>
                )}
              </>
            ) : null}
          </div>
        </div>

        {/* Expense Items */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-3.5 border-b border-slate-100">
            <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Expense Items</h2>
          </div>
          <table className="w-full text-sm" aria-label="Expense line items">
            <thead className="bg-slate-50 text-xs text-slate-500 uppercase tracking-wide">
              <tr>
                <th className="px-5 py-3 text-left font-medium">#</th>
                <th className="px-5 py-3 text-left font-medium">Category</th>
                <th className="px-5 py-3 text-left font-medium">Description</th>
                <th className="px-5 py-3 text-left font-medium">Date</th>
                <th className="px-5 py-3 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 5 }).map((_, j) => (
                      <td key={j} className="px-5 py-3.5">
                        <SkeletonBlock className="h-4 w-3/4" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : expense?.items?.length ? (
                expense.items.map((item, i) => (
                  <tr key={item.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-5 py-3.5 text-slate-400 text-xs">{i + 1}</td>
                    <td className="px-5 py-3.5">
                      <span className={`inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-md ${CATEGORY_BADGE[item.category] ?? "bg-slate-100 text-slate-600"}`}>
                        {CATEGORY_LABEL[item.category] ?? item.category}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-slate-800">{item.description}</td>
                    <td className="px-5 py-3.5 text-slate-500 text-xs">{formatDate(item.date)}</td>
                    <td className="px-5 py-3.5 text-slate-800 text-right font-medium">
                      {formatAmount(item.amount, expense.currency)}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5} className="px-5 py-6 text-center text-sm text-slate-400">No items.</td>
                </tr>
              )}
            </tbody>
            {!loading && expense && (expense.items?.length ?? 0) > 0 && (
              <tfoot className="border-t-2 border-slate-200">
                <tr className="bg-slate-50">
                  <td colSpan={4} className="px-5 py-3 text-sm font-semibold text-slate-600 text-right">Total</td>
                  <td className="px-5 py-3 text-sm font-bold text-slate-900 text-right">
                    {formatAmount(expense.totalAmount, expense.currency)}
                  </td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>

        {/* Approval Chain */}
        {!loading && expense && (
          <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-100">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Approval Chain</h2>
            </div>
            <div className="px-5 py-4 space-y-4">
              {expense.approverId ? (
                <div className="flex items-center gap-4">
                  <div className="w-9 h-9 rounded-full bg-amber-100 text-amber-700 flex items-center justify-center text-xs font-bold flex-shrink-0">
                    {getInitials(expense.approverId)}
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-medium text-slate-800">
                      {expense.approverId.split(".").map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(" ")}
                    </p>
                    <p className="text-xs text-slate-400 mt-0.5">
                      {expense.status === "SUBMITTED"
                        ? `Awaiting review · Submitted ${formatDate(expense.submissionDate)}`
                        : expense.status === "APPROVED"
                        ? `Approved ${formatDate(expense.approvalDate)}`
                        : expense.status === "REJECTED"
                        ? `Rejected ${formatDate(expense.approvalDate)}`
                        : "Pending review"}
                    </p>
                  </div>
                  <StatusBadge status={expense.status === "SUBMITTED" ? "SUBMITTED" : expense.status} />
                </div>
              ) : (
                <p className="text-sm text-slate-400">No approver assigned yet.</p>
              )}

              {/* Draft submit action */}
              {expense.status === "DRAFT" && (
                <div className="pt-2 border-t border-slate-100">
                  <button
                    onClick={handleSubmit}
                    disabled={actionLoading === "submit"}
                    className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors disabled:opacity-60"
                  >
                    {actionLoading === "submit" ? "Submitting…" : "Submit for Approval"}
                  </button>
                </div>
              )}

              {/* Manager approve/reject — role-based, not username-based */}
              {isManager && expense.status === "SUBMITTED" && (
                <div className="pt-2 border-t border-slate-100 flex items-center gap-3">
                  <button
                    onClick={handleApprove}
                    disabled={actionLoading !== null}
                    className="bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors disabled:opacity-60"
                  >
                    {actionLoading === "approve" ? "Approving…" : "Approve"}
                  </button>
                  <button
                    onClick={handleReject}
                    disabled={actionLoading !== null}
                    className="border border-red-300 hover:bg-red-50 text-red-600 text-sm font-medium px-4 py-2 rounded-lg transition-colors disabled:opacity-60"
                  >
                    {actionLoading === "reject" ? "Rejecting…" : "Reject"}
                  </button>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Identity & Audit Trail (only when delegated) */}
        {!loading && isDelegated && expense && (
          <IdentityContextPanel
            delegated
            rows={[
              { label: "userId (subject)", value: expense.userId, valueColor: "amber", note: "report owner" },
              { label: "createdBy (actor)", value: expense.createdBy, valueColor: "blue", note: "submitted on their behalf" },
              { label: "tenantId", value: expense.tenantId },
              { label: "purpose", value: "approve_expenses" },
            ]}
            footer="Headers forwarded to expense-service: X-Delegated-Subject, X-Delegation-Id, X-Actor-Token (ADR-004 · ADR-011)"
          />
        )}

        {/* Event Timeline */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="text-sm font-semibold text-slate-800">Event Timeline</h2>
          </div>
          {loading ? (
            <div className="px-5 py-5 space-y-4">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex gap-4">
                  <div className="w-2.5 h-2.5 rounded-full bg-slate-100 ring-4 ring-slate-50 mt-0.5 flex-shrink-0" />
                  <div className="space-y-1 flex-1">
                    <SkeletonBlock className="h-4 w-40" />
                    <SkeletonBlock className="h-3 w-64" />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <AuditTrail events={auditEvents} />
          )}
        </div>
      </div>
    </div>
  );
}
