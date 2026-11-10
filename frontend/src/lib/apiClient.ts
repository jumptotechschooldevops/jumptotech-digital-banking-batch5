export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: ApiFieldError[] | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = body.status;
    this.code = body.error;
    this.fieldErrors = body.fieldErrors ?? [];
  }
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

function readCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp("(?:^|; )" + name.replace(/([.$?*|{}()[\]\\/+^])/g, "\\$1") + "=([^;]*)"),
  );
  return match ? decodeURIComponent(match[1]) : null;
}

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
};

/**
 * Thin fetch wrapper shared by every API module. Attaches the CSRF header
 * Spring Security expects for any mutating request (read from the
 * non-HttpOnly XSRF-TOKEN cookie the backend sets), sends/receives the
 * session cookie, and converts a non-2xx response into a typed ApiError
 * instead of making every call site parse the error body itself.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = {};

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET") {
    const csrfToken = readCookie("XSRF-TOKEN");
    if (csrfToken) {
      headers["X-XSRF-TOKEN"] = csrfToken;
    }
  }
  if (options.idempotencyKey) {
    headers["Idempotency-Key"] = options.idempotencyKey;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: "include",
    signal: options.signal,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    throw new ApiError(
      data ?? {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: "UNKNOWN_ERROR",
        message: "Something went wrong. Please try again.",
        path,
      },
    );
  }

  return data as T;
}

/** Primes the CSRF cookie, the way a browser loading the SPA shell naturally would. */
export async function primeCsrfCookie(): Promise<void> {
  await fetch("/actuator/health", { credentials: "include" });
}
