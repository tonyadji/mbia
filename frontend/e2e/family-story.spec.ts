import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls, expectReadableScreen } from './support/accessibility';
import { openStartWithMe } from './support/family';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-61 (phase-6-family-story.md): at phone width, a Family reads its story year after year.
 * Without Memory, Family Home invites to tell a first memory (SCREEN-002); three Memories are told
 * (12 March 1962, 1975, undated); the date of a Memory leads to its year (SCREEN-013); "Our story"
 * shows the strip, by keyboard too, without scrolling the page sideways; a year shows its Memories
 * with their day, and `Previous year` / `Next year` move along the strip and stop at its ends
 * (SCREEN-016); a year that is not a number is not found.
 */
for (const { language, locale, dayMonth } of [
  { language: 'fr', locale: 'fr-FR', dayMonth: '12 mars' },
  { language: 'en', locale: 'en-US', dayMonth: 'March 12' },
] satisfies { language: Language; locale: string; dayMonth: string }[]) {
  test.describe(`the family story (${language})`, () => {
    test.use({ locale });

    test('a Family reads its story, year after year', async ({ page, request }) => {
      const familyUrl = await startFamily(page, request, language);
      const story = page.getByRole('region', { name: t(language, 'memory:story.title') });
      const stripOf = () =>
        page.getByRole('navigation', { name: t(language, 'memory:story.strip') });
      const titles =
        language === 'fr'
          ? { first: 'Le mariage de Marie', second: 'La maison de Douala', third: 'La recette' }
          : { first: "Marie's wedding", second: 'The house in Douala', third: 'The recipe' };

      // SCREEN-002: without Memory, the strip is replaced by the invitation.
      await expect(story.getByText(t(language, 'memory:story.invitation'))).toBeVisible();
      await expect(stripOf()).toHaveCount(0);
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);

      // The first Memory, told from the invitation, on 12 March 1962; its date leads to its year.
      await story.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      await publish(page, language, titles.first, { exact: '1962-03-12' });
      const exactDate = language === 'fr' ? '12 mars 1962' : 'March 12, 1962';
      await page
        .getByRole('link', { name: t(language, 'memory:screen.happenedOn', { date: exactDate }) })
        .click();
      await expect(page).toHaveURL(`${familyUrl}/story/1962`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:story.yearTitle', { year: '1962' }),
      );

      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      await publish(page, language, titles.second, { year: '1975' });
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      await publish(page, language, titles.third, {});

      // "Our story": the years in digits, oldest first, then the undated Memories.
      await page.goto(familyUrl);
      const strip = stripOf();
      const entries = strip.getByRole('link');
      const entryOf = (year: string) => t(language, 'memory:story.entry_one', { year, count: '1' });
      const undatedEntry = t(language, 'memory:story.undatedEntry_one', { count: '1' });
      await expect(entries).toHaveText([entryOf('1962'), entryOf('1975'), undatedEntry]);
      await expect(story.getByText(t(language, 'memory:story.invitation'))).toHaveCount(0);
      // Only the strip scrolls sideways, never the page.
      await expect(strip.getByRole('list')).toHaveCSS('overflow-x', 'auto');
      await expectNoPageScroll(page);
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);

      // By keyboard: the arrows move along the strip, Enter opens a year.
      await entries.first().focus();
      await page.keyboard.press('ArrowRight');
      await expect(entries.nth(1)).toBeFocused();
      await page.keyboard.press('ArrowLeft');
      await expect(entries.first()).toBeFocused();
      await page.keyboard.press('Enter');

      // SCREEN-016: What happened in 1962, the current entry marked, its Memory with its day.
      await expect(page).toHaveURL(`${familyUrl}/story/1962`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:story.yearTitle', { year: '1962' }),
      );
      await expect(stripOf().locator('[aria-current="page"]')).toHaveText(entryOf('1962'));
      await expect(page.getByRole('link', { name: new RegExp(titles.first) })).toContainText(
        dayMonth,
      );
      const previous = page.getByRole('button', { name: t(language, 'memory:story.previous') });
      const next = page.getByRole('button', { name: t(language, 'memory:story.next') });
      await expect(previous).toBeDisabled();
      await expectNoPageScroll(page);
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);

      // Next year, then the undated Memories, without going back.
      await next.click();
      await expect(page).toHaveURL(`${familyUrl}/story/1975`);
      await expect(page.getByRole('link', { name: new RegExp(titles.second) })).toBeVisible();
      await expect(stripOf().locator('[aria-current="page"]')).toHaveText(entryOf('1975'));
      await next.click();
      await expect(page).toHaveURL(`${familyUrl}/story/undated`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:story.undated'),
      );
      await expect(page.getByRole('link', { name: new RegExp(titles.third) })).toBeVisible();
      await expect(next).toBeDisabled();
      await expect(previous).toBeEnabled();
      await expectNoPageScroll(page);
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);

      // A year that is not a number from 1 to 9999 is not found.
      await page.goto(`${familyUrl}/story/0`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:story.notFound.title'),
      );
      await expectAccessibleControls(page);
    });
  });
}

/** Publishes a story from SCREEN-006, dated exactly, by its year, or not at all. */
async function publish(
  page: Page,
  language: Language,
  title: string,
  date: { exact?: string; year?: string },
) {
  await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
  await page
    .getByLabel(t(language, 'memory:form.contentLabel'))
    .fill(language === 'fr' ? 'Toute la famille était là.' : 'The whole family was there.');
  const when = page.getByRole('combobox', { name: t(language, 'memory:form.happenedAt.label') });
  if (date.exact !== undefined) {
    await when.selectOption({ label: t(language, 'person:form.precision.EXACT') });
    await page.getByLabel(t(language, 'memory:form.happenedAt.date')).fill(date.exact);
  } else if (date.year !== undefined) {
    await when.selectOption({ label: t(language, 'person:form.precision.YEAR_ONLY') });
    await page
      .getByRole('textbox', { name: t(language, 'memory:form.happenedAt.year') })
      .fill(date.year);
  }
  await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
  await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
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
