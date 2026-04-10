export interface AuditEvent {
  label: string;
  timestamp: string;
  detail?: string;
  color?: "emerald" | "blue" | "violet" | "amber" | "red" | "slate";
}

const DOT_COLORS: Record<NonNullable<AuditEvent["color"]>, { dot: string; ring: string }> = {
  emerald: { dot: "bg-emerald-500", ring: "ring-emerald-50" },
  blue:    { dot: "bg-blue-500",    ring: "ring-blue-50" },
  violet:  { dot: "bg-violet-500",  ring: "ring-violet-50" },
  amber:   { dot: "bg-amber-500",   ring: "ring-amber-50" },
  red:     { dot: "bg-red-500",     ring: "ring-red-50" },
  slate:   { dot: "bg-slate-400",   ring: "ring-slate-100" },
};

interface AuditTrailProps {
  events: AuditEvent[];
}

export function AuditTrail({ events }: AuditTrailProps) {
  if (events.length === 0) {
    return (
      <p className="px-5 py-8 text-center text-sm text-slate-400">No events recorded.</p>
    );
  }

  return (
    <div className="px-5 py-5 space-y-0">
      {events.map((event, i) => {
        const colorKey = event.color ?? "slate";
        const { dot, ring } = DOT_COLORS[colorKey];
        const isLast = i === events.length - 1;

        return (
          <div key={i} className="flex gap-4">
            <div className="flex flex-col items-center flex-shrink-0">
              <div className={`w-2.5 h-2.5 rounded-full ${dot} ring-4 ${ring} mt-0.5`} />
              {!isLast && <div className="w-px flex-1 bg-slate-200 my-1" />}
            </div>
            <div className={isLast ? "" : "pb-5"}>
              <p className="text-sm font-medium text-slate-800">{event.label}</p>
              {event.detail && (
                <p className="text-xs text-slate-400 mt-0.5">{event.detail}</p>
              )}
              <p className="text-xs text-slate-400 mt-0.5">{event.timestamp}</p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
