import { fileURLToPath } from 'node:url';
import { expect, test, type Locator, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/** Image fixtures of Phase 3 (phase-3-family-memories.md §3.8), reused (Phase 4 plan §3.6). */
const fixture = (name: string) =>
  fileURLToPath(new URL(`../../backend/src/test/resources/media/${name}`, import.meta.url));
/** Landscape, larger than 2560 px, with GPS location. */
const LANDSCAPE_PHOTO = fixture('large-with-gps-3000x2000.jpg');
/** A phone photo turned by its EXIF orientation. */
const ROTATED_PHOTO = fixture('exif-gps-orientation-6.jpg');
const PNG_PHOTO = fixture('gps.png');

/**
 * PR-44 and PR-45b (phase-4-memory-photos.md): at phone width, a Memory shows its photos after its
 * title as a grid of thumbnails in the order they were added, each named by its alternative text;
 * the viewer shows each photo large with its caption and taken date, and moves with its buttons and
 * a swipe (SCREEN-013, OQ-048). Nothing scrolls sideways, in portrait or landscape. An expired
 * photo URL is shown again. Its card shows the thumbnail of the first photo on the profile
 * (SCREEN-005) and in the Family Memories (SCREEN-015); a Memory without text shows its title only.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`photos on the Memory and its cards (${language})`, () => {
    test.use({ locale });

    test('a Memory shows its photos, and its card the first one', async ({ page, request }) => {
      const familyUrl = await startFamily(page, request, language);

      // A story with three photos: a caption on the first, a year on the second.
      await page.getByRole('link', { name: t(language, 'family:home.addMemory') }).click();
      const title = language === 'fr' ? 'Le marché du samedi' : 'The Saturday market';
      const story =
        language === 'fr'
          ? 'Chaque samedi, maman nous emmenait au marché.'
          : 'Every Saturday, mum took us to the market.';
      const caption = language === 'fr' ? 'Maman au marché' : 'Mum at the market';
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
      await page.getByLabel(t(language, 'memory:form.contentLabel')).fill(story);
      await page
        .getByTestId('memory-photo-input')
        .setInputFiles([LANDSCAPE_PHOTO, ROTATED_PHOTO, PNG_PHOTO]);
      for (const position of [1, 2, 3]) {
        await expect(
          page.getByRole('img', {
            name: t(language, 'memory:form.photos.thumbnail', { position: String(position) }),
          }),
        ).toBeVisible({ timeout: 15_000 });
      }
      await page
        .getByLabel(t(language, 'memory:form.photos.caption', { position: '1' }))
        .fill(caption);
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
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();

      // SCREEN-013: title, then the photos in order as a grid of thumbnails, then the story.
      const title1 = page.getByRole('heading', { level: 1 });
      await expect(title1).toHaveText(title);
      const photos = page.getByRole('list', { name: t(language, 'memory:screen.photos.label') });
      const thumbnails = photos.getByRole('button');
      await expect(thumbnails).toHaveCount(3);
      const alts = [
        caption,
        t(language, 'memory:screen.photos.alt', { n: '2', total: '3' }),
        t(language, 'memory:screen.photos.alt', { n: '3', total: '3' }),
      ];
      for (const [index, alt] of alts.entries()) {
        await expect(thumbnails.nth(index)).toHaveAccessibleName(alt);
        await expect(thumbnails.nth(index).locator('img')).toHaveAttribute('loading', 'lazy');
      }
      await expectBefore(title1, photos);
      await expectBefore(photos, page.getByText(story));
      for (const index of [0, 1, 2]) await expectLoadedImage(thumbnails.nth(index).locator('img'));

      // At 375 px, nothing scrolls sideways, in portrait then in landscape (812 × 375).
      await expectAccessibleControls(page);
      await expectFullWidth(page, thumbnails);
      const viewport = page.viewportSize();
      await page.setViewportSize({ width: 812, height: 375 });
      await expectFullWidth(page, thumbnails);
      if (viewport) await page.setViewportSize(viewport);

      // The viewer: the tapped photo large, its caption and date, previous and next, a swipe.
      await thumbnails.nth(0).click();
      // Named by the position of its photo, which changes: found by its role.
      const viewer = page.getByRole('dialog');
      await expect(viewer).toHaveAccessibleName(
        t(language, 'memory:screen.photos.alt', { n: '1', total: '3' }),
      );
      await expectLoadedImage(viewer.getByRole('img', { name: caption }));
      await expect(viewer.getByText(caption)).toBeVisible();
      await expect(
        viewer.getByRole('button', { name: t(language, 'memory:screen.photos.previous') }),
      ).toBeDisabled();
      await expectAccessibleControls(page);
      await expectFullWidth(page, viewer.getByRole('img'));
      await viewer.getByRole('button', { name: t(language, 'memory:screen.photos.next') }).click();
      await expect(viewer.getByRole('img', { name: alts[1] })).toBeVisible();
      await expect(
        viewer.getByText(t(language, 'memory:screen.photos.takenAt', { date: '1975' })),
      ).toBeVisible();
      await swipe(page, viewer.getByRole('img'), -150);
      await expect(viewer.getByRole('img', { name: alts[2] })).toBeVisible();
      await expect(
        viewer.getByRole('button', { name: t(language, 'memory:screen.photos.next') }),
      ).toBeDisabled();
      await swipe(page, viewer.getByRole('img'), 150);
      await expect(viewer.getByRole('img', { name: alts[1] })).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await expect(thumbnails.nth(0)).toBeFocused();

      // An expired pre-signed URL (S3 answers 403): the photo comes back with a fresh URL.
      let expired = false;
      await page.route(
        (url) => url.pathname.endsWith('/thumbnail'),
        async (route) => {
          if (expired) return route.continue();
          expired = true;
          // A second later, the new URL is signed at another time.
          await new Promise((resolve) => setTimeout(resolve, 1100));
          return route.fulfill({ status: 403, body: 'Request has expired' });
        },
      );
      await page.goto(memoryUrl);
      await expectLoadedImage(
        page.getByRole('button', { name: caption, exact: true }).locator('img'),
      );
      expect(expired).toBe(true);
      await page.unroute((url) => url.pathname.endsWith('/thumbnail'));

      // The card shows the first photo, decorative, on the profile and in the Family Memories.
      await page.getByRole('link', { name: 'Alice' }).click();
      const card = page.getByRole('link', { name: new RegExp(`^${title}`) });
      await expectThumbnail(card);
      await page.goto(familyUrl);
      await page
        .getByRole('navigation', { name: t(language, 'family:navigation.label') })
        .getByRole('link', { name: t(language, 'family:navigation.memories') })
        .click();
      await expectThumbnail(page.getByRole('link', { name: new RegExp(`^${title}`) }));
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);

      // A Memory with a title and a photo, without text: its card shows its title only.
      await page.getByRole('link', { name: t(language, 'memory:family.add') }).click();
      const textless = language === 'fr' ? 'Mamie en 1975' : 'Grandma in 1975';
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(textless);
      await page.getByTestId('memory-photo-input').setInputFiles(PNG_PHOTO);
      await expect(
        page.getByRole('img', {
          name: t(language, 'memory:form.photos.thumbnail', { position: '1' }),
        }),
      ).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(textless);
      await expect(
        page.getByRole('button', {
          name: t(language, 'memory:screen.photos.alt', { n: '1', total: '1' }),
        }),
      ).toBeVisible();
      await page.goto(familyUrl);
      await page
        .getByRole('navigation', { name: t(language, 'family:navigation.label') })
        .getByRole('link', { name: t(language, 'family:navigation.memories') })
        .click();
      const textlessCard = page.getByRole('link', { name: new RegExp(`^${textless}`) });
      await expectThumbnail(textlessCard);
      await expect(textlessCard).toHaveText(textless);
    });
  });
}

/** A new User creates a Family and adds themselves as Alice; returns the Family Home URL. */
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
  await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
  await expect(page).toHaveURL(familyUrl);
  return familyUrl;
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

/** A card's thumbnail: loaded, decorative, the card named by its title. */
async function expectThumbnail(card: Locator) {
  const thumbnail = card.locator('img');
  await expectLoadedImage(thumbnail);
  await expect(thumbnail).toHaveAttribute('alt', '');
  await expect(card.getByRole('img')).toHaveCount(0);
}

/** `first` comes before `second` in the page. */
async function expectBefore(first: Locator, second: Locator) {
  const [a, b] = await Promise.all([first.boundingBox(), second.boundingBox()]);
  if (!a || !b) throw new Error('not on the page');
  expect(a.y + a.height).toBeLessThanOrEqual(b.y);
}

/** A horizontal swipe with a finger (a mouse drag here): negative to the left. */
async function swipe(page: Page, target: Locator, dx: number) {
  const box = await target.boundingBox();
  if (!box) throw new Error('not on the page');
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx, y, { steps: 5 });
  await page.mouse.up();
}

/** Every photo fits the width of the page, which never scrolls sideways. */
async function expectFullWidth(page: Page, images: Locator) {
  await expectNoPageScroll(page);
  const viewport = page.viewportSize();
  if (!viewport) throw new Error('no viewport');
  for (const image of await images.all()) {
    const box = await image.boundingBox();
    if (!box) throw new Error('photo not on the page');
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
  }
}

/** The page never scrolls sideways. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
