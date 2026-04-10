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
  expenseId: string;
  category: ExpenseCategory;
  description: string;
  amount: number;
  expenseDate: string;  // ISO date
  createdAt: string;
}

export interface Expense {
  id: string;
  tenantId: string;
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
  createdBy: string;
  updatedBy?: string;
  delegationId?: string;
}
