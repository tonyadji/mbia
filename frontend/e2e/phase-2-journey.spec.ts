import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-28: the Phase 2 journey of phase-2-core-family-graph.md §1, end to end, at phone width, in
 * French and English. Each screen is also checked for accessible controls (design-guidelines.md
 * §9) and for no sideways page scroll. The profile's History shows the edit (data-model.md §18).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`Phase 2 journey (${language})`, () => {
    test.use({ locale });

    test('from signing in to merging a duplicate', async ({ page, request }) => {
      // Sign in, create a Family, "Start with me".
      const user = newUser();
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      await expectScreen(page);
      await page
        .getByLabel(t(language, 'family:create.nameLabel'))
        .fill(`E2E ${user.email.slice(4, 12)}`);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();
      await expectScreen(page);
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await expectScreen(page);
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      await expect(page).toHaveURL(familyUrl);

      // My father, then my mother.
      await addMyRelative(page, language, 'FATHER', 'Paul', 'Mbida');
      await expect(page).toHaveURL(familyUrl);
      await addMyRelative(page, language, 'MOTHER', 'Awa');
      await expect(page).toHaveURL(familyUrl);
      await expectScreen(page);

      // A grandparent: my mother's mother, from my mother's profile.
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa');
      await page.getByRole('button', { name: t(language, 'person:relative.menu') }).click();
      await page.getByRole('link', { name: t(language, 'person:relative.choices.MOTHER') }).click();
      await expectScreen(page);
      await firstName(page, language).fill('Marie');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page.getByRole('status').first()).toContainText('Marie');

      // Open the tree, recenter on the grandparent and see "your grandmother" and its path.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expect(card(page, 'focus')).toContainText('Alice');
      await expect(card(page, 'parent')).toHaveCount(2);
      await expectScreen(page);
      await card(page, 'parent').filter({ hasText: 'Awa' }).click();
      await page
        .getByRole('dialog', { name: 'Awa' })
        .getByRole('button', { name: t(language, 'tree:quickView.center', { name: 'Awa' }) })
        .click();
      await expect(card(page, 'focus')).toContainText('Awa');
      await card(page, 'parent').filter({ hasText: 'Marie' }).click();
      await page
        .getByRole('dialog', { name: 'Marie' })
        .getByRole('button', { name: t(language, 'tree:quickView.center', { name: 'Marie' }) })
        .click();
      await expect(card(page, 'focus')).toContainText('Marie');
      await card(page, 'focus').click();
      const marie = page.getByRole('dialog', { name: 'Marie' });
      await expect(marie).toContainText(t(language, 'person:kinship.label.GRANDMOTHER'));
      await expectScreen(page);
      await marie.getByRole('button', { name: t(language, 'tree:quickView.seeHow') }).click();
      await expect(marie.getByRole('listitem')).toHaveText([
        t(language, 'person:kinship.step.PARENT.female', { to: 'Awa', from: 'Alice' }),
        t(language, 'person:kinship.step.PARENT.female', { to: 'Marie', from: 'Awa' }),
      ]);
      await page.keyboard.press('Escape');
      await expect(marie).toBeHidden();

      // Search for a Person and open her profile.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.search') }).click();
      await page.getByRole('searchbox', { name: t(language, 'person:search.label') }).fill('awa');
      const results = page.getByRole('list', { name: t(language, 'person:search.results') });
      await expect(results.getByRole('button')).toHaveCount(1);
      await expectScreen(page);
      await results.getByRole('button', { name: /^Awa/ }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa');
      await expectScreen(page);

      // Edit an allowed field; the History shows the change.
      await page.getByRole('link', { name: t(language, 'person:profile.edit') }).click();
      await expectScreen(page);
      await page.getByLabel(t(language, 'person:form.birthPrecision')).selectOption('YEAR_ONLY');
      await page.getByLabel(t(language, 'person:form.birthYear')).fill('1956');
      await page.getByRole('button', { name: t(language, 'person:edit.submit') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa');
      await page.getByText(t(language, 'person:history.title'), { exact: true }).click();
      const history = page.getByRole('list', { name: t(language, 'person:history.title') });
      await expect(history.getByRole('listitem').first()).toContainText(
        t(language, 'person:history.fieldChanged', {
          field: t(language, 'person:history.fields.birth'),
          from: t(language, 'person:form.precision.UNKNOWN'),
          to: '1956',
        }),
      );
      await expect(history.getByRole('listitem').last()).toContainText(
        t(language, 'person:history.actions.PERSON_CREATED'),
      );
      await expectScreen(page);

      // Remove an incorrect relationship, then restore it as ADMIN.
      const children = page.getByRole('list', {
        name: t(language, 'person:profile.relatives.children'),
      });
      await page
        .getByRole('button', {
          name: t(language, 'person:removeLink.actionFor', { name: 'Alice' }),
        })
        .click();
      await expectScreen(page);
      await page
        .getByRole('dialog', { name: t(language, 'person:removeLink.warning') })
        .getByRole('button', { name: t(language, 'person:removeLink.confirm') })
        .click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:removeLink.done', { name: 'Alice' }),
      );
      await expect(children).toHaveCount(0);
      await page.getByText(t(language, 'person:removedLinks.title'), { exact: true }).click();
      await expectScreen(page);
      await page
        .getByRole('button', {
          name: t(language, 'person:removedLinks.restoreFor', { name: 'Alice' }),
        })
        .click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:removedLinks.restored'),
      );
      await expect(children.getByRole('link', { name: /Alice/ })).toBeVisible();

      // A possible duplicate of my father, created anyway, then merged by the ADMIN.
      await page.goto(familyUrl);
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
      await expectScreen(page);
      await duplicates
        .getByRole('button', { name: t(language, 'person:duplicate.createAnyway') })
        .click();
      await expect(page).toHaveURL(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('paul MBIDA');
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
      await expectScreen(page);
      await dialog.getByRole('button', { name: t(language, 'person:merge.confirm') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul Mbida');
      await page.getByText(t(language, 'person:history.title'), { exact: true }).click();
      await expect(
        page
          .getByRole('list', { name: t(language, 'person:history.title') })
          .getByRole('listitem')
          .first(),
      ).toContainText(t(language, 'person:history.actions.PERSONS_MERGED'));
      await expectScreen(page);
    });
  });
}

/** "Add a relative" from Family Home, first and last name only. */
async function addMyRelative(
  page: Page,
  language: Language,
  relation: 'MOTHER' | 'FATHER',
  first: string,
  last?: string,
) {
  await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
  await page.getByRole('link', { name: t(language, `family:home.relatives.${relation}`) }).click();
  await firstName(page, language).fill(first);
  if (last) await lastName(page, language).fill(last);
  await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
}

function card(page: Page, role: 'parent' | 'focus' | 'partner' | 'child') {
  return page.locator(`[data-role="${role}"]`).getByRole('button');
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}

function lastName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.lastName'), { exact: true });
}

/** Accessible controls, and a page that never scrolls sideways at phone width. */
async function expectScreen(page: Page) {
  await expectAccessibleControls(page);
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
