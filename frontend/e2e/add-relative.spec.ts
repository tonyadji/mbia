import { expect, test, type Page } from '@playwright/test';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-20 (phase-2-core-family-graph.md): from "me", add a mother, a father with a date warning
 * confirmed by the User, then a grandparent from the mother's profile (SCREEN-004, family-tree-ux.md
 * §9 and §9.1, person-relationships-collaboration.md §7.1). PR-21: the father's profile shows the
 * localized kinship label (localization-and-kinship-labels.md §3). PR-22: the profile Family
 * section lists parents and children with their badge (SCREEN-005, §3bis).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`add relatives (${language})`, () => {
    test.use({ locale });

    test('a User adds their parents and a grandparent, confirming a date warning', async ({
      page,
      request,
    }) => {
      const user = newUser();
      const familyName = `E2E ${user.email.slice(4, 12)}`;

      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();

      // "Start with me", born in 1990.
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await fillPerson(page, language, 'Alice', 1990);
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      await expect(page).toHaveURL(familyUrl);

      // My mother: nothing to confirm.
      await openRelativeChoice(page, language, 'family:home.relatives.MOTHER');
      await expect(
        page.getByRole('heading', {
          level: 1,
          name: t(language, 'person:relative.titleMe.MOTHER'),
        }),
      ).toBeVisible();
      await fillPerson(page, language, 'Awa');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page).toHaveURL(familyUrl);
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:relative.added', { name: 'Awa', anchor: 'Alice' }),
      );

      // My father, "born" after me: the warning is explained, then confirmed.
      await openRelativeChoice(page, language, 'family:home.relatives.FATHER');
      await fillPerson(page, language, 'Paul', 1995);
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      const warning = page.getByRole('alert');
      await expect(warning).toContainText(
        t(language, 'person:relative.warnings.PARENT_BORN_AFTER_CHILD', {
          parent: 'Paul',
          child: 'Alice',
          parentYear: '1995',
          childYear: '1990',
        }),
      );
      await warning
        .getByRole('button', { name: t(language, 'person:relative.warnings.confirm') })
        .click();
      await expect(page).toHaveURL(familyUrl);
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:relative.added', { name: 'Paul', anchor: 'Alice' }),
      );
      await expect(
        page.getByText(t(language, 'family:home.personCount_other', { count: '3' }), {
          exact: true,
        }),
      ).toBeVisible();

      // A grandparent, from the father's profile.
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul');
      // PR-21: the profile says what Paul is to the current User.
      await expect(
        page.getByText(t(language, 'person:kinship.label.FATHER'), { exact: true }),
      ).toBeVisible();
      const paulUrl = page.url();
      await page.getByRole('button', { name: t(language, 'person:relative.menu') }).click();
      await page
        .getByRole('link', { name: t(language, 'person:relative.choices.MOTHER'), exact: true })
        .click();
      await expect(
        page.getByRole('heading', {
          level: 1,
          name: t(language, 'person:relative.title.MOTHER', { name: 'Paul' }),
        }),
      ).toBeVisible();
      await fillPerson(page, language, 'Marie', 1940);
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page).toHaveURL(paulUrl);
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:relative.added', { name: 'Marie', anchor: 'Paul' }),
      );

      // PR-22: the Family section lists Paul's relatives, each with what they are to me.
      const paulParents = page.getByRole('list', {
        name: t(language, 'person:profile.relatives.parents'),
      });
      await expect(paulParents.getByRole('link')).toHaveCount(1);
      await expect(paulParents.getByRole('link')).toContainText('Marie');
      await expect(paulParents.getByRole('link')).toContainText(
        t(language, 'person:kinship.label.GRANDMOTHER'),
      );
      const paulChildren = page.getByRole('list', {
        name: t(language, 'person:profile.relatives.children'),
      });
      await expect(paulChildren.getByRole('link')).toContainText(
        t(language, 'person:kinship.label.SELF'),
      );

      // My own profile lists my two parents; the grandmother is one step further.
      await paulChildren.getByRole('link', { name: /Alice/ }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alice');
      const myParents = page.getByRole('list', {
        name: t(language, 'person:profile.relatives.parents'),
      });
      await expect(myParents.getByRole('link')).toHaveCount(2);
      await expect(myParents.getByRole('link', { name: /Awa/ })).toContainText(
        t(language, 'person:kinship.label.MOTHER'),
      );
      await expect(myParents.getByRole('link', { name: /Paul/ })).toContainText(
        t(language, 'person:kinship.label.FATHER'),
      );
      await expect(page.getByText('Marie')).toHaveCount(0);
    });
  });
}

async function openRelativeChoice(page: Page, language: Language, key: string) {
  await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
  await page.getByRole('link', { name: t(language, key) }).click();
}

async function fillPerson(page: Page, language: Language, firstName: string, birthYear?: number) {
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill(firstName);
  if (birthYear === undefined) return;
  await page.getByRole('button', { name: t(language, 'person:form.more') }).click();
  await page
    .getByLabel(t(language, 'person:form.birthPrecision'), { exact: true })
    .selectOption('YEAR_ONLY');
  await page
    .getByLabel(t(language, 'person:form.birthYear'), { exact: true })
    .fill(String(birthYear));
}
