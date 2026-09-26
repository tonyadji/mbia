import { expect, test, type Page } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-26 (phase-2-core-family-graph.md): an ADMIN archives a Person created by mistake after a
 * confirmation; the Person leaves the tree and search, is found again in "Archived people"
 * (SCREEN-007) and restored from the archived profile (SCREEN-005, mvp.md §13). Runs at phone width.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`archive and restore a Person (${language})`, () => {
    test.use({ locale });

    test('an ADMIN archives a Person, then restores it', async ({ page, request }) => {
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
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

      // Paul, my father, added by mistake.
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.FATHER') }).click();
      await firstName(page, language).fill('Paul');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul');
      await expect(page).toHaveURL(/\/persons\/[0-9a-f-]{36}$/);
      const profileUrl = page.url();

      // Archive: cancel once, then confirm.
      const archive = page.getByRole('button', { name: t(language, 'person:archive.action') });
      await archive.click();
      const confirmation = page.getByRole('dialog', {
        name: t(language, 'person:archive.warning', { name: 'Paul' }),
      });
      await confirmation
        .getByRole('button', { name: t(language, 'person:archive.cancel') })
        .click();
      await expect(confirmation).toBeHidden();
      await archive.click();
      await confirmation
        .getByRole('button', { name: t(language, 'person:archive.confirm') })
        .click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:archive.done', { name: 'Paul' }),
      );
      await expect(page.getByText(t(language, 'person:archived.notice'))).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'person:profile.edit') }),
      ).toBeHidden();
      await expectNoPageScroll(page);

      // Paul is gone from the tree and from search.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expect(page.getByRole('button', { name: /^Alice/ })).toBeVisible();
      await expect(page.getByRole('button', { name: /^Paul/ })).toHaveCount(0);
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.search') }).click();
      await page.getByRole('searchbox', { name: t(language, 'person:search.label') }).fill('Paul');
      await expect(
        page.getByText(t(language, 'person:search.noResult', { text: 'Paul' })),
      ).toBeVisible();

      // The ADMIN finds Paul among the archived people and restores him.
      await page.getByRole('link', { name: t(language, 'person:archivedPeople.title') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'person:archivedPeople.title'),
      );
      await page
        .getByRole('list', { name: t(language, 'person:search.results') })
        .getByRole('button', { name: /^Paul/ })
        .click();
      await expect(page).toHaveURL(profileUrl);
      await page.getByRole('button', { name: t(language, 'person:archived.restore') }).click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:archived.restored', { name: 'Paul' }),
      );
      await expect(page.getByText(t(language, 'person:archived.notice'))).toBeHidden();

      // Paul is back in the tree, still my father.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expect(page.getByRole('button', { name: /^Paul/ })).toBeVisible();
    });
  });
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}

/** The page never scrolls sideways at phone width. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
