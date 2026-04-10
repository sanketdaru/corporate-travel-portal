"use client";

import { getPrimaryRole, getRoleBadgeStyle, UserSession } from "@/lib/types/auth";

interface TopNavProps {
  user?: UserSession;
}

function getInitials(name: string): string {
  return name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

function getAvatarBgClass(roles: string[]): string {
  const role = getPrimaryRole(roles);
  switch (role) {
    case "admin": return "bg-purple-600";
    case "manager": return "bg-violet-600";
    case "executive": return "bg-emerald-600";
    case "assistant": return "bg-amber-500";
    default: return "bg-blue-600";
  }
}

export function TopNav({ user }: TopNavProps) {
  const roles = user?.roles ?? ["employee"];
  const role = getPrimaryRole(roles);
  const badge = getRoleBadgeStyle(role);
  const name = user?.name ?? "Guest User";
  const email = user?.email ?? "";
  const initials = getInitials(name);
  const avatarBg = getAvatarBgClass(roles);

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 h-14 bg-white border-b border-slate-200 flex items-center justify-between px-6">
      {/* Logo */}
      <div className="flex items-center gap-2.5">
        <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center flex-shrink-0">
          <svg
            className="w-4 h-4 text-white"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2.5}
              d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
            />
          </svg>
        </div>
        <span className="font-semibold text-slate-900 text-sm">TravelCorp</span>
      </div>

      {/* Right section */}
      <div className="flex items-center gap-3">
        <span
          className={`text-xs font-medium px-2.5 py-1 rounded-full ${badge.className}`}
        >
          {badge.label}
        </span>
        <div className="h-4 w-px bg-slate-200" />
        <div className="flex items-center gap-2.5">
          <div className="text-right hidden sm:block">
            <p className="text-xs font-medium text-slate-800 leading-tight">{name}</p>
            <p className="text-xs text-slate-400 leading-tight">{email}</p>
          </div>
          <div
            className={`w-8 h-8 rounded-full ${avatarBg} flex items-center justify-center text-white text-xs font-semibold flex-shrink-0`}
          >
            {initials}
          </div>
        </div>
      </div>
    </nav>
  );
}
