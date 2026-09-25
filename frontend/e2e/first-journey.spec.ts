import { expect, test } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify, signInOnKeycloak } from './support/keycloak';

/**
 * Phase 1 journey (phase-1-walking-skeleton.md §1): sign up, verify, create a Family, sign out, sign in again;
 * with "Start with me" (PR-17), the Person profile read & edit (PR-18) and claim / unclaim (PR-19)
 * from Phase 2.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`first journey (${language})`, () => {
    // The UI language comes from the browser language (localization-and-kinship-labels.md §1).
    test.use({ locale });

    test('a new User signs up, creates a Family, starts with themselves, edits their profile and finds it again after signing in', async ({
      page,
      context,
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

      // "Start with me" (SCREEN-004): only the first name is required.
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await expect(
        page.getByRole('heading', { level: 1, name: t(language, 'person:form.titleMe') }),
      ).toBeVisible();
      await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

      await expect(page).toHaveURL(familyUrl);
      await expect(page.getByRole('status')).toContainText(
        t(language, 'family:home.selfAdded', { family: familyName }),
      );
      await expect(
        page.getByText(t(language, 'family:home.personCount_one', { count: '1' }), { exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'family:home.addPerson') }),
      ).toBeVisible();

      // Profile (SCREEN-005) and Edit Person (SCREEN-012).
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page).toHaveURL(/\/persons\/[0-9a-f-]{36}$/);
      const profileUrl = page.url();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alice');
      await expect(
        page.getByText(t(language, 'person:profile.relationship.SELF'), { exact: true }),
      ).toBeVisible();

      // Unlink, then "This is me" again (PR-19).
      await page.getByRole('button', { name: t(language, 'person:profile.unclaim') }).click();
      await expect(page.getByRole('status')).toHaveText(t(language, 'person:profile.unclaimed'));
      await expect(
        page.getByText(t(language, 'person:profile.relationship.SELF'), { exact: true }),
      ).toBeHidden();
      await page.getByRole('button', { name: t(language, 'person:profile.claim') }).click();
      await expect(page.getByRole('status')).toHaveText(t(language, 'person:profile.claimed'));
      await expect(
        page.getByText(t(language, 'person:profile.relationship.SELF'), { exact: true }),
      ).toBeVisible();

      // A second tab opens the same form before the first one saves.
      const otherTab = await context.newPage();
      await otherTab.goto(`${profileUrl}/edit`);
      await expect(
        otherTab.getByLabel(t(language, 'person:form.lastName'), { exact: true }),
      ).toHaveValue('');

      await page.getByRole('link', { name: t(language, 'person:profile.edit') }).click();
      await page.getByLabel(t(language, 'person:form.lastName'), { exact: true }).fill('Martin');
      await page
        .getByLabel(t(language, 'person:form.birthPrecision'), { exact: true })
        .selectOption('YEAR_ONLY');
      await page.getByLabel(t(language, 'person:form.birthYear'), { exact: true }).fill('1990');
      await page.getByRole('button', { name: t(language, 'person:edit.submit') }).click();
      await expect(page).toHaveURL(profileUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alice Martin');
      await expect(
        page.getByText(t(language, 'person:profile.birthYear', { year: '1990' })),
      ).toBeVisible();

      // The stale tab is told to reload; nothing is overwritten.
      await otherTab.getByLabel(t(language, 'person:form.lastName'), { exact: true }).fill('Stale');
      await otherTab.getByRole('button', { name: t(language, 'person:edit.submit') }).click();
      await expect(otherTab.getByRole('alert')).toContainText(t(language, 'person:edit.conflict'));
      await otherTab.getByRole('button', { name: t(language, 'person:edit.reload') }).click();
      await expect(
        otherTab.getByLabel(t(language, 'person:form.lastName'), { exact: true }),
      ).toHaveValue('Martin');
      await otherTab.close();

      await page.getByRole('link', { name: t(language, 'settings:back') }).click();
      await expect(page).toHaveURL(familyUrl);

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
      await expect(
        page.getByText(t(language, 'family:home.personCount_one', { count: '1' }), { exact: true }),
      ).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('lang', language);
    });
  });
}
