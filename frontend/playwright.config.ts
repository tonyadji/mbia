import { defineConfig, devices } from '@playwright/test';

const CI = Boolean(process.env.CI);

/**
 * End-to-end tests (technical-specification.md §17). Playwright serves the built frontend; docker compose and the
 * backend must already run (see e2e/support/global-setup.ts).
 */
export default defineConfig({
  testDir: 'e2e',
  globalSetup: './e2e/support/global-setup.ts',
  fullyParallel: true,
  forbidOnly: CI,
  retries: CI ? 1 : 0,
  reporter: CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    // The only origin accepted by the Keycloak client `mbia-web`.
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      // Mobile-first (AGENTS.md §8): phone width.
      name: 'chromium-phone',
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } },
    },
    {
      // The tree (PR-23) and the profile's links (PR-24) also work on desktop.
      name: 'chromium-desktop',
      testMatch: ['family-tree.spec.ts', 'remove-relationship.spec.ts'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
  ],
  webServer: {
    command: 'npm run build && npm run preview',
    url: 'http://localhost:5173',
    // Locally, a running `npm run dev` is reused.
    reuseExistingServer: !CI,
    timeout: 120_000,
  },
});
