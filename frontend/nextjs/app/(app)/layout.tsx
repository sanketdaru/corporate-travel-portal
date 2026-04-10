import { AppShell } from "@/components/layout/AppShell";

export default function AppShellLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AppShell>{children}</AppShell>;
}
