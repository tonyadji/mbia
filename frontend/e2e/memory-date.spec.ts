import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls, expectReadableScreen } from './support/accessibility';
import { openStartWithMe } from './support/family';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-60 (phase-6-family-story.md): at phone width, a User says when their story happened
 * (SCREEN-006): a year in the future is refused before anything is sent, 1975 is published and
 * read "In 1975" (SCREEN-013); the date is changed to 12 March 1962, read in the reader's
 * language, then removed (SCREEN-014), and the Memory no longer says when it happened.
 */
for (const { language, locale, exactDate } of [
  { language: 'fr', locale: 'fr-FR', exactDate: '12 mars 1962' },
  { language: 'en', locale: 'en-US', exactDate: 'March 12, 1962' },
] satisfies { language: Language; locale: string; exactDate: string }[]) {
  test.describe(`the date of a Memory (${language})`, () => {
    test.use({ locale });

    test('a User dates their story, changes its date, then removes it', async ({
      page,
      request,
    }) => {
      await startFamily(page, request, language);
      const when = () =>
        page.getByRole('combobox', { name: t(language, 'memory:form.happenedAt.label') });
      const yearField = () =>
        page.getByRole('textbox', { name: t(language, 'memory:form.happenedAt.year') });
      const precision = (value: string) => t(language, `person:form.precision.${value}`);
      const title = language === 'fr' ? 'Le mariage de Marie' : "Marie's wedding";

      // SCREEN-006: a year in the future is refused before sending, and explains itself.
      await page.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
      await page
        .getByLabel(t(language, 'memory:form.contentLabel'))
        .fill(language === 'fr' ? 'Toute la famille était là.' : 'The whole family was there.');
      await expect(when()).toHaveValue('UNKNOWN');
      await when().selectOption({ label: precision('YEAR_ONLY') });
      await yearField().fill(String(new Date().getFullYear() + 1));
      await expectAccessibleControls(page);
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(yearField()).toHaveAccessibleDescription(
        t(language, 'memory:form.happenedAt.future'),
      );
      await expect(yearField()).toBeFocused();
      await expect(page).toHaveURL(/\/memories\/new$/);

      // 1975 is published, and read in digits only.
      await yearField().fill('1975');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();
      await expect(
        page.getByText(t(language, 'memory:screen.happenedIn', { year: '1975' }), { exact: true }),
      ).toBeVisible();
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);
      await expectNoPageScroll(page);

      // SCREEN-014: the date becomes 12 March 1962.
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await expect(page).toHaveURL(`${memoryUrl}/edit`);
      await expect(yearField()).toHaveValue('1975');
      await when().selectOption({ label: precision('EXACT') });
      await page.getByLabel(t(language, 'memory:form.happenedAt.date')).fill('1962-03-12');
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();
      await expect(page).toHaveURL(memoryUrl);
      await expect(
        page.getByText(t(language, 'memory:screen.happenedOn', { date: exactDate }), {
          exact: true,
        }),
      ).toBeVisible();

      // Then its date is removed: the Memory no longer says when it happened.
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await when().selectOption({ label: precision('UNKNOWN') });
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();
      await expect(page).toHaveURL(memoryUrl);
      await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
      await expect(
        page.getByText(t(language, 'memory:screen.happenedOn', { date: exactDate }), {
          exact: true,
        }),
      ).toHaveCount(0);
      await expect(
        page.getByText(t(language, 'memory:screen.happenedIn', { year: '1962' }), { exact: true }),
      ).toHaveCount(0);
    });
  });
}

/** A new User creates a Family and adds themselves as Alice; returns the Family Home URL. */
async function startFamily(
  page: Page,
  request: Parameters<typeof registerAndVerify>[1],
  language: Language,
) {
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
  await openStartWithMe(page, language);
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
  await expect(page).toHaveURL(familyUrl);
  return familyUrl;
}

/** The page never scrolls sideways. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
