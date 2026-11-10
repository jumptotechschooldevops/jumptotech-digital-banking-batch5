import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { DashboardPage } from "./DashboardPage";
import { renderWithProviders } from "@/test/testUtils";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders balances exactly as returned by the API - nothing hardcoded", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/auth/me")) {
        return Promise.resolve(
          jsonResponse({ id: "u1", email: "sarah@example.test", role: "CUSTOMER", firstName: "Sarah", lastName: "Johnson" }),
        );
      }
      if (url.includes("/transactions")) {
        return Promise.resolve(jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }));
      }
      if (url.includes("/accounts")) {
        return Promise.resolve(
          jsonResponse([
            { id: "a1", maskedAccountNumber: "****4821", accountType: "CHECKING", balanceCents: 850_000, currency: "USD", status: "ACTIVE" },
            { id: "a2", maskedAccountNumber: "****7793", accountType: "SAVINGS", balanceCents: 1_520_000, currency: "USD", status: "ACTIVE" },
          ]),
        );
      }
      return Promise.resolve(jsonResponse({}, 404));
    });
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText("Welcome back, Sarah")).toBeInTheDocument();
    expect(await screen.findByText("$8,500.00")).toBeInTheDocument(); // checking
    expect(await screen.findByText("$15,200.00")).toBeInTheDocument(); // savings
    expect(await screen.findByText("$23,700.00")).toBeInTheDocument(); // total
  });

  it("shows an error message when the accounts request fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) => {
        if (url.includes("/auth/me")) {
          return Promise.resolve(
            jsonResponse({ id: "u1", email: "sarah@example.test", role: "CUSTOMER", firstName: "Sarah", lastName: "Johnson" }),
          );
        }
        return Promise.resolve(jsonResponse({ status: 500, error: "INTERNAL_ERROR", message: "oops", path: "" }, 500));
      }),
    );

    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText(/couldn't load your accounts/i)).toBeInTheDocument();
  });
});
