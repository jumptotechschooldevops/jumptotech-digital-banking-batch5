import { apiFetch, primeCsrfCookie } from "@/lib/apiClient";
import type { AuthUser } from "@/types/api";

export interface RegisterPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export async function register(payload: RegisterPayload): Promise<AuthUser> {
  await primeCsrfCookie();
  return apiFetch<AuthUser>("/auth/register", { method: "POST", body: payload });
}

export async function login(payload: LoginPayload): Promise<AuthUser> {
  await primeCsrfCookie();
  return apiFetch<AuthUser>("/auth/login", { method: "POST", body: payload });
}

export async function logout(): Promise<void> {
  await apiFetch<void>("/auth/logout", { method: "POST" });
}

export async function fetchCurrentUser(): Promise<AuthUser> {
  return apiFetch<AuthUser>("/auth/me");
}
