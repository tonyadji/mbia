import { expect, test } from '@playwright/test';
import { t } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-25 (phase-2-core-family-graph.md): SCREEN-007 from general navigation, from the tree and
 * from Add Relative, where an existing Person becomes the pending relative (SCREEN-004). "Eloise"
 * finds "Éloïse" (mvp.md §19).
 */
test.use({ locale: 'fr-FR' });

test('a User finds a Person from home, links her as their mother, then finds themselves from the tree', async ({
  page,
  request,
}) => {
  const language = 'fr';
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

  // Me, then Éloïse, not linked to anyone yet.
  await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
  await expect(page).toHaveURL(familyUrl);
  await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
  await page.getByRole('link', { name: t(language, 'family:home.relatives.someoneElse') }).click();
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Éloïse');
  await page.getByLabel(t(language, 'person:form.lastName'), { exact: true }).fill('Ngo');
  await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
  await expect(page).toHaveURL(familyUrl);

  // General navigation: "Eloise" finds "Éloïse"; the result opens her profile.
  await page.getByRole('link', { name: t(language, 'family:home.search') }).click();
  await page.getByRole('searchbox', { name: t(language, 'person:search.label') }).fill('Eloise');
  const results = page.getByRole('list', { name: t(language, 'person:search.results') });
  await expect(results.getByRole('button')).toHaveCount(1);
  await results.getByRole('button', { name: /Éloïse Ngo/ }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Éloïse Ngo');

  // Add Relative: Éloïse, already in the family, becomes my mother.
  await page.goto(familyUrl);
  await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
  await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
  await page
    .getByRole('searchbox', { name: t(language, 'person:relative.existing.search') })
    .fill('ngo');
  await page.getByRole('button', { name: /Éloïse Ngo/ }).click();
  await page
    .getByRole('button', {
      name: t(language, 'person:relative.existing.link', { name: 'Éloïse Ngo', anchor: 'Alice' }),
    })
    .click();
  await expect(page).toHaveURL(familyUrl);
  await expect(page.getByRole('status')).toContainText(
    t(language, 'person:relative.linked', { name: 'Éloïse Ngo', anchor: 'Alice' }),
  );
  await expect(
    page.getByText(t(language, 'family:home.personCount_other', { count: '2' }), { exact: true }),
  ).toBeVisible();

  // From the tree: the result recenters the tree.
  await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
  await page.getByRole('button', { name: t(language, 'tree:search') }).click();
  await page.getByRole('searchbox', { name: t(language, 'person:search.label') }).fill('eloïse');
  await page.getByRole('button', { name: /Éloïse Ngo/ }).click();
  await expect(page).toHaveURL(/\/tree\?focus=[0-9a-f-]{36}$/);
  await expect(page.getByRole('button', { name: /^Éloïse Ngo/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /^Alice/ })).toBeVisible();
});
