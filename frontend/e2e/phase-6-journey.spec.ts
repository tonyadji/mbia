import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls, expectReadableScreen } from './support/accessibility';
import { t, type Language } from './support/i18n';
import {
  newUser,
  openRegistration,
  registerAndVerify,
  registerAndVerifyInAnotherTab,
} from './support/keycloak';
import {
  addPersonOnTheWay,
  expectNoPageScroll,
  publishStory,
  setStoryDate,
  stripEntry,
  writeStory,
  yearStrip,
} from './support/story';

/** A PNG photo of the Phase 3 fixtures (phase-3-family-memories.md §3.8). */
const PHOTO = fileURLToPath(
  new URL('../../backend/src/test/resources/media/gps.png', import.meta.url),
);
const PHONE = { width: 375, height: 812 };
const LINK = /^https?:\/\/\S+\/invitations\/[\w-]+$/;

/**
 * PR-63: the Phase 6 journey of phase-6-family-story.md §1, end to end, at phone width, in French
 * and English. A new Family tells a first memory about me, with its year and a photo; Family Home
 * opens on "Our story"; memories about Marie (12 March 1962) and Awa (undated) add them on the way;
 * the strip leads from year to year; Awa, invited by a link, recognises herself and tells a memory
 * of 1980; the ADMIN moves a memory from 1975 to 1962, links Marie, Awa and me, and views the tree;
 * a future date explains itself. SCREEN-002, SCREEN-006 and SCREEN-016 are checked against
 * design-guidelines.md §9 in each of their states (PR-63 accessibility pass).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`Phase 6 journey (${language})`, () => {
    test.use({ locale, viewport: PHONE });

    test('from a first memory to the tree, as the index of the story', async ({
      page,
      browser,
      request,
      baseURL,
    }) => {
      test.setTimeout(240_000);
      const story = stories[language];
      const storyRegion = (target: Page) =>
        target.getByRole('region', { name: t(language, 'memory:story.title') });
      const tellMemory = (target: Page) =>
        storyRegion(target).getByRole('link', { name: t(language, 'memory:story.tell') });

      // Sign up and create a Family: Family Home starts with a memory (SCREEN-002, empty).
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, newUser());
      const familyName = `E2E ${language} phase 6`;
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();
      await expect(
        page.getByRole('heading', {
          level: 2,
          name: t(language, 'family:home.emptyTitle', { name: familyName }),
        }),
      ).toBeVisible();
      await expectScreen(page, language);

      // "Tell a first memory" → "Who is this memory about?" → Me (SCREEN-006).
      await page.getByRole('link', { name: t(language, 'family:home.tellFirstMemory') }).click();
      const subject = page.getByRole('group', {
        name: t(language, 'memory:form.subject.question'),
      });
      await expectScreen(page, language);
      await subject
        .getByRole('button', { name: t(language, 'memory:form.subject.me'), exact: true })
        .click();
      await subject
        .getByRole('textbox', { name: t(language, 'person:form.firstName') })
        .fill('Alice');
      await writeStory(page, language, { ...story.first, date: { year: '1975' } });
      await page.getByTestId('memory-photo-input').setInputFiles(PHOTO);
      await expect(
        page.getByRole('img', {
          name: t(language, 'memory:form.photos.thumbnail', { position: '1' }),
        }),
      ).toBeVisible({ timeout: 15_000 });
      await expectScreen(page, language);
      const firstMemoryUrl = await publishStory(page, language);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.first.title);
      await expect(
        page.getByText(t(language, 'memory:screen.happenedIn', { year: '1975' }), { exact: true }),
      ).toBeVisible();

      // Family Home opens on "Our story": 1975 · 1 memory.
      await page.goto(familyUrl);
      await expect(yearStrip(page, language).getByRole('link')).toHaveText([
        stripEntry(language, '1975', 1),
      ]);
      await expectScreen(page, language);

      // A memory about Marie, not in the tree yet, added on the way, on 12 March 1962.
      await tellMemory(page).click();
      await writeStory(page, language, { ...story.marie, date: { exact: '1962-03-12' } });
      await page
        .getByRole('button', { name: t(language, 'memory:form.removePerson', { name: 'Alice' }) })
        .click();
      await addPersonOnTheWay(page, language, 'Marie Ngo');
      await expect(page.getByText(t(language, 'memory:form.newPerson'))).toBeVisible();
      await expectScreen(page, language);
      await publishStory(page, language);
      await expect(page.getByRole('link', { name: /Marie Ngo/ })).toBeVisible();

      // An undated memory, about Awa, Marie's daughter, added on the way too.
      await page.goto(familyUrl);
      await tellMemory(page).click();
      await writeStory(page, language, story.undated);
      await addPersonOnTheWay(page, language, 'Awa Ngo');
      await publishStory(page, language);
      await page.getByRole('link', { name: /Awa Ngo/ }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa Ngo');
      const awaUrl = page.url();

      // The strip: 1962, 1975, the undated memories; 1962 opens "What happened in 1962".
      await page.goto(familyUrl);
      const entries = yearStrip(page, language).getByRole('link');
      await expect(entries).toHaveText([
        stripEntry(language, '1962', 1),
        stripEntry(language, '1975', 1),
        stripEntry(language, 'undated', 1),
      ]);
      await expectNoPageScroll(page);
      await expectScreen(page, language);
      await entries.first().click();
      await expect(page).toHaveURL(`${familyUrl}/story/1962`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:story.yearTitle', { year: '1962' }),
      );
      await expect(page.getByRole('link', { name: new RegExp(story.marie.title) })).toBeVisible();
      await expectCurrentEntry(page, language, stripEntry(language, '1962', 1));
      await expectNoPageScroll(page);
      await expectScreen(page, language);

      // To 1975 and to the undated memories, without going back.
      const next = page.getByRole('button', { name: t(language, 'memory:story.next') });
      await next.click();
      await expect(page).toHaveURL(`${familyUrl}/story/1975`);
      await expect(page.getByRole('link', { name: new RegExp(story.first.title) })).toBeVisible();
      await expectCurrentEntry(page, language, stripEntry(language, '1975', 1));
      await next.click();
      await expect(page).toHaveURL(`${familyUrl}/story/undated`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:story.undated'),
      );
      await expect(page.getByRole('link', { name: new RegExp(story.undated.title) })).toBeVisible();
      await expectCurrentEntry(page, language, stripEntry(language, 'undated', 1));
      await expect(next).toBeDisabled();
      await expectNoPageScroll(page);
      await expectScreen(page, language);

      // Awa is invited by a link from her profile; signed out, she joins and recognises herself.
      await page.goto(awaUrl);
      await page
        .getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) })
        .click();
      await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
      const invitation = String(await page.getByText(LINK).textContent());
      const awaContext = await browser.newContext({ locale, viewport: PHONE, baseURL });
      const awaSignedOut = await awaContext.newPage();
      await awaSignedOut.goto(invitation);
      await awaSignedOut
        .getByRole('button', { name: t(language, 'invitation:join.submit') })
        .click();
      await openRegistration(awaSignedOut);
      const awa = await registerAndVerifyInAnotherTab(awaSignedOut, request, newUser());
      await expect(awa.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:onboarding.areYou', { name: 'Awa Ngo' }),
      );
      await awa.getByRole('button', { name: t(language, 'invitation:onboarding.yes') }).click();
      await expect(
        awa.getByRole('region', {
          name: t(language, 'family:home.welcome.title', { name: familyName }),
        }),
      ).toBeVisible();
      await expectScreen(awa, language);

      // Awa tells a memory of 1980: it appears in the strip for the ADMIN.
      await tellMemory(awa).click();
      await writeStory(awa, language, { ...story.awa, date: { year: '1980' } });
      await expectScreen(awa, language);
      await publishStory(awa, language);
      await awaContext.close();
      await page.goto(familyUrl);
      await expect(entries).toHaveText([
        stripEntry(language, '1962', 1),
        stripEntry(language, '1975', 1),
        stripEntry(language, '1980', 1),
        stripEntry(language, 'undated', 1),
      ]);

      // The ADMIN moves the first memory from 1975 to 1962 (SCREEN-014): the strip follows, and
      // 1975, left without memory, explains it.
      await page.goto(firstMemoryUrl);
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await setStoryDate(page, language, { year: '1962' });
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();
      await expect(page).toHaveURL(firstMemoryUrl);
      await page.goto(familyUrl);
      await expect(entries).toHaveText([
        stripEntry(language, '1962', 2),
        stripEntry(language, '1980', 1),
        stripEntry(language, 'undated', 1),
      ]);
      await page.goto(`${familyUrl}/story/1975`);
      await expect(
        page.getByText(t(language, 'memory:story.emptyYear', { year: '1975' })),
      ).toBeVisible();
      await expectScreen(page, language);
      await page.goto(`${familyUrl}/story/1962`);
      // Exact dates first, then the years only (OQ-064).
      await expect(
        page.getByRole('link', {
          name: new RegExp(`${story.marie.title}|${story.first.title}`),
        }),
      ).toHaveText([new RegExp(story.marie.title), new RegExp(story.first.title)]);
      await expectScreen(page, language);

      // The ADMIN adds the relationships (Marie mother of Awa, Awa mother of me) and views the tree.
      await page.goto(awaUrl);
      await page.getByRole('button', { name: t(language, 'person:relative.menu') }).click();
      await page.getByRole('link', { name: t(language, 'person:relative.choices.MOTHER') }).click();
      await linkExisting(page, language, 'Marie Ngo', 'Awa Ngo');
      await page.goto(familyUrl);
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await linkExisting(page, language, 'Awa Ngo', 'Alice');
      await expect(page).toHaveURL(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expect(page.locator('[data-role="focus"]').getByRole('button')).toContainText('Alice');
      await expect(page.locator('[data-role="parent"]').getByRole('button')).toContainText([
        'Awa Ngo',
      ]);
      await expectScreen(page, language);

      // A future date is refused and explains itself.
      await page.goto(familyUrl);
      await tellMemory(page).click();
      await writeStory(page, language, {
        ...story.future,
        date: { year: String(new Date().getFullYear() + 1) },
      });
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      const year = page.getByRole('textbox', { name: t(language, 'memory:form.happenedAt.year') });
      await expect(year).toHaveAccessibleDescription(t(language, 'memory:form.happenedAt.future'));
      await expect(page).toHaveURL(/\/memories\/new$/);
      await expectScreen(page, language);
    });
  });
}

const stories = {
  fr: {
    first: { title: 'Mon premier vélo', text: 'Papa me tenait par la selle.' },
    marie: { title: 'Le mariage de Marie', text: 'Tout le quartier était là.' },
    undated: { title: 'Les mangues d’Awa', text: 'Awa grimpait au manguier.' },
    awa: { title: 'La maison de Douala', text: 'Nous y avons vécu dix ans.' },
    future: { title: 'Un souvenir à venir', text: 'Pas encore arrivé.' },
  },
  en: {
    first: { title: 'My first bicycle', text: 'Dad held the saddle.' },
    marie: { title: "Marie's wedding", text: 'The whole neighbourhood was there.' },
    undated: { title: "Awa's mangoes", text: 'Awa climbed the mango tree.' },
    awa: { title: 'The house in Douala', text: 'We lived there for ten years.' },
    future: { title: 'A memory to come', text: 'Not yet happened.' },
  },
} satisfies Record<Language, Record<string, { title: string; text: string }>>;

/** Every visible control and text of the current screen meets design-guidelines.md §9. */
async function expectScreen(page: Page, language: Language) {
  await expectAccessibleControls(page);
  await expectReadableScreen(page, language);
}

/**
 * The current entry of the strip (SCREEN-016) is marked by more than its color
 * (design-guidelines.md §9): `aria-current`, bold and underlined.
 */
async function expectCurrentEntry(page: Page, language: Language, label: string) {
  const current = yearStrip(page, language).locator('[aria-current="page"]');
  await expect(current).toHaveText(label);
  await expect(current).toHaveCSS('font-weight', '700');
  await expect(current).toHaveCSS('text-decoration-line', 'underline');
}

/** On the add-relative screen, links a Person already in the Family (SCREEN-004). */
async function linkExisting(page: Page, language: Language, name: string, anchor: string) {
  await page
    .getByRole('searchbox', { name: t(language, 'person:relative.existing.search') })
    .fill(name.split(' ')[0] ?? name);
  await page.getByRole('button', { name: new RegExp(`^${name}`) }).click();
  await page
    .getByRole('button', { name: t(language, 'person:relative.existing.link', { name, anchor }) })
    .click();
  await expect(page.getByRole('status').first()).toContainText(
    t(language, 'person:relative.linked', { name, anchor }),
  );
}
