import { expect, test, type Page } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-27 (phase-2-core-family-graph.md): adding a Person similar to one already in the Family shows
 * the possible duplicate (SCREEN-004, person-relationships-collaboration.md §4.1); after "Create
 * anyway", the ADMIN merges the duplicate into the first profile (SCREEN-COMPONENT-004, mvp.md
 * §12), whose family links stay. Runs at phone width.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`duplicate and merge (${language})`, () => {
    test.use({ locale });

    test('a possible duplicate is shown, created anyway, then merged by the ADMIN', async ({
      page,
      request,
    }) => {
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
      await expect(page).toHaveURL(familyUrl);

      // Paul Mbida, my father.
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.FATHER') }).click();
      await firstName(page, language).fill('Paul');
      await lastName(page, language).fill('Mbida');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page).toHaveURL(familyUrl);

      // "paul MBIDA" again: the similar Person is shown, then created anyway.
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page
        .getByRole('link', { name: t(language, 'family:home.relatives.someoneElse') })
        .click();
      await firstName(page, language).fill('paul');
      await lastName(page, language).fill('MBIDA');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      const duplicates = page.getByRole('alert', {
        name: t(language, 'person:duplicate.title_one'),
      });
      await expect(duplicates.getByText('Paul Mbida', { exact: true })).toBeVisible();
      await expectNoPageScroll(page);
      await duplicates
        .getByRole('button', { name: t(language, 'person:duplicate.createAnyway') })
        .click();
      await expect(page).toHaveURL(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('paul MBIDA');

      // The ADMIN merges the duplicate into my father's profile.
      await page.getByRole('button', { name: t(language, 'person:merge.action') }).click();
      const dialog = page.getByRole('dialog', {
        name: t(language, 'person:merge.title', { name: 'paul MBIDA' }),
      });
      await dialog
        .getByRole('searchbox', { name: t(language, 'person:merge.search') })
        .fill('Paul');
      await dialog
        .getByRole('list', { name: t(language, 'person:search.results') })
        .getByRole('button', { name: /^Paul Mbida/ })
        .click();
      await expect(
        dialog.getByText(t(language, 'person:merge.kept'), { exact: true }),
      ).toBeVisible();
      await expectNoPageScroll(page);
      await dialog.getByRole('button', { name: t(language, 'person:merge.confirm') }).click();

      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul Mbida');
      await expect(page.getByRole('status').first()).toContainText(
        t(language, 'person:merge.done', { name: 'paul MBIDA', kept: 'Paul Mbida' }),
      );

      // One Paul left, still my father; the duplicate is no longer found.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expect(page.getByRole('button', { name: /^Paul Mbida/ })).toHaveCount(1);
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.search') }).click();
      await page.getByRole('searchbox', { name: t(language, 'person:search.label') }).fill('mbida');
      await expect(
        page.getByRole('list', { name: t(language, 'person:search.results') }).getByRole('button'),
      ).toHaveCount(1);
    });
  });
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}

function lastName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.lastName'), { exact: true });
}

/** The page never scrolls sideways at phone width. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
