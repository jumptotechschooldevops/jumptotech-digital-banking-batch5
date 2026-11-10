import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { fetchTransactions, type TransactionFilters } from "@/api/accounts";

export function useTransactions(accountId: string | undefined, filters: TransactionFilters) {
  return useQuery({
    queryKey: ["transactions", accountId, filters],
    queryFn: () => fetchTransactions(accountId as string, filters),
    enabled: Boolean(accountId),
    placeholderData: keepPreviousData,
  });
}
