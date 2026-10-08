import { bffClient } from "./client";
import type { Booking } from "@/lib/types/booking";
import type { Expense } from "@/lib/types/expense";

export interface DashboardResponse {
  bookings: Booking[];
  expenses: Expense[];
}

export async function getDashboard(): Promise<DashboardResponse> {
  const res = await bffClient.get<DashboardResponse>("/api/bff/dashboard");
  return res.data;
}

export async function getBookings(params?: Record<string, string>): Promise<Booking[]> {
  const res = await bffClient.get<Booking[]>("/api/bff/bookings", { params });
  return res.data;
}

export async function createBooking(body: Partial<Booking>): Promise<Booking> {
  const res = await bffClient.post<Booking>("/api/bff/bookings", body);
  return res.data;
}

export async function getBooking(id: string): Promise<Booking> {
  const res = await bffClient.get<Booking>(`/api/bff/bookings/${id}`);
  return res.data;
}

export async function getExpenses(params?: Record<string, string>): Promise<Expense[]> {
  const res = await bffClient.get<Expense[]>("/api/bff/expenses", { params });
  return res.data;
}

export async function createExpense(body: Partial<Expense>): Promise<Expense> {
  const res = await bffClient.post<Expense>("/api/bff/expenses", body);
  return res.data;
}

export async function getExpense(id: string): Promise<Expense> {
  const res = await bffClient.get<Expense>(`/api/bff/expenses/${id}`);
  return res.data;
}

// The BFF exchanges one audience-scoped token per downstream service and keeps them server-side.
// Fails with 409 until the delegator has authorized the delegation in Keycloak.
export async function activateDelegation(delegationId: string): Promise<void> {
  await bffClient.post(`/api/bff/delegation/activate/${delegationId}`);
}

// ── Delegator's Keycloak grant (RFC 8693 delegation, ADR-024) ────────────────

// Returns the Keycloak URL to navigate to. Keycloak shows a consent screen
// ("Delegate token to …") and redirects back to /delegation?grant=success|error.
export async function startDelegationGrant(delegationId: string): Promise<string> {
  const res = await bffClient.post<{ authorizationUrl: string }>(`/api/bff/delegation/${delegationId}/grant`);
  return res.data.authorizationUrl;
}

export async function getDelegationGrantStatus(delegationId: string): Promise<boolean> {
  const res = await bffClient.get<{ authorized: boolean }>(`/api/bff/delegation/${delegationId}/grant`);
  return res.data.authorized;
}

// Revokes the delegation and its Keycloak grant (the delegator's offline session).
export async function revokeDelegationAndGrant(delegationId: string): Promise<void> {
  await bffClient.delete(`/api/bff/delegation/${delegationId}`);
}

export async function deactivateDelegation(): Promise<void> {
  await bffClient.delete("/api/bff/delegation/deactivate");
}
