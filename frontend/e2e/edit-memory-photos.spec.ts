import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { openStartWithMe } from './support/family';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/** Image fixtures of Phase 3 (phase-3-family-memories.md §3.8), reused (Phase 4 plan §3.6). */
const fixture = (name: string) =>
  fileURLToPath(new URL(`../../backend/src/test/resources/media/${name}`, import.meta.url));
const LANDSCAPE_PHOTO = fixture('large-with-gps-3000x2000.jpg');
const ROTATED_PHOTO = fixture('exif-gps-orientation-6.jpg');
const PNG_PHOTO = fixture('gps.png');

/**
 * PR-45 (phase-4-memory-photos.md): at phone width, a User edits the photos of their story
 * (SCREEN-014): adds a third photo, which reaches the Family's limit (3 at launch) and disables
 * `Add a photo` with its explanation, removes the second one, changes a caption, and saves.
 * SCREEN-013 then shows the photos in their order.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`photos in Edit Memory (${language})`, () => {
    test.use({ locale });

    test('a User adds, removes and describes the photos of their story', async ({
      page,
      request,
    }) => {
      await startFamily(page, request, language);
      const photoLabel = (key: string, position: number) =>
        t(language, `memory:form.photos.${key}`, { position: String(position) });

      // A story with two photos, the first with a caption.
      await page.getByRole('link', { name: t(language, 'memory:story.tell') }).click();
      const title = language === 'fr' ? 'La fête au village' : 'The village party';
      const first = language === 'fr' ? 'Les danseurs' : 'The dancers';
      await page.getByLabel(t(language, 'memory:form.titleLabel')).fill(title);
      await page
        .getByLabel(t(language, 'memory:form.contentLabel'))
        .fill(language === 'fr' ? 'Tout le village dansait.' : 'The whole village danced.');
      await page.getByTestId('memory-photo-input').setInputFiles([LANDSCAPE_PHOTO, PNG_PHOTO]);
      for (const position of [1, 2]) {
        await expect(
          page.getByRole('img', { name: photoLabel('thumbnail', position) }),
        ).toBeVisible({ timeout: 15_000 });
      }
      await page.getByLabel(photoLabel('caption', 1)).fill(first);
      await page.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const memoryUrl = page.url();

      // SCREEN-014 shows the photos of the Memory, with their caption.
      await page.getByRole('link', { name: t(language, 'memory:screen.edit') }).click();
      await expect(page).toHaveURL(`${memoryUrl}/edit`);
      await expect(page.getByRole('img', { name: photoLabel('thumbnail', 2) })).toBeVisible();
      await expect(page.getByLabel(photoLabel('caption', 1))).toHaveValue(first);

      // A third photo reaches the limit: `Add a photo` is disabled, and says why.
      const addPhoto = page.getByRole('button', { name: t(language, 'memory:form.photos.add') });
      await page.getByTestId('memory-photo-input').setInputFiles(ROTATED_PHOTO);
      await expect(page.getByRole('img', { name: photoLabel('thumbnail', 3) })).toBeVisible({
        timeout: 15_000,
      });
      await expect(addPhoto).toBeDisabled();
      await expect(addPhoto).toHaveAccessibleDescription(
        t(language, 'memory:form.photos.limit_other', { count: '3' }),
      );
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);

      // Remove the second photo, describe the new one, save.
      await page.getByRole('button', { name: photoLabel('removeLabel', 2) }).click();
      await expect(addPhoto).toBeEnabled();
      const third = language === 'fr' ? 'Le feu de camp' : 'The campfire';
      await page.getByLabel(photoLabel('caption', 2)).fill(third);
      await page.getByRole('button', { name: t(language, 'memory:edit.submit') }).click();

      // SCREEN-013 shows the result, in order.
      await expect(page).toHaveURL(memoryUrl);
      const thumbnails = page
        .getByRole('list', { name: t(language, 'memory:screen.photos.label') })
        .getByRole('button');
      await expect(thumbnails).toHaveCount(2);
      await expect(thumbnails.nth(0)).toHaveAccessibleName(first);
      await expect(thumbnails.nth(1)).toHaveAccessibleName(third);
      await expectNoPageScroll(page);
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
  await openStartWithMe(page, language);
  await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
  await expect(page).toHaveURL(familyUrl);
  return familyUrl;
}

/** The page never scrolls sideways. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
