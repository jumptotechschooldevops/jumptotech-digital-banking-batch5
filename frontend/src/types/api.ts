// Mirrors backend DTOs documented in docs/api.md. Money is always integer cents.

export type Role = "CUSTOMER" | "ADMIN";
export type AccountType = "CHECKING" | "SAVINGS";
export type AccountStatus = "ACTIVE" | "FROZEN" | "CLOSED";
export type TransferStatus = "PENDING" | "COMPLETED" | "FAILED";
export type EntryType = "DEBIT" | "CREDIT";

export interface AuthUser {
  id: string;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
}

export interface AccountSummary {
  id: string;
  maskedAccountNumber: string;
  accountType: AccountType;
  balanceCents: number;
  currency: string;
  status: AccountStatus;
}

export interface TransactionEntry {
  id: string;
  transferId: string;
  occurredAt: string;
  type: EntryType;
  amountCents: number;
  balanceAfterCents: number;
  status: TransferStatus;
  counterpartyMaskedAccountNumber: string;
  description: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TransferAccountSummary {
  id: string;
  maskedAccountNumber: string;
  balanceCents: number;
}

export interface TransferConfirmation {
  transferId: string;
  status: TransferStatus;
  amountCents: number;
  sourceAccount: TransferAccountSummary;
  destinationAccount: TransferAccountSummary;
  completedAt: string;
}

export interface CustomerProfile {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
}

export interface RecentTransfer {
  transferId: string;
  amountCents: number;
  status: TransferStatus;
  completedAt: string;
}

export interface AdminSummary {
  totalCustomers: number;
  totalAccounts: number;
  totalBalanceCents: number;
  recentTransfers: RecentTransfer[];
}
