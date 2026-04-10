"use client";

import { useState, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { createBooking } from "@/lib/api/bff";
import { setAccessToken } from "@/lib/api/client";
import type { BookingType } from "@/lib/types/booking";

const SUGGESTED_DESTINATIONS = [
  "Bengaluru, India",
  "Shanghai, China",
  "Tokyo, Japan",
  "Sydney, Australia",
  "London, UK",
  "Berlin, Germany",
  "Paris, France",
  "Dubai, UAE",
  "New York, USA",
];

type BookingTypeOption = {
  value: BookingType;
  label: string;
  emoji: string;
};

const BOOKING_TYPES: BookingTypeOption[] = [
  { value: "FLIGHT", label: "Flight",     emoji: "✈️" },
  { value: "HOTEL",  label: "Hotel",      emoji: "🏨" },
  { value: "CAR",    label: "Car Rental", emoji: "🚗" },
];

interface FormErrors {
  destination?: string;
  startDate?: string;
  endDate?: string;
  businessPurpose?: string;
}

export default function BookTripPage() {
  const router   = useRouter();
  const { data: session } = useSession();
  const { delegationActive, subjectName, subjectId, delegationId } = useDelegationContext();

  const [bookingType, setBookingType]         = useState<BookingType>("FLIGHT");
  const [destination, setDestination]         = useState("");
  const [showSuggestions, setShowSuggestions] = useState(false);
  const destinationRef                        = useRef<HTMLDivElement>(null);
  const [startDate, setStartDate]             = useState("");
  const [endDate, setEndDate]                 = useState("");
  const [businessPurpose, setBusinessPurpose] = useState("");
  const [totalAmount, setTotalAmount]         = useState("");
  const [currency, setCurrency]               = useState("INR");
  const [notes, setNotes]                     = useState("");
  const [errors, setErrors]                   = useState<FormErrors>({});
  const [submitting, setSubmitting]           = useState(false);
  const [serverError, setServerError]         = useState("");

  function validate(): boolean {
    const e: FormErrors = {};
    if (!destination.trim()) e.destination = "Destination is required.";
    if (!startDate)           e.startDate    = "Departure date is required.";
    if (!endDate)             e.endDate      = "Return date is required.";
    else if (startDate && endDate < startDate) e.endDate = "Return date must be on or after departure date.";
    if (!businessPurpose.trim()) e.businessPurpose = "Business purpose is required.";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    if (!session?.accessToken) return;

    setAccessToken(session.accessToken);
    setSubmitting(true);
    setServerError("");

    try {
      const booking = await createBooking({
        bookingType,
        destination,
        startDate,
        endDate,
        totalAmount: totalAmount ? Number(totalAmount) : 0,
        // tenantId and userId are overwritten by the service from the JWT context,
        // but @Valid on the controller requires them to be non-null in the payload.
        tenantId: session?.user?.tenantId ?? "placeholder",
        userId:   session?.user?.email    ?? "placeholder",
        // status defaults to PENDING in the service, but @Valid requires it non-null.
        status: "PENDING",
        // businessPurpose, currency, notes stored in details JSON
        details: JSON.stringify({ businessPurpose, currency, notes: notes || undefined }),
      });
      router.push(`/travel/${booking.id}`);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403) {
        setServerError("You don't have permission to create this booking.");
      } else {
        setServerError("Failed to create booking. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const subjectDisplay = delegationActive && subjectName
    ? subjectName
    : session?.user?.name ?? "you";

  return (
    <div className="space-y-5">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-slate-400">
        <Link href="/travel" className="hover:text-slate-700 transition-colors">My Trips</Link>
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-slate-700 font-medium">Book a Trip</span>
      </nav>

      <div className="max-w-2xl space-y-6">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Book a New Trip</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            For: <strong className="text-slate-700">{subjectDisplay}</strong>
            {session?.user?.tenantId && ` · Tenant: ${session.user.tenantId}`}
          </p>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <div className="bg-white border border-slate-200 rounded-xl p-6 space-y-5">

            {/* Booking Type */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                Booking Type <span className="text-red-400">*</span>
              </label>
              <div className="grid grid-cols-3 gap-3">
                {BOOKING_TYPES.map((t) => (
                  <label key={t.value} className="cursor-pointer">
                    <input
                      type="radio"
                      name="bookingType"
                      value={t.value}
                      checked={bookingType === t.value}
                      onChange={() => setBookingType(t.value)}
                      className="sr-only"
                    />
                    <div
                      className={`border-2 rounded-xl p-3.5 text-center text-sm font-medium transition-colors ${
                        bookingType === t.value
                          ? "border-blue-500 bg-blue-50 text-blue-700"
                          : "border-slate-200 text-slate-600 hover:border-slate-300"
                      }`}
                    >
                      {t.emoji} {t.label}
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Source (read-only) */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Source
              </label>
              <div className="flex items-center gap-2 border border-slate-200 bg-slate-50 rounded-lg px-3 py-2.5 text-sm text-slate-500">
                <svg className="w-4 h-4 flex-shrink-0 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
                Office HQ — Mumbai, India
              </div>
            </div>

            {/* Destination */}
            <div className="relative" ref={destinationRef}>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Destination <span className="text-red-400">*</span>
              </label>
              <input
                type="text"
                value={destination}
                onChange={(e) => {
                  setDestination(e.target.value);
                  setShowSuggestions(true);
                }}
                onFocus={() => setShowSuggestions(true)}
                onBlur={() => {
                  // Delay so click on a suggestion fires before blur hides the list
                  setTimeout(() => setShowSuggestions(false), 150);
                }}
                placeholder="e.g. Tokyo, Japan"
                autoComplete="off"
                className={`w-full border rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${
                  errors.destination ? "border-red-400" : "border-slate-300"
                }`}
              />
              {showSuggestions && (
                <ul className="absolute z-20 left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg overflow-hidden max-h-52 overflow-y-auto">
                  {SUGGESTED_DESTINATIONS.filter((d) =>
                    d.toLowerCase().includes(destination.toLowerCase())
                  ).map((d) => (
                    <li
                      key={d}
                      onMouseDown={() => {
                        setDestination(d);
                        setShowSuggestions(false);
                      }}
                      className="px-3 py-2.5 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-700 cursor-pointer"
                    >
                      {d}
                    </li>
                  ))}
                  {SUGGESTED_DESTINATIONS.filter((d) =>
                    d.toLowerCase().includes(destination.toLowerCase())
                  ).length === 0 && (
                    <li className="px-3 py-2.5 text-sm text-slate-400 italic">No matching destinations</li>
                  )}
                </ul>
              )}
              {errors.destination && (
                <p className="mt-1 text-xs text-red-500">{errors.destination}</p>
              )}
            </div>

            {/* Dates */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  Departure Date <span className="text-red-400">*</span>
                </label>
                <input
                  type="date"
                  value={startDate}
                  min={new Date().toISOString().slice(0, 10)}
                  onChange={(e) => setStartDate(e.target.value)}
                  className={`w-full border rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${
                    errors.startDate ? "border-red-400" : "border-slate-300"
                  }`}
                />
                {errors.startDate && <p className="mt-1 text-xs text-red-500">{errors.startDate}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  Return Date <span className="text-red-400">*</span>
                </label>
                <input
                  type="date"
                  value={endDate}
                  min={startDate || new Date().toISOString().slice(0, 10)}
                  onChange={(e) => setEndDate(e.target.value)}
                  className={`w-full border rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${
                    errors.endDate ? "border-red-400" : "border-slate-300"
                  }`}
                />
                {errors.endDate && <p className="mt-1 text-xs text-red-500">{errors.endDate}</p>}
              </div>
            </div>

            {/* Business Purpose */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Business Purpose <span className="text-red-400">*</span>
              </label>
              <input
                type="text"
                value={businessPurpose}
                onChange={(e) => setBusinessPurpose(e.target.value)}
                placeholder="e.g. Q2 Engineering Planning Meeting"
                className={`w-full border rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition ${
                  errors.businessPurpose ? "border-red-400" : "border-slate-300"
                }`}
              />
              {errors.businessPurpose && <p className="mt-1 text-xs text-red-500">{errors.businessPurpose}</p>}
            </div>

            {/* Amount + Currency */}
            <div className="grid grid-cols-3 gap-4">
              <div className="col-span-2">
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Estimated Amount</label>
                <input
                  type="number"
                  min="0"
                  value={totalAmount}
                  onChange={(e) => setTotalAmount(e.target.value)}
                  placeholder="0"
                  className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Currency</label>
                <select
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                  className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option>INR</option>
                  <option>USD</option>
                  <option>EUR</option>
                  <option>SGD</option>
                </select>
              </div>
            </div>

            {/* Notes */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Notes</label>
              <textarea
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Flight preferences, loyalty numbers, special requirements…"
                className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none transition"
              />
            </div>

            <hr className="border-slate-200" />

            {/* Identity Context Panel */}
            <div className={`border rounded-lg p-4 ${delegationActive ? "bg-amber-50 border-amber-200" : "bg-slate-50 border-slate-200"}`}>
              <p className="text-xs font-semibold text-slate-600 mb-2.5">Identity context for this booking</p>
              <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-xs font-mono">
                <span className="text-slate-400 font-sans">userId (subject)</span>
                <span className="text-slate-700">{subjectId ?? session?.user?.email ?? "—"}</span>

                <span className="text-slate-400 font-sans">createdBy (actor)</span>
                <span className={delegationActive ? "text-blue-700 font-semibold" : "text-slate-700"}>
                  {session?.user?.email ?? "—"}
                </span>

                <span className="text-slate-400 font-sans">delegationId</span>
                <span className={delegationId ? "text-amber-700" : "text-slate-400"}>
                  {delegationId ?? "— none"}
                </span>

                <span className="text-slate-400 font-sans">tenantId</span>
                <span className="text-slate-700">{session?.user?.tenantId ?? "—"}</span>
              </div>
            </div>

            {serverError && (
              <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                {serverError}
              </p>
            )}

            {/* Actions */}
            <div className="flex gap-3 pt-1">
              <Link
                href="/travel"
                className="flex-1 text-center border border-slate-300 hover:border-slate-400 text-slate-700 text-sm font-medium py-2.5 rounded-lg transition-colors"
              >
                Cancel
              </Link>
              <button
                type="submit"
                disabled={submitting}
                className="flex-[2] bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white text-sm font-semibold py-2.5 rounded-lg transition-colors"
              >
                {submitting ? "Confirming…" : "Confirm Booking"}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
