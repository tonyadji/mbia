import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls, expectReadableScreen } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-62 (phase-6-family-story.md): a new Family starts with a memory (mvp.md §14). The empty Family
 * Home offers `Tell a first memory` and `Add a person` (SCREEN-002); the first memory asks who it is
 * about, and `Me` creates the User's own Person with it (SCREEN-006, OQ-065); the memory is in "Our
 * story". A second memory is about a grandmother not in the tree yet, added on the way. The tree
 * of a Family without Person offers `Start with me` (SCREEN-003).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`a Family starts with a memory (${language})`, () => {
    test.use({ locale });

    test('a new Family tells its first memory about me, then one about a grandmother', async ({
      page,
      request,
    }) => {
      const familyName = await createFamily(page, request, language);
      const familyUrl = page.url();
      const titles =
        language === 'fr'
          ? { first: 'Mon premier vélo', second: 'Les mangues de grand-mère' }
          : { first: 'My first bicycle', second: "Grandmother's mangoes" };

      // SCREEN-002, empty: the first gesture is a memory; building the tree stays possible.
      await expect(
        page.getByRole('heading', {
          level: 2,
          name: t(language, 'family:home.emptyTitle', { name: familyName }),
        }),
      ).toBeVisible();
      await expect(page.getByText(t(language, 'family:home.emptyBody'))).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'family:home.addPerson') }),
      ).toBeVisible();
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);

      // The empty tree has its own empty state, with `Start with me`.
      await page
        .getByRole('navigation', { name: t(language, 'family:navigation.label') })
        .getByRole('link', { name: t(language, 'family:navigation.tree') })
        .click();
      await expect(
        page.getByRole('heading', { name: t(language, 'tree:empty.title') }),
      ).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'tree:empty.startWithMe') }),
      ).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'tree:empty.addSomeoneElse') }),
      ).toBeVisible();
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);
      await page.goto(familyUrl);

      // SCREEN-006: "Who is this memory about?" → Me.
      await page.getByRole('link', { name: t(language, 'family:home.tellFirstMemory') }).click();
      const question = page.getByRole('group', {
        name: t(language, 'memory:form.subject.question'),
      });
      await expect(question).toBeVisible();
      await expect(
        page.getByRole('button', { name: t(language, 'memory:form.publish') }),
      ).toBeDisabled();
      await question
        .getByRole('button', { name: t(language, 'memory:form.subject.me'), exact: true })
        .click();
      await question
        .getByRole('textbox', { name: t(language, 'person:form.firstName') })
        .fill('Alice');
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);
      await writeStory(page, language, titles.first, '1998');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(titles.first);

      // The memory is in the family story; Family Home now leads with `Tell a memory`.
      await page.goto(familyUrl);
      const story = page.getByRole('region', { name: t(language, 'memory:story.title') });
      await expect(
        story.getByRole('link', {
          name: t(language, 'memory:story.entry_one', { year: '1998', count: '1' }),
        }),
      ).toBeVisible();
      await expect(
        page.getByText(t(language, 'family:home.personCount_one', { count: '1' }), {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'family:home.viewTree') }),
      ).toBeVisible();
      await expect(
        page.getByRole('button', { name: t(language, 'family:home.addRelative') }),
      ).toBeVisible();
      await expectAccessibleControls(page);
      await expectReadableScreen(page, language);

      // A memory about a grandmother not in the tree yet: `Add {typed name}`.
      await story.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      await expect(
        page.getByRole('group', { name: t(language, 'memory:form.subject.question') }),
      ).toHaveCount(0);
      await writeStory(page, language, titles.second, '1975');
      await page.getByRole('button', { name: t(language, 'memory:form.addPerson') }).click();
      const sheet = page.getByRole('dialog', { name: t(language, 'memory:form.chooseTitle') });
      await sheet
        .getByRole('searchbox', { name: t(language, 'memory:form.searchLabel') })
        .fill('Rose Mbida');
      await sheet
        .getByRole('button', { name: t(language, 'memory:form.addNew', { name: 'Rose Mbida' }) })
        .click();
      const naming = page.getByRole('dialog', { name: t(language, 'memory:form.newTitle') });
      await expect(
        naming.getByRole('textbox', { name: t(language, 'person:form.lastName'), exact: true }),
      ).toHaveValue('Mbida');
      await expectAccessibleControls(page);
      await naming.getByRole('button', { name: t(language, 'memory:form.addThisPerson') }).click();
      await expect(page.getByText(t(language, 'memory:form.newPerson'))).toBeVisible();
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      await expect(page.getByRole('link', { name: /Rose Mbida/ })).toBeVisible();

      await page.goto(familyUrl);
      await expect(
        page.getByText(t(language, 'family:home.personCount_other', { count: '2' }), {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        story.getByRole('link', {
          name: t(language, 'memory:story.entry_one', { year: '1975', count: '1' }),
        }),
      ).toBeVisible();
    });
  });
}

/** Title, text and year of a story on SCREEN-006. */
async function writeStory(page: Page, language: Language, title: string, year: string) {
  await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
  await page
    .getByLabel(t(language, 'memory:form.contentLabel'))
    .fill(language === 'fr' ? 'Toute la famille était là.' : 'The whole family was there.');
  await page
    .getByRole('combobox', { name: t(language, 'memory:form.happenedAt.label') })
    .selectOption({ label: t(language, 'person:form.precision.YEAR_ONLY') });
  await page.getByRole('textbox', { name: t(language, 'memory:form.happenedAt.year') }).fill(year);
}

/** A new User creates a Family; returns its name, on its Family Home. */
async function createFamily(
  page: Page,
  request: Parameters<typeof registerAndVerify>[1],
  language: Language,
) {
  const user = newUser();
  const familyName = `E2E ${user.email.slice(4, 12)}`;
  await page.goto('/');
  await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
  await registerAndVerify(page, request, user);
  await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
  await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
  await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
  return familyName;
}
