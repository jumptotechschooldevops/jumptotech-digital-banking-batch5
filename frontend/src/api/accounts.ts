import { apiFetch } from "@/lib/apiClient";
import type { AccountSummary, EntryType, PageResponse, TransactionEntry } from "@/types/api";

export async function fetchAccounts(): Promise<AccountSummary[]> {
  return apiFetch<AccountSummary[]>("/accounts");
}

export async function fetchAccount(accountId: string): Promise<AccountSummary> {
  return apiFetch<AccountSummary>(`/accounts/${accountId}`);
}

export interface TransactionFilters {
  page?: number;
  size?: number;
  type?: EntryType;
  from?: string;
  to?: string;
}

export async function fetchTransactions(
  accountId: string,
  filters: TransactionFilters = {},
): Promise<PageResponse<TransactionEntry>> {
  const params = new URLSearchParams();
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 20));
  if (filters.type) params.set("type", filters.type);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);

  return apiFetch<PageResponse<TransactionEntry>>(`/accounts/${accountId}/transactions?${params.toString()}`);
}
