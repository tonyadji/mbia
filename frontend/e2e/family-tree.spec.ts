import { expect, test, type Page } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-23 (phase-2-core-family-graph.md): the tree (SCREEN-003) grows from its own "Add…" slots, then
 * the User navigates three generations with the Quick View (SCREEN-COMPONENT-001), sees the
 * grandparent's label and its path (localization-and-kinship-labels.md §3–4). Runs at phone width
 * and on desktop (playwright.config.ts).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`family tree (${language})`, () => {
    test.use({ locale });

    test('a User builds and navigates three generations', async ({ page, request }) => {
      const user = newUser();
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      await page
        .getByLabel(t(language, 'family:create.nameLabel'))
        .fill(`E2E ${user.email.slice(4, 12)}`);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

      // Family Home → tree centred on "me".
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(t(language, 'tree:title'));
      await expect(card(page, 'focus')).toContainText('Alice');
      const treeUrl = page.url();

      // Both parents from the tree's own slot; the slot disappears with two parents.
      await addRelative(page, language, 'person:relative.parents', 'MOTHER', 'Awa');
      await expect(card(page, 'parent')).toHaveText(/Awa/);
      await addRelative(page, language, 'person:relative.parents', 'FATHER', 'Paul');
      await expect(card(page, 'parent')).toHaveCount(2);
      await expect(
        page.getByRole('button', { name: t(language, 'person:relative.parents') }),
      ).toHaveCount(0);

      // Recenter on the mother, then add her own mother.
      await card(page, 'parent').filter({ hasText: 'Awa' }).click();
      const awa = page.getByRole('dialog', { name: 'Awa' });
      await expect(awa).toContainText(t(language, 'person:kinship.label.MOTHER'));
      await awa
        .getByRole('button', { name: t(language, 'tree:quickView.center', { name: 'Awa' }) })
        .click();
      await expect(card(page, 'focus')).toContainText('Awa');
      await expect(card(page, 'child')).toContainText('Alice');
      await addRelative(page, language, 'person:relative.parents', 'MOTHER', 'Marie');
      await expect(card(page, 'focus')).toContainText('Awa');
      await expect(card(page, 'parent')).toContainText('Marie');
      await expectNoPageScroll(page);

      // The grandmother's label and how it is derived.
      await card(page, 'parent').click();
      const marie = page.getByRole('dialog', { name: 'Marie' });
      await expectQuickViewPlacement(page, marie);
      await expect(marie).toContainText(t(language, 'person:kinship.label.GRANDMOTHER'));
      await marie.getByRole('button', { name: t(language, 'tree:quickView.seeHow') }).click();
      await expect(marie.getByRole('listitem')).toHaveText([
        t(language, 'person:kinship.step.PARENT.female', { to: 'Awa', from: 'Alice' }),
        t(language, 'person:kinship.step.PARENT.female', { to: 'Marie', from: 'Awa' }),
      ]);
      await page.keyboard.press('Escape');
      await expect(marie).toBeHidden();

      // The focus survives a reload; opening the tree again starts from "me" (OQ-018).
      await page.reload();
      await expect(card(page, 'focus')).toContainText('Awa');
      await page.goto(treeUrl);
      await expect(card(page, 'focus')).toContainText('Alice');
      await expect(card(page, 'parent')).toHaveCount(2);
    });
  });
}

function card(page: Page, role: 'parent' | 'focus' | 'partner' | 'child') {
  return page.locator(`[data-role="${role}"]`).getByRole('button');
}

/** Through the tree slot, "Add a parent" → choice → first name only → back on the tree. */
async function addRelative(
  page: Page,
  language: Language,
  slotKey: string,
  relation: 'MOTHER' | 'FATHER',
  firstName: string,
) {
  await page.getByRole('button', { name: t(language, slotKey) }).click();
  await page
    .getByRole('dialog')
    .getByRole('link', { name: t(language, `person:relative.choices.${relation}`) })
    .click();
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill(firstName);
  await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
  await expect(page.getByRole('status')).toContainText(firstName);
  await expect(page).toHaveURL(/\/tree\?focus=/);
}

/** The tree pans inside its canvas: the page itself never scrolls sideways. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

/** A bottom sheet on a phone, a side panel on desktop (family-tree-ux.md §8). */
async function expectQuickViewPlacement(page: Page, dialog: ReturnType<Page['getByRole']>) {
  const viewport = page.viewportSize();
  const box = await dialog.boundingBox();
  if (!viewport || !box) throw new Error('no viewport or dialog');
  expect(Math.round(box.x + box.width)).toBe(viewport.width);
  if (viewport.width < 640) {
    expect(Math.round(box.x)).toBe(0);
    expect(Math.round(box.y + box.height)).toBe(viewport.height);
  } else {
    expect(box.x).toBeGreaterThan(viewport.width / 2);
    expect(Math.round(box.y)).toBe(0);
  }
}
