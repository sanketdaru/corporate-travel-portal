export interface UserSession {
  id: string;
  name: string;
  email: string;
  roles: string[];
  tenantId: string;
  accessToken?: string;
}

export type UserRole =
  | "employee"
  | "manager"
  | "admin"
  | "executive"
  | "assistant";

export function getPrimaryRole(roles: string[]): UserRole {
  if (roles.includes("admin")) return "admin";
  if (roles.includes("manager")) return "manager";
  if (roles.includes("executive")) return "executive";
  if (roles.includes("assistant")) return "assistant";
  return "employee";
}

export function getRoleBadgeStyle(role: UserRole): {
  label: string;
  className: string;
} {
  switch (role) {
    case "admin":
      return {
        label: "Admin",
        className:
          "text-purple-700 bg-purple-50 border border-purple-200",
      };
    case "manager":
      return {
        label: "Manager",
        className:
          "text-violet-700 bg-violet-50 border border-violet-200",
      };
    case "executive":
      return {
        label: "Executive",
        className:
          "text-emerald-700 bg-emerald-50 border border-emerald-200",
      };
    case "assistant":
      return {
        label: "Assistant",
        className:
          "text-amber-700 bg-amber-50 border border-amber-200",
      };
    case "employee":
    default:
      return {
        label: "Employee",
        className:
          "text-blue-700 bg-blue-50 border border-blue-200",
      };
  }
}
