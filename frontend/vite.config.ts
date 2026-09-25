import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { configDefaults, defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react(), tailwindcss()],
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
});
