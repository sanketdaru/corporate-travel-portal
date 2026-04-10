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

export async function activateDelegation(delegationId: string, audience = "travel-service"): Promise<void> {
  await bffClient.post(`/api/bff/delegation/activate/${delegationId}`, null, {
    params: { audience },
  });
}

export async function deactivateDelegation(): Promise<void> {
  await bffClient.delete("/api/bff/delegation/deactivate");
}
