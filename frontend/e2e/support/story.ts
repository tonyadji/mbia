import { expect, type Page } from '@playwright/test';
import { t, type Language } from './i18n';

/** When a story happened on SCREEN-006 / SCREEN-014: on a day, in a year, or not known. */
export type StoryDate = { exact: string } | { year: string } | 'unknown';

/**
 * Fills the title, the story and, when given, when it happened on SCREEN-006 or SCREEN-014
 * (mvp.md §17). The related Persons and the photos are left to the spec.
 */
export async function writeStory(
  page: Page,
  language: Language,
  story: { title: string; text: string; date?: StoryDate },
) {
  await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.title);
  await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(story.text);
  if (story.date !== undefined) await setStoryDate(page, language, story.date);
}

/** `When did it happen?` of SCREEN-006 / SCREEN-014. */
export async function setStoryDate(page: Page, language: Language, date: StoryDate) {
  const when = page.getByRole('combobox', { name: t(language, 'memory:form.happenedAt.label') });
  const precision = (value: string) => ({ label: t(language, `person:form.precision.${value}`) });
  if (date === 'unknown') {
    await when.selectOption(precision('UNKNOWN'));
  } else if ('exact' in date) {
    await when.selectOption(precision('EXACT'));
    await page.getByLabel(t(language, 'memory:form.happenedAt.date')).fill(date.exact);
  } else {
    await when.selectOption(precision('YEAR_ONLY'));
    await page
      .getByRole('textbox', { name: t(language, 'memory:form.happenedAt.year') })
      .fill(date.year);
  }
}

/**
 * Adds a Person who is not in the tree yet from the related-Persons field of SCREEN-006
 * (`Add {typed name}`, OQ-065): created when the Memory is published.
 */
export async function addPersonOnTheWay(page: Page, language: Language, name: string) {
  await page.getByRole('button', { name: t(language, 'memory:form.addPerson') }).click();
  const sheet = page.getByRole('dialog', { name: t(language, 'memory:form.chooseTitle') });
  await sheet.getByRole('searchbox', { name: t(language, 'memory:form.searchLabel') }).fill(name);
  await sheet.getByRole('button', { name: t(language, 'memory:form.addNew', { name }) }).click();
  await page
    .getByRole('dialog', { name: t(language, 'memory:form.newTitle') })
    .getByRole('button', { name: t(language, 'memory:form.addThisPerson') })
    .click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

/** Publishes SCREEN-006 and waits for the Memory (SCREEN-013); returns its address. */
export async function publishStory(page: Page, language: Language) {
  await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
  await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
  return page.url();
}

/** The strip of years of the family story (SCREEN-002, SCREEN-016). */
export function yearStrip(page: Page, language: Language) {
  return page.getByRole('navigation', { name: t(language, 'memory:story.strip') });
}

/** The label of an entry of the strip: a year (or `undated`) and its number of Memories. */
export function stripEntry(language: Language, year: string, count: number) {
  const key = year === 'undated' ? 'memory:story.undatedEntry' : 'memory:story.entry';
  return t(language, `${key}_${count === 1 ? 'one' : 'other'}`, {
    year,
    count: String(count),
  });
}

/** The page never scrolls sideways (the e2e tsconfig has no DOM types: it runs in the page). */
export async function expectNoPageScroll(page: Page) {
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
