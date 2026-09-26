import { fileURLToPath } from 'node:url';
import { expect, test, type Locator } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

/** A phone photo larger than 2560 px, with GPS location (Phase 3 plan §3.8). */
const LARGE_PHOTO = fileURLToPath(
  new URL('../../backend/src/test/resources/media/large-with-gps-3000x2000.jpg', import.meta.url),
);

/**
 * PR-38 (phase-3-family-memories.md): an ADMIN gives a photo to a Person from SCREEN-012 at phone
 * width. The photo is reduced in the browser before upload (§3.6), its progress is shown, and the
 * thumbnail appears on the profile and on the tree card. An unsupported file is refused before any
 * upload, and an image whose pre-signed URL expired is shown again with a fresh URL.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`Person photo (${language})`, () => {
    test.use({ locale });

    test('an ADMIN gives a photo to a Person; the tree card shows it', async ({
      page,
      request,
    }) => {
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
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await page.getByLabel(t(language, 'person:form.firstName'), { exact: true }).fill('Awa');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa');
      const profileUrl = page.url();
      await page.getByRole('link', { name: t(language, 'person:profile.edit') }).click();

      // An unsupported file is refused before any upload, in human language.
      const slots: string[] = [];
      page.on('request', (sent) => {
        if (sent.method() === 'POST' && sent.url().endsWith('/media/uploads'))
          slots.push(sent.url());
      });
      const input = page.getByTestId('photo-input');
      await input.setInputFiles({
        name: 'notes.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('not a photo'),
      });
      await expect(page.getByRole('alert')).toHaveText(
        new RegExp(escape(t(language, 'person:photo.errors.unsupported'))),
      );
      expect(slots).toHaveLength(0);

      // The large photo is sent reduced; progress stays visible while the server checks it.
      await page.route('**/media/uploads/*/complete', async (route) => {
        await new Promise((resolve) => setTimeout(resolve, 500));
        await route.continue();
      });
      const upload = page.waitForRequest((sent) => sent.method() === 'PUT');
      await input.setInputFiles(LARGE_PHOTO);
      await expect(page.getByRole('progressbar')).toBeVisible();
      const body = (await upload).postDataBuffer();
      expect(body).not.toBeNull();
      const size = jpegSize(body ?? Buffer.alloc(0));
      expect(Math.max(size.width, size.height)).toBeLessThanOrEqual(2560);
      expect(size).toEqual({ width: 2560, height: 1707 });
      await expect(page.getByRole('progressbar')).toBeHidden();
      await expect(
        page.getByRole('button', { name: t(language, 'person:photo.change') }),
      ).toBeVisible();
      await expectAccessibleControls(page);
      await page.getByRole('button', { name: t(language, 'person:edit.submit') }).click();

      // The thumbnail is on the profile, then on the tree card.
      await expect(page).toHaveURL(profileUrl);
      await expectLoadedImage(page.locator('header img'));
      await page.goto(familyUrl);
      await page.getByRole('link', { name: t(language, 'family:home.viewTree') }).click();
      await expectLoadedImage(page.getByRole('button', { name: /^Awa/ }).locator('img'));
      await expect(page.getByRole('button', { name: /^Alice/ }).locator('img')).toHaveCount(0);

      // An expired pre-signed URL (S3 answers 403): the image comes back with a fresh URL.
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
      await page.goto(profileUrl);
      await expectLoadedImage(page.locator('header img'));
      expect(expired).toBe(true);
    });
  });
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
