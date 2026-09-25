import { randomUUID } from 'node:crypto';
import type { APIRequestContext, Page } from '@playwright/test';
import { verificationLink } from './mailpit';

export const KEYCLOAK_URL = process.env.E2E_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/mbia';

export interface TestUser {
  email: string;
  password: string;
}

/** A new account per test: runs are independent and can repeat without resetting the local data. */
export function newUser(): TestUser {
  return { email: `e2e-${randomUUID()}@mbia.local`, password: 'e2e-password-1' };
}

/**
 * Keycloak pages use the default theme; its field ids are stable, its texts follow Keycloak's own translations.
 * With email verification on, the password is chosen after the email is verified (infrastructure/keycloak/README.md).
 */
export async function registerAndVerify(page: Page, request: APIRequestContext, user: TestUser) {
  await page.waitForURL(`${KEYCLOAK_URL}/**`);
  await page.locator('#email').fill(user.email);
  await page.locator('#firstName').fill('E2E');
  await page.locator('#lastName').fill('Tester');
  await page.locator('input[type=submit]').click();
  await page.waitForURL(/execution=VERIFY_EMAIL/);

  // Opened in the same browser, the link continues the pending sign-up.
  await page.goto(await verificationLink(request, user.email));
  await page.locator('#password-new').fill(user.password);
  await page.locator('#password-confirm').fill(user.password);
  await page.locator('#kc-submit').click();
}

export async function signInOnKeycloak(page: Page, user: TestUser) {
  await page.waitForURL(`${KEYCLOAK_URL}/**`);
  await page.locator('#username').fill(user.email);
  await page.locator('#password').fill(user.password);
  await page.locator('#kc-login').click();
}
