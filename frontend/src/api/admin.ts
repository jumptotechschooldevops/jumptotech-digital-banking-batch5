import { apiFetch } from "@/lib/apiClient";
import type { AdminSummary } from "@/types/api";

export async function fetchAdminSummary(): Promise<AdminSummary> {
  return apiFetch<AdminSummary>("/admin/summary");
}
