"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm, useFieldArray, Controller } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useSession } from "next-auth/react";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { createExpense, getBookings, getBooking } from "@/lib/api/bff";
import { submitExpense } from "@/lib/api/gateway";
import { setAccessToken } from "@/lib/api/client";
import type { Booking } from "@/lib/types/booking";
import type { Expense, ExpenseCategory } from "@/lib/types/expense";

const CATEGORIES: { value: ExpenseCategory; label: string }[] = [
  { value: "TRAVEL",         label: "Travel" },
  { value: "ACCOMMODATION",  label: "Accommodation" },
  { value: "MEALS",          label: "Meals" },
  { value: "TRANSPORTATION", label: "Transportation" },
  { value: "OTHER",          label: "Other" },
];

const itemSchema = z.object({
  category:    z.enum(["TRAVEL", "ACCOMMODATION", "MEALS", "TRANSPORTATION", "OTHER"]),
  description: z.string().min(1, "Description is required"),
  date:        z.string().min(1, "Date is required"),
  amount:      z.number({ error: "Enter a valid positive amount" }).positive("Amount must be positive"),
});

const formSchema = z.object({
  title:       z.string().min(1, "Report title is required"),
  description: z.string().optional(),
  bookingId:   z.string().optional(),
  currency:    z.enum(["INR", "USD", "EUR", "SGD"]),
  items:       z.array(itemSchema).min(1, "Add at least one expense item"),
});

type FormValues = z.infer<typeof formSchema>;

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p className="mt-1 text-xs text-red-500">{message}</p>;
}

export default function SubmitExpensePage() {
  const router       = useRouter();
  const searchParams = useSearchParams();
  const { data: session } = useSession();
  const { delegationActive, subjectName, subjectId, actorId, delegationId, consentId, purpose } = useDelegationContext();

  const [bookings, setBookings]       = useState<Booking[]>([]);
  const [serverError, setServerError] = useState("");
  const [submitAction, setSubmitAction] = useState<"draft" | "submit">("draft");

  const prefillBookingId       = searchParams.get("bookingId")        ?? "";
  const prefillDestination     = searchParams.get("destination")      ?? "";
  const prefillBusinessPurpose = searchParams.get("businessPurpose")  ?? "";
  const prefillBudget          = parseFloat(searchParams.get("budget") ?? "0");
  const prefillBudgetCurrency  = (searchParams.get("budgetCurrency")  ?? "INR") as FormValues["currency"];
  const prefillStartDate       = searchParams.get("startDate")        ?? "";

  const [approvedBudget, setApprovedBudget]         = useState(prefillBudget);
  const [approvedBudgetCurrency, setApprovedBudgetCurrency] = useState(prefillBudgetCurrency);

  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      title:       prefillDestination && prefillBusinessPurpose
                     ? `${prefillDestination} — ${prefillBusinessPurpose}`
                     : "",
      description: prefillBusinessPurpose,
      bookingId:   prefillBookingId,
      currency:    prefillBudgetCurrency,
      items: [{
        category:    "TRAVEL",
        description: "",
        date:        prefillStartDate,
        amount:      0,
      }],
    },
  });

  const { fields, append, remove } = useFieldArray({ control, name: "items" });

  const watchedItems    = watch("items");
  const watchedCurrency = watch("currency");
  const runningTotal    = watchedItems.reduce((sum, item) => sum + (Number(item.amount) || 0), 0);

  function formatAmount(n: number, currency = "INR") {
    if (currency === "INR") return `₹${n.toLocaleString("en-IN")}`;
    return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(n);
  }

  useEffect(() => {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);

    async function init() {
      const allBookings = await getBookings().catch(() => [] as Booking[]);
      setBookings(allBookings);

      // If we arrived without rich URL params but have a bookingId, fetch booking
      // to populate the approved budget indicator.
      if (prefillBookingId && !prefillBudget) {
        const booking = await getBooking(prefillBookingId).catch(() => null);
        if (booking) {
          setApprovedBudget(booking.budget);
          setApprovedBudgetCurrency(booking.budgetCurrency);
          if (!prefillDestination) {
            reset({
              title:       `${booking.destination} — ${booking.businessPurpose ?? "Business Trip"}`,
              description: booking.businessPurpose ?? "",
              bookingId:   booking.id,
              currency:    booking.budgetCurrency as FormValues["currency"],
              items: [{ category: "TRAVEL", description: "", date: booking.startDate, amount: 0 }],
            });
          }
        }
      }
    }

    init();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken]);

  async function onSubmit(values: FormValues) {
    if (!session?.accessToken) return;
    setAccessToken(session.accessToken);
    setServerError("");

    try {
      const expense = await createExpense({
        title:       values.title,
        description: values.description || undefined,
        bookingId:   values.bookingId   || undefined,
        currency:    values.currency,
        totalAmount: runningTotal,
        // Server will assign id/expenseId/createdAt; cast to satisfy the Partial<Expense> type
        items: values.items.map((item) => ({
          category:    item.category,
          description: item.description,
          date:        item.date,
          amount:      item.amount,
          currency:    values.currency,
        })) as Expense["items"],
      });

      if (submitAction === "submit") {
        await submitExpense(expense.id);
      }

      router.push(`/expense/${expense.id}`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: Record<string, unknown> } })?.response?.data;
      if (data?.status === 422 && data?.budget && data?.total) {
        const currency = String(data.currency ?? "INR");
        const budget   = formatAmount(Number(data.budget), currency);
        const total    = formatAmount(Number(data.total), currency);
        const overage  = formatAmount(Number(data.overage ?? 0), currency);
        setServerError(`Expense total ${total} exceeds the approved travel budget of ${budget} (over by ${overage}). Reduce your items or request a higher budget.`);
      } else {
        const msg = (data as { message?: string })?.message;
        setServerError(msg ?? "An error occurred. Please try again.");
      }
    }
  }

  return (
    <div className="space-y-1">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-slate-400 mb-5">
        <Link href="/expense" className="hover:text-slate-700 transition-colors">My Expenses</Link>
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-slate-700 font-medium">New Expense Report</span>
      </nav>

      <div className="max-w-2xl space-y-6">
        {/* Page title */}
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Submit Expense Report</h1>
          {delegationActive && subjectName && (
            <div className="flex items-center gap-1.5 mt-1">
              <svg className="w-3.5 h-3.5 text-amber-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              <p className="text-xs text-amber-700">
                This report will be attributed to <strong>{subjectName}</strong> — you are acting as their delegate
              </p>
            </div>
          )}
        </div>

        {serverError && (
          <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
            {serverError}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="space-y-6">

            {/* Report Details */}
            <div className="bg-white border border-slate-200 rounded-xl p-6 space-y-5">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Report Details</h2>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  Report Title <span className="text-red-400">*</span>
                </label>
                <input
                  {...register("title")}
                  type="text"
                  placeholder="e.g. Singapore trip — meals &amp; transport"
                  className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                />
                <FieldError message={errors.title?.message} />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Description</label>
                <textarea
                  {...register("description")}
                  rows={2}
                  placeholder="Brief description of this expense report…"
                  className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none transition"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">Linked Trip</label>
                  <select
                    {...register("bookingId")}
                    className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">— None —</option>
                    {bookings.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.id.length > 12 ? b.id.slice(0, 12).toUpperCase() : b.id} — {b.destination}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">
                    Currency <span className="text-red-400">*</span>
                  </label>
                  <select
                    {...register("currency")}
                    className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="INR">INR</option>
                    <option value="USD">USD</option>
                    <option value="EUR">EUR</option>
                    <option value="SGD">SGD</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Expense Items */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
                <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Expense Items</h2>
                <button
                  type="button"
                  onClick={() => append({ category: "OTHER", description: "", date: "", amount: 0 })}
                  className="flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                  </svg>
                  Add item
                </button>
              </div>

              {/* Column headers */}
              <div className="px-5 py-2 grid grid-cols-12 gap-3 border-b border-slate-100 bg-slate-50">
                <span className="col-span-3 text-xs text-slate-400 font-medium">Category</span>
                <span className="col-span-4 text-xs text-slate-400 font-medium">Description</span>
                <span className="col-span-2 text-xs text-slate-400 font-medium">Date</span>
                <span className="col-span-2 text-xs text-slate-400 font-medium">Amount</span>
              </div>

              <div className="divide-y divide-slate-100">
                {fields.map((field, index) => (
                  <div key={field.id} className="px-5 py-3.5 grid grid-cols-12 gap-3 items-start">
                    <div className="col-span-3">
                      <Controller
                        control={control}
                        name={`items.${index}.category`}
                        render={({ field: f }) => (
                          <select
                            {...f}
                            className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                          >
                            {CATEGORIES.map((c) => (
                              <option key={c.value} value={c.value}>{c.label}</option>
                            ))}
                          </select>
                        )}
                      />
                    </div>
                    <div className="col-span-4">
                      <input
                        {...register(`items.${index}.description`)}
                        type="text"
                        placeholder="Description"
                        className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      />
                      <FieldError message={errors.items?.[index]?.description?.message} />
                    </div>
                    <div className="col-span-2">
                      <input
                        {...register(`items.${index}.date`)}
                        type="date"
                        className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      />
                      <FieldError message={errors.items?.[index]?.date?.message} />
                    </div>
                    <div className="col-span-2">
                      <input
                        {...register(`items.${index}.amount`, { valueAsNumber: true })}
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="0"
                        className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      />
                      <FieldError message={errors.items?.[index]?.amount?.message} />
                    </div>
                    <div className="col-span-1 flex justify-center pt-2">
                      <button
                        type="button"
                        onClick={() => fields.length > 1 && remove(index)}
                        disabled={fields.length <= 1}
                        className="text-slate-300 hover:text-red-400 transition-colors disabled:cursor-not-allowed"
                        aria-label="Remove item"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              {errors.items?.root?.message && (
                <p className="px-5 pb-3 text-xs text-red-500">{errors.items.root.message}</p>
              )}

              {/* Running total + budget indicator */}
              <div className="px-5 py-3.5 bg-slate-50 border-t border-slate-200 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-slate-600">Total</span>
                  <span className="text-base font-semibold text-slate-900">
                    {formatAmount(runningTotal, watchedCurrency)}
                  </span>
                </div>
                {approvedBudget > 0 && (
                  <div className={`rounded-lg px-3 py-2.5 text-xs space-y-1 ${runningTotal > approvedBudget ? "bg-red-50 border border-red-200" : "bg-emerald-50 border border-emerald-200"}`}>
                    <div className="flex justify-between">
                      <span className={runningTotal > approvedBudget ? "text-red-600" : "text-emerald-700"}>Approved budget</span>
                      <span className={`font-semibold ${runningTotal > approvedBudget ? "text-red-700" : "text-emerald-800"}`}>
                        {formatAmount(approvedBudget, approvedBudgetCurrency)}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className={runningTotal > approvedBudget ? "text-red-600" : "text-emerald-700"}>
                        {runningTotal > approvedBudget ? "Over budget by" : "Remaining"}
                      </span>
                      <span className={`font-semibold ${runningTotal > approvedBudget ? "text-red-700" : "text-emerald-800"}`}>
                        {runningTotal > approvedBudget
                          ? formatAmount(runningTotal - approvedBudget, approvedBudgetCurrency)
                          : formatAmount(approvedBudget - runningTotal, approvedBudgetCurrency)}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Identity Context Panel */}
            <div className={`border rounded-xl p-4 ${delegationActive ? "bg-amber-50 border-amber-200" : "bg-slate-50 border-slate-200"}`}>
              <p className={`text-xs font-semibold mb-2.5 ${delegationActive ? "text-amber-900" : "text-slate-700"}`}>
                Identity context — this report will be recorded as
              </p>
              <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-xs font-mono">
                <span className="text-slate-400 font-sans">userId (subject)</span>
                <span className={`font-semibold ${delegationActive ? "text-amber-700" : "text-slate-700"}`}>
                  {delegationActive ? (subjectId ?? "—") : (session?.user?.name ?? "—")}
                </span>
                <span className="text-slate-400 font-sans">createdBy (actor)</span>
                <span className="text-blue-700">
                  {delegationActive ? (actorId ?? "—") : (session?.user?.name ?? "—")}
                </span>
                {delegationActive && (
                  <>
                    <span className="text-slate-400 font-sans">delegationId</span>
                    <span className="text-slate-700">{delegationId ?? "—"}</span>
                    <span className="text-slate-400 font-sans">consentId</span>
                    <span className="text-slate-700">{consentId ?? "—"}</span>
                    <span className="text-slate-400 font-sans">purpose</span>
                    <span className="text-slate-700">{purpose ?? "—"}</span>
                  </>
                )}
                <span className="text-slate-400 font-sans">tenantId</span>
                <span className="text-slate-700">{(session as { user?: { tenantId?: string } })?.user?.tenantId ?? "—"}</span>
              </div>
            </div>

            {/* Actions */}
            <div className="flex gap-3 pb-8">
              <button
                type="submit"
                disabled={isSubmitting}
                onClick={() => setSubmitAction("draft")}
                className="flex-1 border border-slate-300 hover:border-slate-400 text-slate-700 text-sm font-medium py-2.5 rounded-lg transition-colors disabled:opacity-60"
              >
                {isSubmitting && submitAction === "draft" ? "Saving…" : "Save as Draft"}
              </button>
              <button
                type="submit"
                disabled={isSubmitting || (approvedBudget > 0 && runningTotal > approvedBudget)}
                onClick={() => setSubmitAction("submit")}
                title={approvedBudget > 0 && runningTotal > approvedBudget ? "Total exceeds approved budget" : undefined}
                className="flex-[2] bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold py-2.5 rounded-lg transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isSubmitting && submitAction === "submit" ? "Submitting…" : "Submit for Approval"}
              </button>
            </div>

          </div>
        </form>
      </div>
    </div>
  );
}
