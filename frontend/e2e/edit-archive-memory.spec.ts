import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-33 (phase-3-family-memories.md): a User edits the story they wrote (SCREEN-014), then
 * archives it after a confirmation (SCREEN-013), which brings them back to Family Home: it is no
 * longer found, nor listed in the Family Memories. Runs at phone width, in French and English.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`edit and archive a Memory (${language})`, () => {
    test.use({ locale });

    test('a User corrects their story, then archives it', async ({ page, request }) => {
      const user = newUser();
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      await page
        .getByLabel(t(language, 'family:create.nameLabel'))
        .fill(`E2E ${user.email.slice(4, 12)}`);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();

      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      await expect(page).toHaveURL(familyUrl);

      // A story about me, published from Family Home.
      await page.getByRole('link', { name: t(language, 'family:home.addMemory') }).click();
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill('Le marché');
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill('Une première version.');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();

      // Edit it (SCREEN-014).
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await expect(page).toHaveURL(`${memoryUrl}/edit`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:edit.title'),
      );
      const title = page.getByLabel(t(language, 'memory:form.titleLabel'));
      await expect(title).toHaveValue('Le marché');
      await title.fill('Le marché du samedi');
      await page
        .getByLabel(t(language, 'memory:form.contentLabel'))
        .fill('Chaque samedi, maman nous emmenait au marché.\nAvant le lever du soleil.');
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();

      await expect(page).toHaveURL(memoryUrl);
      await expect(page.getByRole('status')).toContainText(
        t(language, 'memory:saved', { title: 'Le marché du samedi' }),
      );
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Le marché du samedi');
      await expect(page.getByText('Avant le lever du soleil.', { exact: false })).toBeVisible();

      // Archive it: a Modal confirms, then the User is back on Family Home.
      await page.getByRole('button', { name: t(language, 'memory:screen.archive') }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toContainText(t(language, 'memory:archive.warning'));
      await expectNoPageScroll(page);
      await dialog.getByRole('button', { name: t(language, 'memory:archive.confirm') }).click();
      await expect(page).toHaveURL(familyUrl);

      // It is gone for everyone.
      await page.goto(memoryUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:screen.notFound.title'),
      );
      await page.goto(`${familyUrl}/memories`);
      await expect(page.getByText(t(language, 'memory:family.empty'))).toBeVisible();
    });
  });
}

/** The page never scrolls sideways at phone width. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
