"use client";

import Link from "next/link";
import { useSession } from "next-auth/react";
import { StatCard } from "@/components/shared/StatCard";

// Static service health — wired to live data in Phase 7
const SERVICES = [
  { name: "employee-bff",       port: "8085", note: "UP" },
  { name: "travel-service",     port: "8081", note: "UP" },
  { name: "expense-service",    port: "8082", note: "UP" },
  { name: "delegation-service", port: "8083", note: "UP" },
  { name: "consent-service",    port: "8084", note: "UP" },
  { name: "Keycloak",           port: "8080", note: "UP · realm: corporate-travel" },
  { name: "OPA",                port: "8181", note: "UP · 6 policies loaded" },
];

// Static recent audit events — live data in Phase 7
const AUDIT_EVENTS = [
  { time: "09:41:03", action: "CREATE_BOOKING",      actor: "dave.assistant", subject: "carol.executive", resource: "BKG-…",        result: "ALLOW" },
  { time: "09:38:51", action: "ACTIVATE_DELEGATION", actor: "dave.assistant", subject: "carol.executive", resource: "DLG-…",        result: "ALLOW" },
  { time: "09:22:10", action: "VIEW_BOOKINGS",        actor: "carol.executive", subject: "—",             resource: "/api/bff/bookings", result: "ALLOW" },
  { time: "08:55:44", action: "CREATE_EXPENSE",       actor: "alice.employee",  subject: "—",             resource: "EXP-…",        result: "ALLOW" },
  { time: "08:31:02", action: "VIEW_BOOKINGS",        actor: "unknown.user",    subject: "—",             resource: "tenant: beta-corp", result: "DENY" },
];

function UserIcon() {
  return (
    <svg className="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}
function ShieldCheckIcon() {
  return (
    <svg className="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}
function CheckCircleIcon() {
  return (
    <svg className="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function AlertTriangleIcon() {
  return (
    <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>
  );
}

export function AdminDashboard() {
  const { data: session } = useSession();

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Admin Dashboard</h1>
        <p className="text-sm text-slate-400 mt-0.5">
          {new Date().toLocaleDateString("en-IN", {
            weekday: "long",
            day: "numeric",
            month: "short",
            year: "numeric",
          })}{" "}
          · Tenant: {session?.user?.tenantId ?? ""}
        </p>
      </div>

      {/* Stat cards — static placeholders until Phase 7 */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard
          label="Active Users"
          value="—"
          subtitle="All via Keycloak"
          icon={<UserIcon />}
          iconBgClass="bg-blue-50"
        />
        <StatCard
          label="Active Delegations"
          value="—"
          subtitle="Live data in Phase 7"
          icon={<ShieldCheckIcon />}
          iconBgClass="bg-amber-50"
        />
        <StatCard
          label="OPA Decisions (24h)"
          value="—"
          subtitle="Live data in Phase 7"
          icon={<CheckCircleIcon />}
          iconBgClass="bg-emerald-50"
        />
        <StatCard
          label="Policy Violations (24h)"
          value="—"
          subtitle="Live data in Phase 7"
          icon={<AlertTriangleIcon />}
          iconBgClass="bg-red-50"
        />
      </div>

      {/* Service Health — static in Phase 3 */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-800">Service Health</h2>
          <span className="text-xs text-slate-400">Static placeholder — live polling in Phase 7</span>
        </div>
        <div className="divide-y divide-slate-100">
          {SERVICES.map((svc) => (
            <div key={svc.name} className="flex items-center justify-between px-5 py-3 hover:bg-slate-50/70 transition-colors">
              <div className="flex items-center gap-3">
                <span className="w-2 h-2 rounded-full bg-emerald-500 flex-shrink-0" aria-hidden="true" />
                <span className="text-sm font-medium text-slate-800">{svc.name}</span>
                <span className="text-xs text-slate-400 font-mono">:{svc.port}</span>
              </div>
              <span className="text-xs text-emerald-600 font-medium">{svc.note}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Recent Audit Events — static in Phase 3 */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <h2 className="text-sm font-semibold text-slate-800">Recent Audit Events</h2>
          <Link href="/admin/audit" className="text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors">
            Full audit log →
          </Link>
        </div>
        <table className="w-full" aria-label="Recent Audit Events">
          <thead>
            <tr className="border-b border-slate-100 bg-slate-50">
              {["Time", "Action", "Actor", "Subject", "Resource", "Result"].map((h) => (
                <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 font-mono text-xs">
            {AUDIT_EVENTS.map((ev, i) => (
              <tr
                key={i}
                className={ev.result === "DENY" ? "bg-red-50/50 hover:bg-red-50/80 transition-colors" : "hover:bg-slate-50/70 transition-colors"}
              >
                <td className="px-5 py-3.5 text-slate-400">{ev.time}</td>
                <td className={`px-5 py-3.5 ${ev.result === "DENY" ? "text-red-700" : "text-slate-700"}`}>{ev.action}</td>
                <td className={`px-5 py-3.5 ${ev.result === "DENY" ? "text-red-600" : "text-blue-600"}`}>{ev.actor}</td>
                <td className={`px-5 py-3.5 ${ev.subject === "—" ? "text-slate-400" : "text-amber-600"}`}>{ev.subject}</td>
                <td className={`px-5 py-3.5 ${ev.result === "DENY" ? "text-red-500" : "text-slate-500"}`}>{ev.resource}</td>
                <td className="px-5 py-3.5">
                  <span className={`font-semibold ${ev.result === "ALLOW" ? "text-emerald-600" : "text-red-600"}`}>
                    {ev.result}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="px-5 py-3 border-t border-slate-100 text-xs text-slate-400 bg-slate-50">
          Showing static sample events · Live audit data in Phase 7 · Columns: actorId · subjectId · delegationId · consentId (ADR-011)
        </div>
      </div>
    </div>
  );
}
