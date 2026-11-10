import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // The backend (port 8080) and frontend dev server (port 5173, proxying /api to 8080)
  // must already be running - see docs/local-development.md. Not started automatically
  // here because the backend needs the dev database and seed data in place first.
});
