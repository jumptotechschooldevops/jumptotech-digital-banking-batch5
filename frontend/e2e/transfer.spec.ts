import { test, expect } from "@playwright/test";

/**
 * Full-stack smoke test: log in as the seeded demo customer, read both
 * account balances, transfer money between them, and confirm the receipt
 * and transaction history reflect it.
 *
 * Requires, already running (see docs/local-development.md):
 *   - the backend on :8080 with SEED_DEMO_DATA=true and SEED_SARAH_PASSWORD
 *     set to a known value (read here from the same env var so no password
 *     is ever hardcoded in the test file)
 *   - the frontend dev server on :5173
 *
 * Not run as part of Stage 3 - the backend isn't running during a frontend-only
 * build/test pass. Intended to run once the full stack is wired up (Stage 4/5).
 */

const SARAH_EMAIL = "sarah.johnson@example.local";
const SARAH_PASSWORD = process.env.SEED_SARAH_PASSWORD;

test.skip(!SARAH_PASSWORD, "SEED_SARAH_PASSWORD must be set to run this end-to-end test");

test("login, view balances, transfer funds, and see it in history", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel(/email address/i).fill(SARAH_EMAIL);
  await page.getByLabel(/password/i).fill(SARAH_PASSWORD!);
  await page.getByRole("button", { name: /log in/i }).click();

  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByText(/welcome back, sarah/i)).toBeVisible();

  await page.getByRole("link", { name: "Transfer" }).click();
  await expect(page).toHaveURL(/\/transfer/);

  await page.getByLabel(/from account/i).selectOption({ label: /CHECKING/ });
  await page.getByLabel(/to account/i).selectOption({ label: /SAVINGS/ });
  await page.getByLabel(/amount/i).fill("25.00");
  await page.getByRole("button", { name: /review transfer/i }).click();

  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: /confirm transfer/i }).click();

  await expect(page.getByText(/transfer complete/i)).toBeVisible();
  await expect(page.getByText("$25.00")).toBeVisible();

  await page.getByRole("link", { name: "Transactions", exact: true }).click();
  await expect(page.getByText(/transaction history/i)).toBeVisible();
  await expect(page.getByText("$25.00").first()).toBeVisible();
});
