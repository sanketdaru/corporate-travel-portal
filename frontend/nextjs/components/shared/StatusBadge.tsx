interface StatusBadgeProps {
  status: string;
  className?: string;
}

type StatusConfig = {
  className: string;
  dotClass: string;
};

const STATUS_MAP: Record<string, StatusConfig> = {
  // Bookings
  CONFIRMED: { className: "text-emerald-700 bg-emerald-50 border border-emerald-200", dotClass: "bg-emerald-500" },
  APPROVED:  { className: "text-emerald-700 bg-emerald-50 border border-emerald-200", dotClass: "bg-emerald-500" },
  ACTIVE:    { className: "text-emerald-700 bg-emerald-50 border border-emerald-200", dotClass: "bg-emerald-500" },
  PAID:      { className: "text-emerald-700 bg-emerald-50 border border-emerald-200", dotClass: "bg-emerald-500" },

  SUBMITTED: { className: "text-amber-700 bg-amber-50 border border-amber-200",   dotClass: "bg-amber-400" },
  PENDING:   { className: "text-amber-700 bg-amber-50 border border-amber-200",   dotClass: "bg-amber-400" },

  DRAFT:     { className: "text-slate-600 bg-slate-100 border border-slate-200",  dotClass: "bg-slate-400" },
  COMPLETED: { className: "text-slate-600 bg-slate-100 border border-slate-200",  dotClass: "bg-slate-400" },
  EXPIRED:   { className: "text-slate-600 bg-slate-100 border border-slate-200",  dotClass: "bg-slate-400" },

  REJECTED:  { className: "text-red-700 bg-red-50 border border-red-200",         dotClass: "bg-red-500" },
  CANCELLED: { className: "text-red-700 bg-red-50 border border-red-200",         dotClass: "bg-red-500" },
};

export function StatusBadge({ status, className = "" }: StatusBadgeProps) {
  const config = STATUS_MAP[status.toUpperCase()] ?? {
    className: "text-slate-600 bg-slate-100 border border-slate-200",
    dotClass: "bg-slate-400",
  };

  return (
    <span
      role="status"
      className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${config.className} ${className}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${config.dotClass}`} />
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}
