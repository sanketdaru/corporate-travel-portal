interface StatCardProps {
  label: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  iconBgClass?: string;
  valueClassName?: string;
}

export function StatCard({ label, value, subtitle, icon, iconBgClass = "bg-slate-100", valueClassName }: StatCardProps) {
  return (
    <div className="bg-white border border-slate-200 rounded-xl p-5">
      <div className="flex items-start justify-between">
        <div className="min-w-0">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-widest truncate">{label}</p>
          <p className={`text-2xl font-semibold mt-1 ${valueClassName ?? "text-slate-900"}`}>{value}</p>
          {subtitle && (
            <p className="text-xs text-slate-400 mt-1 truncate">{subtitle}</p>
          )}
        </div>
        <div className={`w-10 h-10 rounded-lg ${iconBgClass} flex items-center justify-center flex-shrink-0 ml-3`}>
          {icon}
        </div>
      </div>
    </div>
  );
}
