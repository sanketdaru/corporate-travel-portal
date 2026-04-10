export type BookingType = "FLIGHT" | "HOTEL" | "CAR";

export type BookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "COMPLETED"
  | "CANCELLED"
  | "DRAFT";

export interface Booking {
  id: string;
  tenantId: string;
  userId: string;
  bookingType: BookingType;
  destination: string;
  startDate: string;   // ISO date: "2024-01-15"
  endDate: string;     // ISO date
  status: BookingStatus;
  totalAmount: number;
  currency?: string;
  details?: string;    // JSON string with extra info
  businessPurpose?: string;
  notes?: string;
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
  createdAt: string;
}

