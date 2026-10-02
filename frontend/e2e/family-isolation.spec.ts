import { expect, test } from '@playwright/test';
import { t } from './support/i18n';
import { newUser, registerAndVerify, signInOnKeycloak } from './support/keycloak';

/** Local test user of the realm (infrastructure/keycloak/README.md), language `en`. */
const BOB = { email: 'bob@mbia.local', password: 'bob-local-1' };

test.use({ locale: 'en-US' });

// Family isolation (AGENTS.md §5, mvp.md §28 "Cross-Family access must fail").
test('a User opening another User\'s Family sees only "family not found"', async ({
  browser,
  page,
  request,
}) => {
  const userA = newUser();
  const familyName = `E2E private ${userA.email.slice(4, 12)}`;

  await page.goto('/');
  await page.getByRole('button', { name: t('en', 'auth:welcome.createFamily') }).click();
  await registerAndVerify(page, request, userA);
  await page.getByLabel(t('en', 'family:create.nameLabel')).fill(familyName);
  await page.getByRole('button', { name: t('en', 'family:create.submit') }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(familyName);
  const familyUrl = page.url();

  // User B, in a browser of their own.
  const contextB = await browser.newContext({ locale: 'en-US' });
  const pageB = await contextB.newPage();
  await pageB.goto('/');
  await pageB.getByRole('button', { name: t('en', 'auth:welcome.signIn') }).click();
  await signInOnKeycloak(pageB, BOB);
  // Signed in: back in Mbia, past the sign-in callback.
  await expect(pageB).toHaveURL(/localhost:5173\/(home|families\/)/);

  await pageB.goto(familyUrl);
  await expect(
    pageB.getByRole('heading', { name: t('en', 'family:notFound.title') }),
  ).toBeVisible();
  await expect(pageB.getByText(familyName)).toHaveCount(0);
  await contextB.close();
});
