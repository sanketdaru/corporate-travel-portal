"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { signOut } from "next-auth/react";
import { useDelegationContext } from "@/lib/context/DelegationContext";
import { UserSession } from "@/lib/types/auth";

interface SidebarProps {
  user?: UserSession;
}

interface NavItem {
  href: string;
  label: string;
  icon: React.ReactNode;
}

function PlaneIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
    </svg>
  );
}
function HomeIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>
  );
}
function CardIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
    </svg>
  );
}
function ShieldIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}
function UsersIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  );
}
function ClockIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function ServerIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
    </svg>
  );
}
function TableIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M3 6h18M3 14h18M3 18h18" />
    </svg>
  );
}
function LogoutIcon() {
  return (
    <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
    </svg>
  );
}
function BuildingIcon() {
  return (
    <svg className="w-3.5 h-3.5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  );
}

const coreNavItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: <HomeIcon /> },
  { href: "/travel", label: "My Trips", icon: <PlaneIcon /> },
  { href: "/expense", label: "My Expenses", icon: <CardIcon /> },
  { href: "/delegation", label: "Delegations", icon: <ShieldIcon /> },
];

const managerNavItems: NavItem[] = [
  { href: "/approvals", label: "Pending Approvals", icon: <UsersIcon /> },
];

const adminNavItems: NavItem[] = [
  { href: "/admin/bookings", label: "All Bookings", icon: <PlaneIcon /> },
  { href: "/admin/expenses", label: "All Expenses", icon: <TableIcon /> },
  { href: "/admin/audit", label: "Audit Log", icon: <ClockIcon /> },
  { href: "/admin/health", label: "System Health", icon: <ServerIcon /> },
];

function NavLink({ href, label, icon, active }: NavItem & { active: boolean }) {
  return (
    <Link
      href={href}
      className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
        active
          ? "bg-slate-800 text-white font-medium"
          : "text-slate-400 hover:bg-slate-800 hover:text-white"
      }`}
    >
      {icon}
      {label}
    </Link>
  );
}

export function Sidebar({ user }: SidebarProps) {
  const pathname = usePathname();
  const { delegationActive, subjectName } = useDelegationContext();
  const roles = user?.roles ?? ["employee"];
  const isManager = roles.includes("manager");
  const isAdmin = roles.includes("admin");
  const tenantId = user?.tenantId ?? "acme-corp";

  const topPosition = delegationActive ? "top-24" : "top-14";

  return (
    <aside
      className={`fixed left-0 ${topPosition} bottom-0 w-64 bg-slate-900 flex flex-col z-40 transition-all duration-200`}
    >
      {/* Tenant header */}
      <div className="px-4 py-3 border-b border-slate-800 flex items-center gap-2.5">
        <div className="w-6 h-6 rounded-md bg-blue-600/20 flex items-center justify-center flex-shrink-0">
          <BuildingIcon />
        </div>
        <div>
          <p className="text-xs font-medium text-slate-300 leading-tight">{tenantId}</p>
          <p className="text-xs text-slate-500 leading-tight">Corporate tenant</p>
        </div>
      </div>

      {/* Delegation context label */}
      {delegationActive && subjectName && (
        <div className="px-4 py-2 bg-amber-500/10 border-b border-amber-500/20">
          <p className="text-xs text-amber-400 font-medium">
            Acting for{" "}
            <span className="bg-amber-500 text-white px-1.5 py-0.5 rounded-md font-semibold text-xs">
              {subjectName.split(" ")[0]}&apos;s
            </span>{" "}
            account
          </p>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
        <div className="px-3 pb-1">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-widest">My Work</p>
        </div>
        {coreNavItems.map((item) => (
          <NavLink key={item.href} {...item} active={pathname === item.href || pathname.startsWith(item.href + "/")} />
        ))}

        {isManager && (
          <>
            <div className="px-3 pt-4 pb-1">
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-widest">Team</p>
            </div>
            {managerNavItems.map((item) => (
              <NavLink key={item.href} {...item} active={pathname === item.href} />
            ))}
          </>
        )}

        {isAdmin && (
          <>
            <div className="px-3 pt-4 pb-1">
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-widest">Administration</p>
            </div>
            {adminNavItems.map((item) => (
              <NavLink key={item.href} {...item} active={pathname === item.href} />
            ))}
          </>
        )}
      </nav>

      {/* Sign out */}
      <div className="px-2 py-3 border-t border-slate-800">
        <button
          onClick={() => signOut({ callbackUrl: "/login" })}
          className="flex items-center gap-3 px-3 py-2 rounded-lg text-slate-400 hover:bg-slate-800 hover:text-white transition-colors text-sm w-full"
        >
          <LogoutIcon />
          Sign out
        </button>
      </div>
    </aside>
  );
}
