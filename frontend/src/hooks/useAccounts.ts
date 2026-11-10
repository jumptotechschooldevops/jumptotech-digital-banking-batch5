import { useQuery } from "@tanstack/react-query";
import { fetchAccount, fetchAccounts } from "@/api/accounts";

export function useAccounts() {
  return useQuery({
    queryKey: ["accounts"],
    queryFn: fetchAccounts,
  });
}

export function useAccount(accountId: string | undefined) {
  return useQuery({
    queryKey: ["accounts", accountId],
    queryFn: () => fetchAccount(accountId as string),
    enabled: Boolean(accountId),
  });
}
