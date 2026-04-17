"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { ServiceHealth } from "@/app/api/health/route";

const POLL_INTERVAL_MS = 30_000;

function formatLatency(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function ServerIcon() {
  return (
    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
    </svg>
  );
}

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg
      className={`w-4 h-4 ${spinning ? "animate-spin" : ""}`}
      fill="none" stroke="currentColor" viewBox="0 0 24 24"
    >
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

export default function HealthPage() {
  const [services, setServices] = useState<ServiceHealth[]>([]);
  const [checkedAt, setCheckedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchHealth = useCallback(async (isManual = false) => {
    if (isManual) setRefreshing(true);
    try {
      const res = await fetch("/api/health", { cache: "no-store" });
      const data = await res.json() as { services: ServiceHealth[]; checkedAt: string };
      setServices(data.services);
      setCheckedAt(data.checkedAt);
    } catch {
      // Leave existing data in place; user can retry
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchHealth();
    timerRef.current = setInterval(() => fetchHealth(), POLL_INTERVAL_MS);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [fetchHealth]);

  const upCount   = services.filter((s) => s.status === "UP").length;
  const downCount = services.filter((s) => s.status === "DOWN").length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">System Health</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Live service status · Auto-refreshes every 30 seconds
          </p>
        </div>
        <button
          onClick={() => fetchHealth(true)}
          disabled={refreshing}
          className="flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900 bg-white border border-slate-200 hover:border-slate-300 px-3 py-2 rounded-lg transition-colors disabled:opacity-50"
        >
          <RefreshIcon spinning={refreshing} />
          Refresh
        </button>
      </div>

      {/* Summary row */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white border border-slate-200 rounded-xl px-5 py-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Total Services</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{loading ? "—" : services.length}</p>
        </div>
        <div className="bg-white border border-emerald-200 rounded-xl px-5 py-4">
          <p className="text-xs font-medium text-emerald-600 uppercase tracking-wide">Services UP</p>
          <p className="text-2xl font-bold text-emerald-700 mt-1">{loading ? "—" : upCount}</p>
        </div>
        <div className={`bg-white border rounded-xl px-5 py-4 ${downCount > 0 ? "border-red-200" : "border-slate-200"}`}>
          <p className={`text-xs font-medium uppercase tracking-wide ${downCount > 0 ? "text-red-600" : "text-slate-500"}`}>
            Services DOWN
          </p>
          <p className={`text-2xl font-bold mt-1 ${downCount > 0 ? "text-red-700" : "text-slate-400"}`}>
            {loading ? "—" : downCount}
          </p>
        </div>
      </div>

      {/* Service list */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
            <ServerIcon />
            <span>Service Status</span>
          </div>
          {checkedAt && (
            <span className="text-xs text-slate-400">
              Last checked: {new Date(checkedAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
            </span>
          )}
        </div>

        <div className="divide-y divide-slate-100">
          {loading
            ? Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="flex items-center justify-between px-5 py-4">
                  <div className="flex items-center gap-4">
                    <div className="w-2.5 h-2.5 rounded-full bg-slate-200 animate-pulse" />
                    <div className="w-36 h-4 bg-slate-100 rounded animate-pulse" />
                    <div className="w-12 h-4 bg-slate-100 rounded animate-pulse" />
                  </div>
                  <div className="flex items-center gap-6">
                    <div className="w-16 h-4 bg-slate-100 rounded animate-pulse" />
                    <div className="w-10 h-5 bg-slate-100 rounded animate-pulse" />
                  </div>
                </div>
              ))
            : services.map((svc) => {
                const isUp = svc.status === "UP";
                return (
                  <div
                    key={svc.name}
                    className={`flex items-center justify-between px-5 py-4 transition-colors ${
                      isUp ? "hover:bg-slate-50/70" : "bg-red-50/30 hover:bg-red-50/60"
                    }`}
                  >
                    <div className="flex items-center gap-4">
                      <span
                        className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${
                          isUp ? "bg-emerald-500" : "bg-red-500 animate-pulse"
                        }`}
                        aria-label={svc.status}
                      />
                      <span className="text-sm font-medium text-slate-800">{svc.name}</span>
                      <span className="text-xs text-slate-400 font-mono">:{svc.port}</span>
                      {svc.note && (
                        <span className="text-xs text-slate-500">{svc.note}</span>
                      )}
                    </div>

                    <div className="flex items-center gap-6">
                      <span className="text-xs font-mono text-slate-400">
                        {formatLatency(svc.latencyMs)}
                      </span>
                      <span
                        className={`text-xs font-semibold px-2 py-0.5 rounded-md ${
                          isUp
                            ? "bg-emerald-50 text-emerald-700"
                            : "bg-red-50 text-red-700"
                        }`}
                      >
                        {svc.status}
                      </span>
                    </div>
                  </div>
                );
              })}
        </div>
      </div>

      {/* Footer note */}
      <p className="text-xs text-slate-400 text-center">
        Health checks via Spring Boot Actuator <code>/actuator/health</code> · Polling interval: 30s · Timeout: 3s per service
      </p>
    </div>
  );
}
