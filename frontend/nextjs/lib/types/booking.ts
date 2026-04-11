export type BookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "COMPLETED"
  | "CANCELLED"
  | "DRAFT";

export type BudgetCurrency = "INR" | "USD" | "EUR" | "SGD";

export interface Booking {
  id: string;
  tenantId: string;
  userId: string;
  destination: string;
  startDate: string;           // ISO date: "2026-05-10"
  endDate: string;             // ISO date: "2026-05-17"
  businessPurpose?: string;
  notes?: string;
  status: BookingStatus;
  budget: number;              // pre-approved spending ceiling
  budgetCurrency: BudgetCurrency;
  details?: string;            // JSON string for extra info
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy?: string;
  delegationId?: string;
}

export interface BookingAudit {
  id: string;
  bookingId: string;
  action: string;
  actorId: string;
  subjectId?: string;
  delegationId?: string;
  consentId?: string;
  details?: Record<string, unknown>;
  timestamp: string;
}
