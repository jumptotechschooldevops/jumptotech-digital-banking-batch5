import { useQuery } from "@tanstack/react-query";
import { fetchAdminSummary } from "@/api/admin";

export function useAdminSummary() {
  return useQuery({
    queryKey: ["admin", "summary"],
    queryFn: fetchAdminSummary,
  });
}
