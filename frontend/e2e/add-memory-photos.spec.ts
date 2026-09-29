import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { openStartWithMe } from './support/family';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/** Image fixtures of Phase 3 (phase-3-family-memories.md §3.8), reused (Phase 4 plan §3.6). */
const fixture = (name: string) =>
  fileURLToPath(new URL(`../../backend/src/test/resources/media/${name}`, import.meta.url));
/** A phone photo larger than 2560 px, with GPS location. */
const LARGE_PHOTO = fixture('large-with-gps-3000x2000.jpg');
const PNG_PHOTO = fixture('gps.png');

interface CreatedMemory {
  title: string;
  content: string | null;
  photos: { caption: string | null; takenAt: { precision: string; year: number | null } | null }[];
}

/**
 * PR-43 (phase-4-memory-photos.md): at phone width, a User writes a story on SCREEN-006 and adds
 * two photos from their phone at once. The large one is sent reduced, each shows its progress,
 * `Publish` waits for both; the first gets a caption and the second a year behind `More
 * information`. A second Memory is published with a title and a photo only. The photos are shown
 * on the Memory from PR-44: here the published Memory is read from the API response.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`photos in Add Memory (${language})`, () => {
    test.use({ locale });

    test('a User adds two photos while writing a story', async ({ page, request }) => {
      await startFamily(page, request, language);
      await page.getByRole('link', { name: t(language, 'memory:story.tell') }).click();

      const title = language === 'fr' ? 'Le marché du samedi' : 'The Saturday market';
      const story =
        language === 'fr'
          ? 'Chaque samedi, maman nous emmenait au marché.'
          : 'Every Saturday, mum took us to the market.';
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(story);

      // Two photos chosen at once; the large one is sent reduced.
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
      await expect(photoImage(page, language, 1)).toBeVisible({ timeout: 15_000 });
      await expect(photoImage(page, language, 2)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('progressbar')).toHaveCount(0);
      await expect(publish).toBeEnabled();
      expect(puts).toHaveLength(2);
      // Both are sent in parallel: the reduced large photo is one of them.
      expect(puts.map(jpegSize)).toContainEqual({ width: 2560, height: 1707 });
      for (const size of puts.map(jpegSize)) {
        expect(Math.max(size.width, size.height)).toBeLessThanOrEqual(2560);
      }

      // A caption on the first photo, a year on the second.
      await page
        .getByLabel(t(language, 'memory:form.photos.caption', { position: '1' }))
        .fill(language === 'fr' ? 'Maman au marché' : 'Mum at the market');
      await page
        .getByRole('button', {
          name: t(language, 'memory:form.photos.moreInfoLabel', { position: '2' }),
        })
        .click();
      const second = page.getByRole('group', {
        name: t(language, 'memory:form.photos.photo', { position: '2' }),
      });
      await second.getByLabel(t(language, 'memory:form.photos.takenAt')).selectOption('YEAR_ONLY');
      await second.getByLabel(t(language, 'memory:form.photos.takenYear')).fill('1975');

      await expectAccessibleControls(page);
      await expectNoPageScroll(page);

      const memory = await publishAndRead(page, language);
      expect(memory.title).toBe(title);
      expect(memory.content).toBe(story);
      // An unknown taken date is returned as the precision UNKNOWN.
      expect(
        memory.photos.map((photo) => [
          photo.caption,
          photo.takenAt?.precision,
          photo.takenAt?.year,
        ]),
      ).toEqual([
        [language === 'fr' ? 'Maman au marché' : 'Mum at the market', 'UNKNOWN', null],
        [null, 'YEAR_ONLY', 1975],
      ]);
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
    });

    test('a User publishes a Memory with a title and a photo, without text', async ({
      page,
      request,
    }) => {
      await startFamily(page, request, language);
      await page.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      const title = language === 'fr' ? 'Mamie en 1975' : 'Grandma in 1975';
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
      await page.getByTestId('memory-photo-input').setInputFiles(PNG_PHOTO);
      await expect(photoImage(page, language, 1)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(t(language, 'memory:form.contentOptional'))).toBeVisible();

      const memory = await publishAndRead(page, language);
      expect(memory.title).toBe(title);
      expect(memory.content).toBeNull();
      expect(memory.photos).toHaveLength(1);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
    });
  });
}

/** A new User creates a Family and adds themselves, then lands on Family Home. */
async function startFamily(
  page: Page,
  request: Parameters<typeof registerAndVerify>[1],
  language: Language,
) {
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
  await openStartWithMe(page, language);
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
  await expect(page).toHaveURL(familyUrl);
}

function photoImage(page: Page, language: Language, position: number) {
  return page.getByRole('img', {
    name: t(language, 'memory:form.photos.thumbnail', { position: String(position) }),
  });
}

async function publishAndRead(page: Page, language: Language) {
  const created = page.waitForResponse(
    (response) => response.url().endsWith('/memories/stories') && response.status() === 201,
  );
  await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
  return (await (await created).json()) as CreatedMemory;
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

/** The page never scrolls sideways at phone width. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
