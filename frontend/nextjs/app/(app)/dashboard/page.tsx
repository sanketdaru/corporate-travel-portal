"use client";

import { useSession } from "next-auth/react";
import { EmployeeDashboard } from "@/components/dashboard/EmployeeDashboard";
import { ManagerDashboard } from "@/components/dashboard/ManagerDashboard";
import { AdminDashboard } from "@/components/dashboard/AdminDashboard";

export default function DashboardPage() {
  const { data: session, status } = useSession();

  if (status === "loading") {
    return (
      <div className="space-y-6">
        <div className="h-8 w-64 bg-slate-100 rounded animate-pulse" />
        <div className="grid grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-28 bg-white border border-slate-200 rounded-xl animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  const roles = session?.user?.roles ?? [];

  // Priority: admin > manager > everything else
  if (roles.includes("admin")) {
    return <AdminDashboard />;
  }
  if (roles.includes("manager")) {
    return <ManagerDashboard />;
  }
  return <EmployeeDashboard />;
}
