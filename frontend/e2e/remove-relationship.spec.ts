import { expect, test, type Page } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-24 (phase-2-core-family-graph.md): a wrong parent link is removed from the profile after the
 * SCREEN-COMPONENT-003 confirmation, kinship changes right away, then the ADMIN restores it from
 * "Removed links" (SCREEN-005, mvp.md §13). Runs at phone width and on desktop.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`remove and restore a link (${language})`, () => {
    test.use({ locale });

    test('an ADMIN removes a wrong parent link, then restores it', async ({ page, request }) => {
      const user = newUser();
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      await page
        .getByLabel(t(language, 'family:create.nameLabel'))
        .fill(`E2E ${user.email.slice(4, 12)}`);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

      // Paul, my father.
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.FATHER') }).click();
      await firstName(page, language).fill('Paul');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul');
      await page
        .getByRole('list', { name: t(language, 'person:profile.relatives.children') })
        .getByRole('link', { name: /Alice/ })
        .click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alice');
      const parents = page.getByRole('list', {
        name: t(language, 'person:profile.relatives.parents'),
      });
      await expect(parents.getByRole('link', { name: /Paul/ })).toContainText(
        t(language, 'person:kinship.label.FATHER'),
      );

      // Remove: cancel once, then confirm.
      const remove = page.getByRole('button', {
        name: t(language, 'person:removeLink.actionFor', { name: 'Paul' }),
      });
      await remove.click();
      const confirmation = page.getByRole('dialog', {
        name: t(language, 'person:removeLink.warning'),
      });
      await confirmation
        .getByRole('button', { name: t(language, 'person:removeLink.cancel') })
        .click();
      await expect(confirmation).toBeHidden();
      await expect(parents.getByRole('link')).toHaveCount(1);
      await remove.click();
      await confirmation
        .getByRole('button', { name: t(language, 'person:removeLink.confirm') })
        .click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:removeLink.done', { name: 'Paul' }),
      );
      await expect(page.getByText(t(language, 'person:profile.noRelatives'))).toBeVisible();
      await expectNoPageScroll(page);

      // The ADMIN finds the removed link, with what Paul was to Alice.
      const removedLinks = page.getByText(t(language, 'person:removedLinks.title'), {
        exact: true,
      });
      await removedLinks.click();
      const removed = page.getByRole('list', { name: t(language, 'person:removedLinks.title') });
      await expect(removed.getByRole('listitem')).toContainText(
        t(language, 'person:kinship.step.PARENT.male', { to: 'Paul', from: 'Alice' }),
      );

      // Kinship changed right away: Paul is no longer my father.
      await removed.getByRole('link', { name: 'Paul' }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul');
      await expect(
        page.getByText(t(language, 'person:profile.relationship.NONE_KNOWN'), { exact: true }),
      ).toBeVisible();
      await page.goBack();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alice');

      // Restore.
      await removedLinks.click();
      await page
        .getByRole('button', {
          name: t(language, 'person:removedLinks.restoreFor', { name: 'Paul' }),
        })
        .click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:removedLinks.restored'),
      );
      await expect(parents.getByRole('link', { name: /Paul/ })).toContainText(
        t(language, 'person:kinship.label.FATHER'),
      );
      await expect(removedLinks).toBeHidden();
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
