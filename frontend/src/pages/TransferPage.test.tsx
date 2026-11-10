import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TransferPage } from "./TransferPage";
import { renderWithProviders } from "@/test/testUtils";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockAccountsFetch() {
  return vi.fn().mockImplementation((url: string) => {
    if (url.includes("/auth/me")) {
      return Promise.resolve(
        jsonResponse({ id: "u1", email: "sarah@example.test", role: "CUSTOMER", firstName: "Sarah", lastName: "Johnson" }),
      );
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
}

describe("TransferPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("requires a source account, destination account, and amount before submitting", async () => {
    vi.stubGlobal("fetch", mockAccountsFetch());
    renderWithProviders(<TransferPage />);

    await userEvent.click(await screen.findByRole("button", { name: /review transfer/i }));

    expect(await screen.findByText(/choose a source account/i)).toBeInTheDocument();
    expect(await screen.findByText(/choose a destination account/i)).toBeInTheDocument();
    expect(await screen.findByText(/enter an amount/i)).toBeInTheDocument();
  });

  it("opens a confirmation dialog with the entered amount and accounts before submitting", async () => {
    vi.stubGlobal("fetch", mockAccountsFetch());
    renderWithProviders(<TransferPage />);

    await screen.findByRole("button", { name: /review transfer/i });

    await userEvent.selectOptions(screen.getByLabelText(/from account/i), "a1");
    await userEvent.selectOptions(screen.getByLabelText(/to account/i), "a2");
    await userEvent.type(screen.getByLabelText(/amount/i), "25.00");
    await userEvent.click(screen.getByRole("button", { name: /review transfer/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    const dialog = within(screen.getByRole("dialog"));
    expect(dialog.getByText(/\$25\.00/)).toBeInTheDocument();
    expect(dialog.getByText(/\*\*\*\*4821/)).toBeInTheDocument();
    expect(dialog.getByText(/\*\*\*\*7793/)).toBeInTheDocument();
  });

  it("rejects transferring an account to itself", async () => {
    vi.stubGlobal("fetch", mockAccountsFetch());
    renderWithProviders(<TransferPage />);

    await screen.findByRole("button", { name: /review transfer/i });

    await userEvent.selectOptions(screen.getByLabelText(/from account/i), "a1");
    await userEvent.selectOptions(screen.getByLabelText(/to account/i), "a1");
    await userEvent.type(screen.getByLabelText(/amount/i), "10.00");
    await userEvent.click(screen.getByRole("button", { name: /review transfer/i }));

    expect(await screen.findByText(/must be different/i)).toBeInTheDocument();
  });
});
