import { apiFetch } from "@/lib/apiClient";
import type { CustomerProfile } from "@/types/api";

export async function fetchMyProfile(): Promise<CustomerProfile> {
  return apiFetch<CustomerProfile>("/customers/me");
}

export interface UpdateProfilePayload {
  phone?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
}

export async function updateMyProfile(payload: UpdateProfilePayload): Promise<CustomerProfile> {
  return apiFetch<CustomerProfile>("/customers/me", { method: "PATCH", body: payload });
}
