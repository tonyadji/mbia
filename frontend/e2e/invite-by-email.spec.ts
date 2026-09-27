import { expect, test } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import { newUser, registerAndVerify } from './support/keycloak';
import { lastEmailTo } from './support/mailpit';

/**
 * PR-51 (phase-5-collaboration.md): the ADMIN invites a cousin by email as `Read only`
 * (SCREEN-009); the email reaches Mailpit in the inviter's language, with the Family, the inviter,
 * the role and a link that opens the invitation (mvp.md §18, OQ-055). Runs at phone width, in
 * French and English.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`invite a relative by email (${language})`, () => {
    test.use({ locale });

    test('the email arrives in the inviter language and its link opens the invitation', async ({
      page,
      request,
      browser,
    }) => {
      const user = newUser();
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, user);
      const familyName = `E2E ${user.email.slice(4, 12)}`;
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();

      await page.goto(`${familyUrl}/invitations/new`);
      const channel = page.getByRole('combobox', {
        name: t(language, 'invitation:invite.channel'),
      });
      // Share a link first on a phone.
      await expect(channel).toHaveValue('LINK');
      await channel.selectOption('EMAIL');
      const cousin = `cousin-${user.email}`;
      await page.getByLabel(t(language, 'invitation:invite.email')).fill(cousin);
      await page
        .getByRole('combobox', { name: t(language, 'invitation:invite.role') })
        .selectOption('VIEWER');
      await expectAccessibleControls(page);
      await page
        .getByRole('button', { name: t(language, 'invitation:invite.submitEmail') })
        .click();

      await expect(
        page.getByRole('heading', { name: t(language, 'invitation:email.sent') }),
      ).toBeVisible();
      await expect(
        page.getByText(t(language, 'invitation:email.sentTo', { email: cousin })),
      ).toBeVisible();
      await expectAccessibleControls(page);
      const overflow = await page.evaluate<number>(
        'document.documentElement.scrollWidth - document.documentElement.clientWidth',
      );
      expect(overflow).toBeLessThanOrEqual(0);

      // The email, in the inviter's language.
      const email = await lastEmailTo(request, cousin);
      expect(email.Subject).toBe(
        language === 'fr'
          ? `E2E Tester vous invite dans la famille ${familyName} sur Mbia`
          : `E2E Tester invites you to the ${familyName} family on Mbia`,
      );
      expect(email.Text).toContain(t(language, 'invitation:invite.roles.VIEWER'));
      const link = /https?:\/\/\S+\/invitations\/[\w-]{43}/.exec(email.Text)?.[0];
      expect(link).toBeDefined();

      // Its link opens the invitation, signed out.
      const context = await browser.newContext({ locale, viewport: { width: 375, height: 812 } });
      const invitee = await context.newPage();
      await invitee.goto(String(link));
      await expect(invitee.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:join.title', { family: familyName }),
      );
      await context.close();
    });
  });
}
