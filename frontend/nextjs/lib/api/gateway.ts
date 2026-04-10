import { gatewayClient } from "./client";
import type { Expense } from "@/lib/types/expense";
import type { BookingAudit } from "@/lib/types/booking";

export async function approveExpense(id: string): Promise<Expense> {
  const res = await gatewayClient.post<Expense>(`/api/expenses/${id}/approve`);
  return res.data;
}

export async function rejectExpense(id: string): Promise<Expense> {
  const res = await gatewayClient.post<Expense>(`/api/expenses/${id}/reject`);
  return res.data;
}

export async function submitExpense(id: string): Promise<Expense> {
  const res = await gatewayClient.post<Expense>(`/api/expenses/${id}/submit`);
  return res.data;
}

export async function getExpenses(): Promise<Expense[]> {
  const res = await gatewayClient.get<Expense[]>("/api/expenses");
  return res.data;
}

export async function getBookingAudit(bookingId: string): Promise<BookingAudit[]> {
  const res = await gatewayClient.get<BookingAudit[]>(`/api/bookings/${bookingId}/audit`);
  return res.data;
}

export interface ExpenseAudit {
  id: string;
  expenseId: string;
  action: string;
  actorId: string;
  subjectId?: string;
  delegationId?: string;
  details?: Record<string, unknown>;
  createdAt: string;
}

export async function getExpenseAudit(expenseId: string): Promise<ExpenseAudit[]> {
  const res = await gatewayClient.get<ExpenseAudit[]>(`/api/expenses/${expenseId}/audit`);
  return res.data;
}

export interface Delegation {
  id: string;
  delegatorId: string;
  delegateId: string;
  purpose: string;
  scopes: string[];
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  expiresAt: string;
  createdAt: string;
}

export async function getMyDelegations(): Promise<Delegation[]> {
  const res = await gatewayClient.get<Delegation[]>("/api/delegations/my-delegations");
  return res.data;
}

export async function getDelegationsToMe(): Promise<Delegation[]> {
  const res = await gatewayClient.get<Delegation[]>("/api/delegations/to-me");
  return res.data;
}

export async function createDelegation(body: Partial<Delegation>): Promise<Delegation> {
  const res = await gatewayClient.post<Delegation>("/api/delegations", body);
  return res.data;
}

export async function revokeDelegation(id: string): Promise<void> {
  await gatewayClient.delete(`/api/delegations/${id}`);
}

export interface Consent {
  id: string;
  grantorId: string;
  granteeId: string;
  delegationId: string;
  purpose: string;
  scopes: string[];
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  validUntil: string;
  createdAt: string;
}

export async function getMyConsents(): Promise<Consent[]> {
  const res = await gatewayClient.get<Consent[]>("/api/consents/my-consents");
  return res.data;
}
