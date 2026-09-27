import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';

const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';

/**
 * PR-49 (phase-5-collaboration.md): the ADMIN invites their mother Awa from her profile
 * (SCREEN-005), creates a `Can contribute` link (SCREEN-009), copies and shares it with the message
 * naming the inviter and Awa, then renews it from her profile (mvp.md §18, OQ-050). Runs at phone
 * width, in French and English.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`invite a relative from their profile (${language})`, () => {
    test.use({ locale, permissions: ['clipboard-read', 'clipboard-write'] });

    test('an ADMIN invites Awa by a link, then renews it', async ({ page, request }) => {
      // The device share sheet, recorded instead of opened.
      await page.addInitScript(`
        window.__shared = [];
        navigator.share = (data) => { window.__shared.push(data); return Promise.resolve(); };
      `);
      const user = newUser();
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      const familyName = `E2E ${user.email.slice(4, 12)}`;
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

      // Awa Ngo, my mother, living and linked to no one.
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await firstName(page, language).fill('Awa');
      await page.getByLabel(t(language, 'person:form.lastName'), { exact: true }).fill('Ngo');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa Ngo');
      const profileUrl = page.url();

      // My own profile, linked to me, cannot be invited.
      await page.getByRole('link', { name: /^Alice/ }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alice');
      await expect(
        page.getByRole('link', {
          name: t(language, 'invitation:profile.invite', { name: 'Alice' }),
        }),
      ).toHaveCount(0);

      // From Awa's profile: Invite Awa, Can contribute preselected.
      await page.goto(profileUrl);
      await page
        .getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) })
        .click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:invite.titlePerson', { name: 'Awa Ngo' }),
      );
      const permission = page.getByRole('combobox', {
        name: t(language, 'invitation:invite.role'),
      });
      await expect(permission).toHaveValue('CONTRIBUTOR');
      await expect(permission.locator('option:checked')).toHaveText(
        t(language, 'invitation:invite.roles.CONTRIBUTOR'),
      );
      await expectAccessibleControls(page);
      await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();

      // The link, shown once, with Copy link, Share and the message.
      await expect(
        page.getByRole('heading', { name: t(language, 'invitation:share.done') }),
      ).toBeVisible();
      const link = await page.getByText(/^https?:\/\/\S+\/invitations\/[\w-]+$/).textContent();
      expect(link).toMatch(/^http:\/\/localhost:5173\/invitations\/[\w-]{43}$/);
      const message = t(language, 'invitation:share.withPerson', {
        inviter: 'E2E Tester',
        family: familyName,
        person: 'Awa Ngo',
        link: String(link),
      });
      await expect(page.getByText(message, { exact: true })).toBeVisible();
      await expect(page.getByText(t(language, 'invitation:share.once'))).toBeVisible();

      await page.getByRole('button', { name: t(language, 'invitation:share.copy') }).click();
      await expect(page.getByRole('status')).toHaveText(t(language, 'invitation:share.copied'));
      expect(await page.evaluate<string>('navigator.clipboard.readText()')).toBe(link);

      await page.getByRole('button', { name: t(language, 'invitation:share.share') }).click();
      expect(await page.evaluate<unknown>('window.__shared')).toEqual([{ text: message }]);
      await expectAccessibleControls(page);
      await expectNoPageScroll(page);

      // The link works: its public preview names the Family and who invites.
      const token = String(link).split('/').pop();
      const preview = await request.get(`${API_URL}/api/v1/invitations/${String(token)}`);
      expect(preview.ok()).toBe(true);
      expect(await preview.json()).toMatchObject({ familyName, role: 'CONTRIBUTOR' });

      // Back on Awa's profile: Invitation pending, and Renew gives a new link.
      await page
        .getByRole('link', {
          name: t(language, 'invitation:invite.backToProfile', { name: 'Awa Ngo' }),
        })
        .click();
      await expect(page.getByText(t(language, 'invitation:profile.pending'))).toBeVisible();
      await expect(
        page.getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) }),
      ).toHaveCount(0);
      await expectAccessibleControls(page);
      await page
        .getByRole('button', { name: t(language, 'invitation:profile.renewFor', { name: 'Awa' }) })
        .click();
      await expect(page.getByText(t(language, 'invitation:profile.renewed'))).toBeVisible();
      const renewed = await page.getByText(/^https?:\/\/\S+\/invitations\/[\w-]+$/).textContent();
      expect(renewed).toMatch(/\/invitations\/[\w-]{43}$/);
      expect(renewed).not.toBe(link);
      await expectNoPageScroll(page);
      // The previous link stops working.
      const previous = await request.get(`${API_URL}/api/v1/invitations/${String(token)}`);
      expect(previous.status()).toBe(404);
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
