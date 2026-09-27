import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/** A phone photo larger than 2560 px, with GPS location (Phase 3 plan §3.8). */
const LARGE_PHOTO = fileURLToPath(
  new URL('../../backend/src/test/resources/media/large-with-gps-3000x2000.jpg', import.meta.url),
);

/** A word with no break opportunity, as a long place name or a pasted link: it must wrap at 375 px. */
const LONG_WORD = 'Nkolbisson'.repeat(8);

/**
 * PR-39: the Phase 3 journey of phase-3-family-memories.md §1, end to end, at phone width, in
 * French and English. Every screen is checked for accessible controls (design-guidelines.md §9)
 * and for no sideways page scroll, long texts included. The photo is reduced in the browser, and
 * the images served back hold no location (mvp.md §23).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`Phase 3 journey (${language})`, () => {
    test.use({ locale });

    test('from a grandmother’s stories to her photo and a merged duplicate', async ({
      page,
      request,
    }) => {
      // Sign in; a Family with me, my mother and my grandmother.
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
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await firstName(page, language).fill('Awa');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page).toHaveURL(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa');
      await page.getByRole('button', { name: t(language, 'person:relative.menu') }).click();
      await page.getByRole('link', { name: t(language, 'person:relative.choices.MOTHER') }).click();
      await firstName(page, language).fill('Marie');
      await lastName(page, language).fill('Ngo');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page.getByRole('status').first()).toContainText('Marie Ngo');
      const awaUrl = page.url();

      // From my grandmother's profile: Add a memory, about her and my mother.
      await profiles(page, language)
        .getByRole('link', { name: /^Marie Ngo/ })
        .click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Marie Ngo');
      const marieUrl = page.url();
      await expectScreen(page);
      await page
        .getByRole('region', { name: t(language, 'memory:profile.title') })
        .getByRole('link', { name: t(language, 'memory:profile.add') })
        .click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:form.title'),
      );
      const persons = page.getByRole('list', { name: t(language, 'memory:form.persons') });
      await expect(
        persons.getByRole('button', {
          name: t(language, 'memory:form.removePerson', { name: 'Marie Ngo' }),
        }),
      ).toBeVisible();
      const story = stories[language];
      const text = `${story.paragraphs.join('\n\n')}\n${LONG_WORD}`;
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.title);
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(text);
      await page.getByRole('button', { name: t(language, 'memory:form.addPerson') }).click();
      const sheet = page.getByRole('dialog', { name: t(language, 'memory:form.chooseTitle') });
      await sheet
        .getByRole('searchbox', { name: t(language, 'memory:form.searchLabel') })
        .fill('awa');
      await expectScreen(page);
      await sheet.getByRole('button', { name: /Awa/ }).click();
      await expect(persons.getByRole('listitem')).toHaveCount(2);
      await expectScreen(page);

      // Publish, then read it in full (SCREEN-013), its title focused.
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'memory:published', { title: story.title }),
      );
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.title);
      await expect(page.getByRole('heading', { level: 1 })).toBeFocused();
      expect(await page.getByText(story.paragraphs[0] ?? '').textContent()).toBe(text);
      await expect(
        page
          .getByRole('list', { name: t(language, 'memory:screen.persons') })
          .getByRole('listitem'),
      ).toHaveCount(2);
      await expectScreen(page);

      // The story is on my grandmother's and my mother's profiles.
      for (const [url, name] of [
        [marieUrl, 'Marie Ngo'],
        [awaUrl, 'Awa'],
      ] as const) {
        await page.goto(url);
        await expect(page.getByRole('heading', { level: 1 })).toHaveText(name);
        await expect(memoryCards(page, language)).toHaveCount(1);
        await expect(memoryCards(page, language).first()).toContainText(story.title);
        await expectScreen(page);
      }
      await memoryCards(page, language).first().click();
      await expect(page).toHaveURL(memoryUrl);

      // Found in the Family "Memories" tab.
      await page.goto(familyUrl);
      await page
        .getByRole('navigation', { name: t(language, 'family:navigation.label') })
        .getByRole('link', { name: t(language, 'family:navigation.memories') })
        .click();
      const familyMemories = page.getByRole('list', { name: t(language, 'memory:family.title') });
      await expect(familyMemories.getByRole('listitem')).toHaveCount(1);
      await expectScreen(page);
      await familyMemories.getByRole('link', { name: new RegExp(escape(story.title)) }).click();
      await expect(page).toHaveURL(memoryUrl);

      // Edit the story (SCREEN-014).
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toBeFocused();
      await expectScreen(page);
      const edited = `${story.title} (${LONG_WORD})`;
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(edited);
      await page
        .getByLabel(t(language, 'memory:form.contentLabel'))
        .fill(`${text}\n${story.addition}`);
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();
      await expect(page).toHaveURL(memoryUrl);
      await expect(page.getByRole('status')).toContainText(
        t(language, 'memory:saved', { title: edited }),
      );
      await expect(page.getByText(story.addition, { exact: false })).toBeVisible();
      await expectScreen(page);

      // A second story, archived after a confirmation.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.addMemory') }).click();
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.second);
      await page
        .getByLabel(t(language, 'memory:form.contentLabel'))
        .fill(story.paragraphs[1] ?? '');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.second);
      await page.getByRole('button', { name: t(language, 'memory:screen.archive') }).click();
      const archive = page.getByRole('dialog');
      await expect(archive).toContainText(t(language, 'memory:archive.warning'));
      await expectScreen(page);
      await archive.getByRole('button', { name: t(language, 'memory:archive.confirm') }).click();
      await expect(page).toHaveURL(familyUrl);
      await page.goto(`${familyUrl}/memories`);
      await expect(familyMemories.getByRole('listitem')).toHaveCount(1);
      await expect(familyMemories).not.toContainText(story.second);

      // The Quick View shows my grandmother's Memory count.
      await openTreeOnAwa(page, language, familyUrl);
      await card(page, 'parent').filter({ hasText: 'Marie Ngo' }).click();
      const quickView = page.getByRole('dialog', { name: 'Marie Ngo' });
      await expect(quickView).toContainText(
        t(language, 'tree:quickView.memories_one', { count: '1' }),
      );
      await expectScreen(page);
      await page.keyboard.press('Escape');

      // A photo from my phone, large and with a location: reduced, with progress, then shown.
      await page.goto(marieUrl);
      await page.getByRole('link', { name: t(language, 'person:profile.edit') }).click();
      await expectScreen(page);
      await page.route('**/media/uploads/*/complete', async (route) => {
        await new Promise((resolve) => setTimeout(resolve, 500));
        await route.continue();
      });
      const upload = page.waitForRequest((sent) => sent.method() === 'PUT');
      const completed = page.waitForResponse(
        (response) => response.url().endsWith('/complete') && response.status() === 200,
      );
      await page.getByTestId('photo-input').setInputFiles(LARGE_PHOTO);
      await expect(page.getByRole('progressbar')).toBeVisible();
      const sent = jpegSize((await upload).postDataBuffer() ?? Buffer.alloc(0));
      expect(Math.max(sent.width, sent.height)).toBeLessThanOrEqual(2560);
      const asset = (await (await completed).json()) as { url: string; thumbnailUrl: string };
      await expect(page.getByRole('progressbar')).toBeHidden();
      await expectScreen(page);
      await page.getByRole('button', { name: t(language, 'person:edit.submit') }).click();
      await expect(page).toHaveURL(marieUrl);
      const photo = page.getByRole('img', { name: 'Marie Ngo' });
      await expectLoadedImage(photo);
      await expectScreen(page);
      // Neither the display version nor the thumbnail on screen keeps a location.
      const shown = await photo.getAttribute('src');
      for (const url of [asset.url, asset.thumbnailUrl, shown ?? '']) {
        await expectNoMetadata(request, url);
      }
      await openTreeOnAwa(page, language, familyUrl);
      await expectLoadedImage(card(page, 'parent').filter({ hasText: 'Marie Ngo' }).locator('img'));

      // A duplicate of my grandmother, with a story, merged into her: the story stays.
      await page.goto(familyUrl);
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page
        .getByRole('link', { name: t(language, 'family:home.relatives.someoneElse') })
        .click();
      await firstName(page, language).fill('marie');
      await lastName(page, language).fill('NGO');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await page
        .getByRole('alert', { name: t(language, 'person:duplicate.title_one') })
        .getByRole('button', { name: t(language, 'person:duplicate.createAnyway') })
        .click();
      await expect(page).toHaveURL(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('marie NGO');
      await page
        .getByRole('region', { name: t(language, 'memory:profile.title') })
        .getByRole('link', { name: t(language, 'memory:profile.add') })
        .click();
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.duplicate);
      await page
        .getByLabel(t(language, 'memory:form.contentLabel'))
        .fill(story.paragraphs[2] ?? '');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.duplicate);
      await page
        .getByRole('list', { name: t(language, 'memory:screen.persons') })
        .getByRole('link', { name: 'marie NGO' })
        .click();
      await page.getByRole('button', { name: t(language, 'person:merge.action') }).click();
      const merge = page.getByRole('dialog', {
        name: t(language, 'person:merge.title', { name: 'marie NGO' }),
      });
      await merge
        .getByRole('searchbox', { name: t(language, 'person:merge.search') })
        .fill('Marie');
      await merge
        .getByRole('list', { name: t(language, 'person:search.results') })
        .getByRole('button', { name: /^Marie Ngo/ })
        .click();
      await expectScreen(page);
      await merge.getByRole('button', { name: t(language, 'person:merge.confirm') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Marie Ngo');
      await expect(memoryCards(page, language)).toHaveCount(2);
      await expect(memoryCards(page, language).first()).toContainText(story.duplicate);
      await expect(memoryCards(page, language).last()).toContainText(story.title);
      await expectLoadedImage(page.getByRole('img', { name: 'Marie Ngo' }));
      await expectScreen(page);
    });
  });
}

const stories = {
  fr: {
    title: 'Le marché du samedi',
    paragraphs: [
      'Chaque samedi, grand-mère Marie vendait du plantain au marché de Mokolo.',
      'Maman l’accompagnait avant le lever du soleil, avec les paniers sur la tête.',
      'Au village, grand-mère puisait l’eau avant tout le monde.',
    ],
    addition: 'Elle chantait en rangeant les régimes.',
    second: 'Un brouillon à retirer',
    duplicate: 'Le puits du village',
  },
  en: {
    title: 'The Saturday market',
    paragraphs: [
      'Every Saturday, grandmother Marie sold plantains at the Mokolo market.',
      'Mum went with her before sunrise, carrying the baskets on her head.',
      'In the village, grandmother drew water before anyone else.',
    ],
    addition: 'She sang while stacking the bunches.',
    second: 'A draft to remove',
    duplicate: 'The village well',
  },
} satisfies Record<Language, Record<string, string | string[]>>;

/** The tree, recentred on my mother: my grandmother is on the parents row. */
async function openTreeOnAwa(page: Page, language: Language, familyUrl: string) {
  await page.goto(familyUrl);
  await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
  await expect(card(page, 'focus')).toContainText('Alice');
  await card(page, 'parent').filter({ hasText: 'Awa' }).click();
  await page
    .getByRole('dialog', { name: 'Awa' })
    .getByRole('button', { name: t(language, 'tree:quickView.center', { name: 'Awa' }) })
    .click();
  await expect(card(page, 'focus')).toContainText('Awa');
  await expectScreen(page);
}

/** The Family section of the current profile. */
function profiles(page: Page, language: Language) {
  return page.getByRole('list', { name: t(language, 'person:profile.relatives.parents') });
}

function memoryCards(page: Page, language: Language) {
  return page.getByRole('region', { name: t(language, 'memory:profile.title') }).getByRole('link', {
    name: new RegExp(`^(?!${escape(t(language, 'memory:profile.add'))}$)`),
  });
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

/** An image that is shown and actually loaded. */
async function expectLoadedImage(image: Locator) {
  await expect(image).toBeVisible();
  // Avatars load lazily, once on screen.
  await image.scrollIntoViewIfNeeded();
  await expect
    .poll(() =>
      image.evaluate((element) => {
        // The e2e tsconfig has no DOM types.
        const loaded = element as unknown as { complete: boolean; naturalWidth: number };
        return loaded.complete ? loaded.naturalWidth : 0;
      }),
    )
    .toBeGreaterThan(0);
}

/** The served image is a JPEG without EXIF (GPS included) nor XMP. */
async function expectNoMetadata(request: APIRequestContext, url: string) {
  const response = await request.get(url);
  expect(response.status()).toBe(200);
  const body = await response.body();
  expect(body.subarray(0, 3)).toEqual(Buffer.from([0xff, 0xd8, 0xff]));
  const raw = body.toString('latin1');
  expect(raw).not.toContain('Exif\u0000\u0000');
  expect(raw).not.toContain('http://ns.adobe.com/xap/1.0/');
  expect(raw).not.toContain('xmpmeta');
}

/** The dimensions in a JPEG's start-of-frame header. */
function jpegSize(data: Buffer) {
  let offset = 2;
  while (offset + 9 < data.length) {
    const marker = data[offset + 1] ?? 0;
    if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) {
      return { height: data.readUInt16BE(offset + 5), width: data.readUInt16BE(offset + 7) };
    }
    offset += 2 + data.readUInt16BE(offset + 2);
  }
  throw new Error('not a JPEG');
}

function escape(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
