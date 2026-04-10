"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { AuditTrail, type AuditEvent } from "@/components/shared/AuditTrail";
import { IdentityContextPanel } from "@/components/shared/IdentityContextPanel";
import { getBooking } from "@/lib/api/bff";
import { gatewayClient } from "@/lib/api/client";
import { setAccessToken } from "@/lib/api/client";
import type { Booking, BookingAudit } from "@/lib/types/booking";

const TYPE_BADGE: Record<string, string> = {
  FLIGHT: "text-sky-700 bg-sky-50 border border-sky-200",
  HOTEL:  "text-violet-700 bg-violet-50 border border-violet-200",
  CAR:    "text-teal-700 bg-teal-50 border border-teal-200",
};

function formatDate(iso: string): string {
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

function auditActionToEvent(entry: BookingAudit): AuditEvent {
  const actor = entry.actorId ?? "unknown";
  const subject = entry.subjectId ?? entry.actorId ?? "unknown";

  switch (entry.action) {
    case "CREATE_BOOKING":
      return {
        label: "Booking created",
        timestamp: formatDateTime(entry.createdAt),
        detail: `Actor: ${actor}${entry.subjectId && entry.subjectId !== actor ? ` · Subject: ${subject}` : ""}`,
        color: "emerald",
      };
    case "ACTIVATE_DELEGATION":
      return {
        label: "Delegation mode activated",
        timestamp: formatDateTime(entry.createdAt),
        detail: "Token exchange completed for audience: travel-service",
        color: "blue",
      };
    case "VIEW_BOOKINGS":
      return {
        label: "Bookings viewed",
        timestamp: formatDateTime(entry.createdAt),
        detail: `Actor: ${actor}`,
        color: "slate",
      };
    default:
      return {
        label: entry.action.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
        timestamp: formatDateTime(entry.createdAt),
        detail: `Actor: ${actor}`,
        color: "slate",
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

export default function BookingDetailPage() {
  const params = useParams<{ id: string }>();
  const { data: session } = useSession();

  const [booking, setBooking] = useState<Booking | null>(null);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!session?.accessToken || !params.id) return;
    setAccessToken(session.accessToken);

    async function load() {
      try {
        const b = await getBooking(params.id);
        setBooking(b);

        // Fetch audit trail from gateway (best-effort; ignore if unavailable)
        try {
          const auditRes = await gatewayClient.get<BookingAudit[]>(`/api/bookings/${params.id}/audit`);
          const events = auditRes.data
            .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
            .map(auditActionToEvent);
          setAuditEvents(events);
        } catch {
          // Audit trail unavailable — show empty timeline
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

  if (notFound) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <p className="text-slate-500 text-sm">Booking not found.</p>
        <Link href="/travel" className="text-sm text-blue-600 hover:underline">← Back to My Trips</Link>
      </div>
    );
  }

  const isDelegated = Boolean(booking?.delegationId);

  return (
    <div className="space-y-5">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-slate-400">
        <Link href="/travel" className="hover:text-slate-700 transition-colors">My Trips</Link>
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-slate-700 font-mono font-medium">
          {loading ? "…" : booking?.id ?? params.id}
        </span>
      </nav>

      <div className="max-w-3xl space-y-5">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3 flex-wrap">
              {loading ? (
                <SkeletonBlock className="h-6 w-64" />
              ) : (
                <>
                  <h1 className="text-xl font-semibold text-slate-900">
                    {booking?.destination} — {booking?.businessPurpose ?? "Trip"}
                  </h1>
                  {booking && <StatusBadge status={booking.status} />}
                </>
              )}
            </div>
            {loading ? (
              <SkeletonBlock className="h-4 w-48 mt-1" />
            ) : booking ? (
              <p className="text-sm text-slate-400 font-mono mt-1">
                {booking.id} · {booking.bookingType.charAt(0) + booking.bookingType.slice(1).toLowerCase()} · {formatDate(booking.startDate)} – {formatDate(booking.endDate)}
              </p>
            ) : null}
          </div>
          {booking && (
            <Link
              href={`/expense/submit?bookingId=${booking.id}`}
              className="flex items-center gap-2 bg-white border border-slate-300 hover:border-slate-400 text-slate-700 text-sm font-medium px-3.5 py-2 rounded-lg transition-colors flex-shrink-0"
            >
              Submit Expense
            </Link>
          )}
        </div>

        {/* Booking Details Card */}
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100">
            <h2 className="text-sm font-semibold text-slate-800">Booking Details</h2>
          </div>
          <div className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="grid grid-cols-3 px-5 py-3">
                  <SkeletonBlock className="h-4 w-24" />
                  <div className="col-span-2"><SkeletonBlock className="h-4 w-40" /></div>
                </div>
              ))
            ) : booking ? (
              <>
                <DetailRow label="Booking type">
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${TYPE_BADGE[booking.bookingType] ?? ""}`}>
                    {booking.bookingType.charAt(0) + booking.bookingType.slice(1).toLowerCase()}
                  </span>
                </DetailRow>
                <DetailRow label="Source">
                  <span className="text-slate-500 text-sm">Office HQ — Mumbai, India</span>
                </DetailRow>
                <DetailRow label="Destination">
                  <span className="font-medium text-slate-800 text-sm">{booking.destination}</span>
                </DetailRow>
                <DetailRow label="Travel dates">
                  <span className="text-slate-700 text-sm">{formatDate(booking.startDate)} → {formatDate(booking.endDate)}</span>
                </DetailRow>
                <DetailRow label="Total amount">
                  <span className="font-semibold text-slate-900 text-sm">{formatAmount(booking.totalAmount, booking.currency)}</span>
                </DetailRow>
                {booking.businessPurpose && (
                  <DetailRow label="Business purpose">
                    <span className="text-slate-700 text-sm">{booking.businessPurpose}</span>
                  </DetailRow>
                )}
                <DetailRow label="Created">
                  <span className="text-slate-700 text-sm">{formatDateTime(booking.createdAt)}</span>
                </DetailRow>
              </>
            ) : null}
          </div>
        </div>

        {/* Identity & Audit Trail (only when delegated) */}
        {!loading && isDelegated && booking && (
          <IdentityContextPanel
            delegated
            rows={[
              { label: "userId (subject)", value: booking.userId, valueColor: "amber", note: "trip owner" },
              { label: "createdBy (actor)", value: booking.createdBy, valueColor: "blue", note: "performed the action" },
              { label: "delegationId", value: booking.delegationId },
              { label: "tenantId", value: booking.tenantId },
              { label: "audience", value: "travel-service" },
              { label: "purpose", value: booking.details ? (() => { try { return (JSON.parse(booking.details!) as { purpose?: string }).purpose ?? null; } catch { return null; } })() : null },
            ]}
            footer="Headers forwarded: X-Delegated-Subject · X-Delegation-Id · X-Actor-Token (ADR-004 · ADR-011)"
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
