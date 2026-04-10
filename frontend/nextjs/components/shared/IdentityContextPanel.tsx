interface IdentityRow {
  label: string;
  value: string | null | undefined;
  /** Color applied to the value text when present */
  valueColor?: "amber" | "blue" | "slate";
  note?: string;
}

interface IdentityContextPanelProps {
  rows: IdentityRow[];
  /** When true, panel uses amber border + header; otherwise slate */
  delegated?: boolean;
  /** Optional footer text */
  footer?: string;
}

export function IdentityContextPanel({ rows, delegated = false, footer }: IdentityContextPanelProps) {
  const borderClass = delegated
    ? "border-amber-200"
    : "border-slate-200";
  const headerBg = delegated
    ? "bg-amber-50 border-b border-amber-100"
    : "bg-slate-50 border-b border-slate-100";
  const headerText = delegated ? "text-amber-900" : "text-slate-800";
  const headerIcon = delegated ? "text-amber-600" : "text-slate-400";

  return (
    <div className={`bg-white border ${borderClass} rounded-xl overflow-hidden`}>
      <div className={`px-5 py-4 flex items-center justify-between ${headerBg}`}>
        <div className="flex items-center gap-2">
          <svg className={`w-4 h-4 ${headerIcon}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
          <h2 className={`text-sm font-semibold ${headerText}`}>Identity &amp; Audit Trail</h2>
        </div>
        {delegated && (
          <span className="text-xs text-amber-600 font-medium">Booked via delegation · RFC 8693 Token Exchange</span>
        )}
      </div>

      <div className="divide-y divide-slate-100">
        {rows.map((row) => {
          const valueClass =
            row.valueColor === "amber"
              ? "text-amber-700 font-semibold"
              : row.valueColor === "blue"
              ? "text-blue-700 font-semibold"
              : "text-slate-700";

          return (
            <div key={row.label} className="grid grid-cols-3 px-5 py-3 text-sm">
              <span className="text-slate-500">{row.label}</span>
              <span className={`col-span-2 font-mono ${row.value ? valueClass : "text-slate-400"}`}>
                {row.value ?? "— none"}
                {row.note && (
                  <span className="ml-2 text-xs font-normal font-sans text-slate-400">{row.note}</span>
                )}
              </span>
            </div>
          );
        })}
      </div>

      {footer && (
        <div className="px-5 py-3 bg-slate-50 border-t border-slate-100 text-xs text-slate-400">
          {footer}
        </div>
      )}
    </div>
  );
}
