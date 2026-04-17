import { gatewayClient, bffClient } from "./client";
import type { Expense } from "@/lib/types/expense";
import type { Booking, BookingAudit } from "@/lib/types/booking";

export async function approveExpense(id: string): Promise<Expense> {
  const res = await gatewayClient.post<Expense>(`/api/expenses/${id}/approve`);
  return res.data;
}

export async function rejectExpense(id: string): Promise<Expense> {
  const res = await gatewayClient.post<Expense>(`/api/expenses/${id}/reject`);
  return res.data;
}

export async function submitExpense(id: string): Promise<Expense> {
  const res = await bffClient.post<Expense>(`/api/bff/expenses/${id}/submit`);
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

/** Admin: list all bookings in the tenant. Requires admin role; uses dedicated /all endpoint. */
export async function getAllBookings(): Promise<Booking[]> {
  const res = await gatewayClient.get<Booking[]>("/api/bookings/all");
  return res.data;
}

/** Admin: list all expenses in the tenant. Requires admin role; uses dedicated /all endpoint. */
export async function getAllExpenses(): Promise<Expense[]> {
  const res = await gatewayClient.get<Expense[]>("/api/expenses/all");
  return res.data;
}

export interface ExpenseAudit {
  id: string;
  expenseId: string;
  action: string;
  actorId: string;
  subjectId?: string;
  delegationId?: string;
  consentId?: string;
  tenantId?: string;
  details?: Record<string, unknown>;
  timestamp: string;
}

export async function getExpenseAudit(expenseId: string): Promise<ExpenseAudit[]> {
  const res = await bffClient.get<ExpenseAudit[]>(`/api/bff/expenses/${expenseId}/audit`);
  return res.data;
}

export interface Delegation {
  id: string;
  tenantId: string;
  delegatorId: string;
  delegateId: string;
  purpose: string;
  scopes: string[];
  // Backend sends active + valid booleans, not a status string
  active: boolean;
  valid: boolean;
  expiresAt: string | null;
  revokedAt: string | null;
  revokedBy: string | null;
  grantedAt: string;
  createdAt: string;
}

/** Derive a display status from the backend's boolean fields. */
export function delegationStatus(d: Delegation): "ACTIVE" | "REVOKED" | "EXPIRED" {
  if (!d.active && d.revokedAt) return "REVOKED";
  if (!d.active || !d.valid)    return "EXPIRED";
  return "ACTIVE";
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
  tenantId: string;
  grantorId: string;
  granteeId: string;
  delegationId: string | null;
  purpose: string;
  scopes: string[];
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  expiresAt: string | null;  // backend field name (was incorrectly typed as validUntil)
  createdAt: string;
  valid: boolean;
}

export async function getMyConsents(): Promise<Consent[]> {
  const res = await gatewayClient.get<Consent[]>("/api/consents/my-consents");
  return res.data;
}

export interface CreateConsentBody {
  grantorId: string;
  granteeId: string;
  delegationId: string;
  purpose: string;
  scopes: string[];
  expiresAt?: string;
}

export async function createConsent(body: CreateConsentBody): Promise<Consent> {
  const res = await gatewayClient.post<Consent>("/api/consents", body);
  return res.data;
}

export async function revokeConsent(id: string): Promise<void> {
  await gatewayClient.delete(`/api/consents/${id}`);
}
