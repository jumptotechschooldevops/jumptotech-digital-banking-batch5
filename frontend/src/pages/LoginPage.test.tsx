import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginPage } from "./LoginPage";
import { renderWithProviders } from "@/test/testUtils";

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("shows validation errors when submitted empty", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse({ status: 401, error: "UNAUTHENTICATED", message: "", path: "" }, 401)),
    );
    renderWithProviders(<LoginPage />);

    await userEvent.click(await screen.findByRole("button", { name: /log in/i }));

    expect(await screen.findByText(/email is required/i)).toBeInTheDocument();
    expect(await screen.findByText(/password is required/i)).toBeInTheDocument();
  });

  it("submits credentials to the backend and shows no error on success", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/auth/login")) {
        return Promise.resolve(
          jsonResponse({
            id: "11111111-1111-1111-1111-111111111111",
            email: "sarah@example.test",
            role: "CUSTOMER",
            firstName: "Sarah",
            lastName: "Johnson",
          }),
        );
      }
      return Promise.resolve(jsonResponse({ status: "UP" }));
    });
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders(<LoginPage />);

    await userEvent.type(screen.getByLabelText(/email address/i), "sarah@example.test");
    await userEvent.type(screen.getByLabelText(/password/i), "SuperSecret123");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/v1/auth/login"),
        expect.objectContaining({ method: "POST" }),
      );
    });

    expect(screen.queryByText(/invalid email or password/i)).not.toBeInTheDocument();
  });

  it("shows the backend's generic error message on invalid credentials", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/auth/login")) {
        return Promise.resolve(
          jsonResponse(
            { status: 401, error: "INVALID_CREDENTIALS", message: "Invalid email or password.", path: "/api/v1/auth/login" },
            401,
          ),
        );
      }
      return Promise.resolve(jsonResponse({ status: "UP" }));
    });
    vi.stubGlobal("fetch", fetchMock);

    renderWithProviders(<LoginPage />);

    await userEvent.type(screen.getByLabelText(/email address/i), "sarah@example.test");
    await userEvent.type(screen.getByLabelText(/password/i), "WrongPassword");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument();
  });
});
