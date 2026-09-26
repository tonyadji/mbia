import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/**
 * PR-30, PR-31, PR-32 (phase-3-family-memories.md): from Family Home, a User writes a long story
 * about themselves and their mother (SCREEN-006). Their own Person is preselected, the mother is
 * found with the Family search (SCREEN-007), and the story is published; it is then read on the
 * Memory screen (SCREEN-013), found on the mother's profile (SCREEN-005) and in the Family
 * `Memories` tab (SCREEN-015), and counted on Family Home (SCREEN-002). Runs at phone width.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`add a Memory (${language})`, () => {
    test.use({ locale });

    test('a User writes a story about themselves and their mother', async ({ page, request }) => {
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

      // Me, then my mother.
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await firstName(page, language).fill('Éloïse');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page).toHaveURL(familyUrl);

      // The story form opens directly, with me preselected.
      await page.getByRole('link', { name: t(language, 'family:home.addMemory') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:form.title'),
      );
      const persons = page.getByRole('list', { name: t(language, 'memory:form.persons') });
      await expect(
        persons.getByRole('button', {
          name: t(language, 'memory:form.removePerson', { name: 'Alice' }),
        }),
      ).toBeVisible();

      const title = language === 'fr' ? 'Le marché du samedi' : 'The Saturday market';
      const paragraph =
        language === 'fr'
          ? 'Chaque samedi, maman nous emmenait au marché avant le lever du soleil.'
          : 'Every Saturday, mum took us to the market before sunrise.';
      const story = Array.from({ length: 12 }, () => paragraph).join('\n\n');
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(story);

      // My mother, found with the Family search.
      await page.getByRole('button', { name: t(language, 'memory:form.addPerson') }).click();
      const sheet = page.getByRole('dialog', { name: t(language, 'memory:form.chooseTitle') });
      await sheet
        .getByRole('searchbox', { name: t(language, 'memory:form.searchLabel') })
        .fill('elo');
      await sheet.getByRole('button', { name: /Éloïse/ }).click();
      await expect(sheet).toBeHidden();
      await expect(persons.getByRole('listitem')).toHaveCount(2);

      await expectAccessibleControls(page);
      await expectNoPageScroll(page);

      const created = page.waitForResponse(
        (response) => response.url().endsWith('/memories/stories') && response.status() === 201,
      );
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      const memory = (await (await created).json()) as {
        title: string;
        content: string;
        relatedPersons: { displayName: string }[];
      };
      expect(memory.title).toBe(title);
      expect(memory.content).toBe(story);
      expect(memory.relatedPersons.map((person) => person.displayName).sort()).toEqual([
        'Alice',
        'Éloïse',
      ]);

      // PR-31: the User lands on the Memory (SCREEN-013), its text shown as typed.
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'memory:published', { title }),
      );
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
      expect(await page.getByText(paragraph).first().textContent()).toBe(story);
      const related = page.getByRole('list', { name: t(language, 'memory:screen.persons') });
      await expect(related.getByRole('listitem')).toHaveCount(2);
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);

      // The story is on my mother's profile, first section; its card opens it again.
      await related.getByRole('link', { name: 'Éloïse' }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Éloïse');
      await expect(page.getByRole('heading', { level: 2 }).first()).toHaveText(
        t(language, 'memory:profile.title'),
      );
      const memories = page.getByRole('region', { name: t(language, 'memory:profile.title') });
      await expectNoPageScroll(page);
      await memories.getByRole('link', { name: new RegExp(title) }).click();
      await expect(page).toHaveURL(memoryUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);

      // PR-32: Family Home counts it, and the Memories tab lists it.
      await page.goto(familyUrl);
      await expect(
        page.getByText(t(language, 'family:home.memoryCount_one', { count: '1' }), { exact: true }),
      ).toBeVisible();
      const navigation = page.getByRole('navigation', {
        name: t(language, 'family:navigation.label'),
      });
      await navigation
        .getByRole('link', { name: t(language, 'family:navigation.memories') })
        .click();
      await expect(page).toHaveURL(`${familyUrl}/memories`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:family.title'),
      );
      const familyMemories = page.getByRole('list', { name: t(language, 'memory:family.title') });
      await expect(familyMemories.getByRole('listitem')).toHaveCount(1);
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);
      await familyMemories.getByRole('link', { name: new RegExp(title) }).click();
      await expect(page).toHaveURL(memoryUrl);
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
