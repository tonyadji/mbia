import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { configDefaults, defineConfig } from 'vitest/config';
import {
  applyLocalDefaults,
  checkStagingEnv,
  STAGING_MODE,
  stagingRobotsTxt,
} from './scripts/build-env.js';

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, import.meta.dirname, 'VITE_');
  if (mode === STAGING_MODE) {
    // The staging build never falls back to a local address: every value comes from the workflow (ADR-010).
    const problems = command === 'build' ? checkStagingEnv(env) : [];
    if (problems.length > 0) {
      throw new Error(`Cannot build for staging:\n  - ${problems.join('\n  - ')}`);
    }
  } else {
    applyLocalDefaults(env);
  }

  return {
    plugins: [react(), tailwindcss(), stagingRobotsTxt(mode)],
    server: {
      port: 5173,
      strictPort: true,
    },
    // Same origin as `dev`: the only one accepted by the Keycloak client (used by the E2E tests).
    preview: {
      port: 5173,
      strictPort: true,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      // Playwright end-to-end tests (npm run test:e2e).
      exclude: [...configDefaults.exclude, 'e2e/**'],
    },
  };
});
