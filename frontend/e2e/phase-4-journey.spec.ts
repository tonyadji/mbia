import { fileURLToPath } from 'node:url';
import {
  expect,
  test,
  type APIRequestContext,
  type Locator,
  type Page,
  type Response,
} from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/** Image fixtures of Phase 3 (phase-3-family-memories.md §3.8), reused (Phase 4 plan §3.6). */
const fixture = (name: string) =>
  fileURLToPath(new URL(`../../backend/src/test/resources/media/${name}`, import.meta.url));
/** A phone photo larger than 2560 px, with GPS location. */
const LARGE_PHOTO = fixture('large-with-gps-3000x2000.jpg');
/** A phone photo turned by its EXIF orientation, with GPS location. */
const ROTATED_PHOTO = fixture('exif-gps-orientation-6.jpg');
/** A PNG with GPS location. */
const PNG_PHOTO = fixture('gps.png');

/** A word with no break opportunity, as a long place name: it must wrap at 375 px. */
const LONG_WORD = 'Nkolbisson'.repeat(8);

interface MemoryPhoto {
  url: string;
  thumbnailUrl: string;
  caption: string | null;
}

/**
 * PR-46: the Phase 4 journey of phase-4-memory-photos.md §1, end to end, at phone width, in French
 * and English. A story about my grandmother gets two photos from my phone (one large, with a
 * location), a caption and a year, then a third photo and a removal; a Memory is written with a
 * title and a photo only; a duplicate of my grandmother with a photo story is merged into her and
 * the photos stay. Every screen is checked for accessible controls (design-guidelines.md §9) and
 * for no sideways page scroll, long caption included; every photo served holds no location
 * (mvp.md §23).
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`Phase 4 journey (${language})`, () => {
    test.use({ locale });

    test('from a grandmother’s story with photos to a merged duplicate', async ({
      page,
      request,
    }) => {
      test.setTimeout(120_000);
      const story = stories[language];
      const served: MemoryPhoto[] = [];
      const photoLabel = (key: string, position: number) =>
        t(language, `memory:form.photos.${key}`, { position: String(position) });
      const alt = (n: number, total: number) =>
        t(language, 'memory:screen.photos.alt', { n: String(n), total: String(total) });

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
      const awaUrl = page.url();
      await page.getByRole('button', { name: t(language, 'person:relative.menu') }).click();
      await page.getByRole('link', { name: t(language, 'person:relative.choices.MOTHER') }).click();
      await firstName(page, language).fill('Marie');
      await lastName(page, language).fill('Ngo');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page.getByRole('status').first()).toContainText('Marie Ngo');

      // From my grandmother's profile: Add a memory; a title and a short story.
      await page
        .getByRole('list', { name: t(language, 'person:profile.relatives.parents') })
        .getByRole('link', { name: /^Marie Ngo/ })
        .click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Marie Ngo');
      const marieUrl = page.url();
      await page
        .getByRole('region', { name: t(language, 'memory:profile.title') })
        .getByRole('link', { name: t(language, 'memory:profile.add') })
        .click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'memory:form.title'),
      );
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.title);
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(story.text);
      await expectScreen(page);

      // Two photos from my phone, one large with a location: each shows its progress, the large
      // one is sent reduced, and Publish waits for both.
      await page.route('**/media/uploads/*/complete', async (route) => {
        await new Promise((resolve) => setTimeout(resolve, 500));
        await route.continue();
      });
      const puts: Buffer[] = [];
      page.on('request', (sent) => {
        if (sent.method() === 'PUT') puts.push(sent.postDataBuffer() ?? Buffer.alloc(0));
      });
      const publish = page.getByRole('button', { name: t(language, 'memory:form.publish') });
      await page.getByTestId('memory-photo-input').setInputFiles([LARGE_PHOTO, PNG_PHOTO]);
      await expect(page.getByRole('progressbar').first()).toBeVisible();
      await expect(publish).toBeDisabled();
      for (const position of [1, 2]) {
        await expect(
          page.getByRole('img', { name: photoLabel('thumbnail', position) }),
        ).toBeVisible({ timeout: 15_000 });
      }
      await expect(page.getByRole('progressbar')).toHaveCount(0);
      await expect(publish).toBeEnabled();
      expect(puts).toHaveLength(2);
      expect(puts.map(jpegSize)).toContainEqual({ width: 2560, height: 1707 });
      await page.unroute('**/media/uploads/*/complete');

      // A long caption on the first photo, a year on the second.
      const caption = `${story.caption} ${LONG_WORD}`;
      await page.getByLabel(photoLabel('caption', 1)).fill(caption);
      await page.getByRole('button', { name: photoLabel('moreInfoLabel', 2) }).click();
      const second = page.getByRole('group', { name: photoLabel('photo', 2) });
      await second.getByLabel(t(language, 'memory:form.photos.takenAt')).selectOption('YEAR_ONLY');
      await second.getByLabel(t(language, 'memory:form.photos.takenYear')).fill('1975');
      await expectScreen(page);

      // Link my mother; publish.
      await page.getByRole('button', { name: t(language, 'memory:form.addPerson') }).click();
      const sheet = page.getByRole('dialog', { name: t(language, 'memory:form.chooseTitle') });
      await sheet
        .getByRole('searchbox', { name: t(language, 'memory:form.searchLabel') })
        .fill('awa');
      await sheet.getByRole('button', { name: /Awa/ }).click();
      await expect(
        page.getByRole('list', { name: t(language, 'memory:form.persons') }).getByRole('listitem'),
      ).toHaveCount(2);
      const created = memoryResponse(page, 'POST');
      await publish.click();
      served.push(...(await photosOf(created)));
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();

      // The Memory: its title, both photos, then the story.
      const heading = page.getByRole('heading', { level: 1 });
      await expect(heading).toHaveText(story.title);
      const photos = page.getByRole('list', { name: t(language, 'memory:screen.photos.label') });
      const thumbnails = photos.getByRole('button');
      await expect(thumbnails).toHaveCount(2);
      await expect(thumbnails.nth(0)).toHaveAccessibleName(caption);
      await expect(thumbnails.nth(1)).toHaveAccessibleName(alt(2, 2));
      for (const index of [0, 1]) await expectLoadedImage(thumbnails.nth(index).locator('img'));
      await expectBefore(heading, photos);
      await expectBefore(photos, page.getByText(story.text));
      await expectScreen(page);

      // Each photo with its caption and year, in the viewer.
      await thumbnails.nth(0).click();
      const viewer = page.getByRole('dialog');
      await expect(viewer).toHaveAccessibleName(alt(1, 2));
      await expectLoadedImage(viewer.getByRole('img', { name: caption }));
      await expect(viewer.getByText(caption)).toBeVisible();
      await expectScreen(page);
      await viewer.getByRole('button', { name: t(language, 'memory:screen.photos.next') }).click();
      await expect(viewer).toHaveAccessibleName(alt(2, 2));
      await expect(
        viewer.getByText(t(language, 'memory:screen.photos.takenAt', { date: '1975' })),
      ).toBeVisible();
      await expectScreen(page);
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await expect(thumbnails.nth(0)).toBeFocused();
      served.push(...(await shownPhotos(page)));

      // Its card shows the first photo on both profiles and in the Family "Memories" tab.
      for (const [url, name] of [
        [marieUrl, 'Marie Ngo'],
        [awaUrl, 'Awa'],
      ] as const) {
        await page.goto(url);
        await expect(page.getByRole('heading', { level: 1 })).toHaveText(name);
        await expect(memoryCards(page, language)).toHaveCount(1);
        await expectThumbnail(memoryCards(page, language).first(), story.title);
        await expectScreen(page);
      }
      await openFamilyMemories(page, language, familyUrl);
      await expectThumbnail(familyMemoryCard(page, language, story.title), story.title);
      await expectScreen(page);

      // Edit it: a third photo reaches the limit and disables Add a photo; remove the second.
      await page.goto(memoryUrl);
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toBeFocused();
      await expect(page.getByLabel(photoLabel('caption', 1))).toHaveValue(caption);
      const addPhoto = page.getByRole('button', { name: t(language, 'memory:form.photos.add') });
      await page.getByTestId('memory-photo-input').setInputFiles(ROTATED_PHOTO);
      await expect(page.getByRole('img', { name: photoLabel('thumbnail', 3) })).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByRole('group', { name: photoLabel('photo', 3) })).toBeFocused();
      await expect(addPhoto).toBeDisabled();
      await expect(addPhoto).toHaveAccessibleDescription(
        t(language, 'memory:form.photos.limit_other', { count: '3' }),
      );
      await expectScreen(page);
      await page.getByRole('button', { name: photoLabel('removeLabel', 2) }).click();
      // The third photo takes the place of the second, and the focus.
      await expect(page.getByRole('group', { name: photoLabel('photo', 2) })).toBeFocused();
      await expect(addPhoto).toBeEnabled();
      await expectScreen(page);
      const updated = memoryResponse(page, 'PATCH');
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();
      served.push(...(await photosOf(updated)));
      await expect(page).toHaveURL(memoryUrl);
      await expect(thumbnails).toHaveCount(2);
      await expect(thumbnails.nth(0)).toHaveAccessibleName(caption);
      await expect(thumbnails.nth(1)).toHaveAccessibleName(alt(2, 2));
      await expectLoadedImage(thumbnails.nth(1).locator('img'));
      await expectScreen(page);
      served.push(...(await shownPhotos(page)));

      // A Memory with a title and a photo only, without text: its card shows its title only.
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.addMemory') }).click();
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.textless);
      await page.getByTestId('memory-photo-input').setInputFiles(PNG_PHOTO);
      await expect(page.getByRole('img', { name: photoLabel('thumbnail', 1) })).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByText(t(language, 'memory:form.contentOptional'))).toBeVisible();
      await expectScreen(page);
      const textless = memoryResponse(page, 'POST');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      served.push(...(await photosOf(textless)));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.textless);
      await expect(thumbnails).toHaveCount(1);
      await expectScreen(page);
      await openFamilyMemories(page, language, familyUrl);
      const textlessCard = familyMemoryCard(page, language, story.textless);
      await expectThumbnail(textlessCard, story.textless);
      await expect(textlessCard).toHaveText(story.textless);

      // A duplicate of my grandmother, with a Memory with a photo, merged into her: the photos stay.
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
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(story.duplicateText);
      await page.getByTestId('memory-photo-input').setInputFiles(LARGE_PHOTO);
      await expect(page.getByRole('img', { name: photoLabel('thumbnail', 1) })).toBeVisible({
        timeout: 15_000,
      });
      const duplicate = memoryResponse(page, 'POST');
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      served.push(...(await photosOf(duplicate)));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.duplicate);
      const duplicateUrl = page.url();
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
      await expectThumbnail(memoryCards(page, language).first(), story.duplicate);
      await expectThumbnail(memoryCards(page, language).last(), story.title);
      await expectScreen(page);
      for (const [url, count] of [
        [duplicateUrl, 1],
        [memoryUrl, 2],
      ] as const) {
        await page.goto(url);
        await expect(thumbnails).toHaveCount(count);
        for (let index = 0; index < count; index++) {
          await expectLoadedImage(thumbnails.nth(index).locator('img'));
        }
        served.push(...(await shownPhotos(page)));
      }

      // No served photo contains location data: every display and thumbnail URL returned, and
      // every image shown.
      const urls = new Set(served.flatMap((photo) => [photo.url, photo.thumbnailUrl]));
      expect(urls.size).toBeGreaterThanOrEqual(12);
      for (const url of urls) await expectNoMetadata(request, url);
    });
  });
}

const stories = {
  fr: {
    title: 'Le marché du samedi',
    text: 'Chaque samedi, grand-mère Marie vendait du plantain au marché de Mokolo.',
    caption: 'Grand-mère et maman au marché de',
    textless: 'Grand-mère en 1975',
    duplicate: 'Le puits du village',
    duplicateText: 'Au village, grand-mère puisait l’eau avant tout le monde.',
  },
  en: {
    title: 'The Saturday market',
    text: 'Every Saturday, grandmother Marie sold plantains at the Mokolo market.',
    caption: 'Grandmother and mum at the market of',
    textless: 'Grandmother in 1975',
    duplicate: 'The village well',
    duplicateText: 'In the village, grandmother drew water before anyone else.',
  },
} satisfies Record<Language, Record<string, string>>;

/** The next response of createStoryMemory (POST) or updateMemory (PATCH). */
function memoryResponse(page: Page, method: 'POST' | 'PATCH') {
  return page.waitForResponse(
    (response) =>
      response.request().method() === method &&
      /\/memories\/(stories|[0-9a-f-]{36})$/.test(new URL(response.url()).pathname) &&
      response.ok(),
  );
}

async function photosOf(response: Promise<Response>) {
  const body = (await (await response).json()) as { photos: MemoryPhoto[] };
  expect(body.photos.length).toBeGreaterThan(0);
  return body.photos;
}

/** The images of the Memory on screen, as served photos: the thumbnails of its grid. */
async function shownPhotos(page: Page): Promise<MemoryPhoto[]> {
  const sources: string[] = [];
  for (const image of await page.locator('main img').all()) {
    sources.push((await image.getAttribute('src')) ?? '');
  }
  return sources
    .filter((source) => source.startsWith('http'))
    .map((source) => ({ url: source, thumbnailUrl: source, caption: null }));
}

async function openFamilyMemories(page: Page, language: Language, familyUrl: string) {
  await page.goto(familyUrl);
  await page
    .getByRole('navigation', { name: t(language, 'family:navigation.label') })
    .getByRole('link', { name: t(language, 'family:navigation.memories') })
    .click();
}

function familyMemoryCard(page: Page, language: Language, title: string) {
  return page
    .getByRole('list', { name: t(language, 'memory:family.title') })
    .getByRole('link', { name: new RegExp(`^${escape(title)}`) });
}

function memoryCards(page: Page, language: Language) {
  return page.getByRole('region', { name: t(language, 'memory:profile.title') }).getByRole('link', {
    name: new RegExp(`^(?!${escape(t(language, 'memory:profile.add'))}$)`),
  });
}

/** A card named by its title, with the first photo's thumbnail, loaded and decorative. */
async function expectThumbnail(card: Locator, title: string) {
  await expect(card).toHaveAccessibleName(new RegExp(`^${escape(title)}`));
  const thumbnail = card.locator('img');
  await expectLoadedImage(thumbnail);
  await expect(thumbnail).toHaveAttribute('alt', '');
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
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

/** An image that is shown and actually loaded (images load lazily, once on screen). */
async function expectLoadedImage(image: Locator) {
  await image.scrollIntoViewIfNeeded();
  await expect(image).toBeVisible();
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

/** `first` comes before `second` in the page. */
async function expectBefore(first: Locator, second: Locator) {
  const [a, b] = await Promise.all([first.boundingBox(), second.boundingBox()]);
  if (!a || !b) throw new Error('not on the page');
  expect(a.y + a.height).toBeLessThanOrEqual(b.y);
}

/** The served image is a JPEG without EXIF (GPS included) nor XMP. */
async function expectNoMetadata(request: APIRequestContext, url: string) {
  const response = await request.get(url);
  expect(response.status(), url).toBe(200);
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
