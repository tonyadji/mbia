import { expect, test } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify, signInOnKeycloak } from './support/keycloak';

/** Phase 1 journey (phase-1-walking-skeleton.md §1): sign up, verify, create a Family, sign out, sign in again. */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`first journey (${language})`, () => {
    // The UI language comes from the browser language (localization-and-kinship-labels.md §1).
    test.use({ locale });

    test('a new User signs up, creates a Family and finds it again after signing in', async ({
      page,
      request,
    }) => {
      const user = newUser();
      const familyName = `E2E ${user.email.slice(4, 12)}`;

      await page.goto('/');
      await expect(page.locator('html')).toHaveAttribute('lang', language);
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();

      await registerAndVerify(page, request, user);

      await expect(page).toHaveURL('/families/new');
      await expect(
        page.getByRole('heading', { name: t(language, 'family:create.title') }),
      ).toBeVisible();
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();

      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(familyName);
      await expect(
        page.getByRole('heading', {
          name: t(language, 'family:home.emptyTitle', { name: familyName }),
        }),
      ).toBeVisible();
      const familyUrl = page.url();

      await page.getByRole('link', { name: t(language, 'settings:open') }).click();
      await page.getByRole('button', { name: t(language, 'auth:signOut') }).click();
      await expect(
        page.getByRole('button', { name: t(language, 'auth:welcome.signIn') }),
      ).toBeVisible();

      await page.getByRole('button', { name: t(language, 'auth:welcome.signIn') }).click();
      await signInOnKeycloak(page, user);

      // One Family: signing in leads straight to its home.
      await expect(page).toHaveURL(familyUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(familyName);
      await expect(page.locator('html')).toHaveAttribute('lang', language);
    });
  });
}
