export type ExpenseStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "PAID";

export type ExpenseCategory =
  | "TRAVEL"
  | "ACCOMMODATION"
  | "MEALS"
  | "TRANSPORTATION"
  | "OTHER";

export interface ExpenseItem {
  id: string;
  /** ISO date string — field name in backend is `date` */
  date: string;
  category: ExpenseCategory;
  description: string;
  amount: number;
  currency: string;
  receiptUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Expense {
  id: string;
  tenantId: string;
  /** The expense owner — in delegation scenarios this is the subject */
  userId: string;
  bookingId?: string;
  title: string;
  description?: string;
  totalAmount: number;
  currency: string;
  status: ExpenseStatus;
  submissionDate?: string;
  approvalDate?: string;
  approverId?: string;
  items: ExpenseItem[];
  createdAt: string;
  updatedAt: string;
  /** The user who created the record — the actor in delegation scenarios */
  createdBy: string;
  updatedBy?: string;
  /** No delegationId on the backend — use `createdBy !== userId` to detect delegation */
}
