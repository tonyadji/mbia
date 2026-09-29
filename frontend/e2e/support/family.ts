import { expect, type Page } from '@playwright/test';
import { t, type Language } from './i18n';

/**
 * Opens "Start with me" (SCREEN-004) for a new Family, from Family Home: Family Home now starts with
 * a memory (mvp.md §14), so `Start with me` is reached from the empty tree (SCREEN-003). The spec
 * then fills and submits the form as before, which creates the User's own Person only (plan §3.5).
 */
export async function openStartWithMe(page: Page, language: Language) {
  await page
    .getByRole('navigation', { name: t(language, 'family:navigation.label') })
    .getByRole('link', { name: t(language, 'family:navigation.tree') })
    .click();
  await page.getByRole('link', { name: t(language, 'tree:empty.startWithMe') }).click();
  await expect(
    page.getByRole('heading', { level: 1, name: t(language, 'person:form.titleMe') }),
  ).toBeVisible();
}
