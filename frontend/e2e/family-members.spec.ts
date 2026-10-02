import { expect, test, type Locator, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { openStartWithMe } from './support/family';
import { t, type Language } from './support/i18n';
import {
  newUser,
  openRegistration,
  registerAndVerify,
  registerAndVerifyInAnotherTab,
} from './support/keycloak';

const PHONE = { width: 375, height: 812 };
const LINK = /^https?:\/\/\S+\/invitations\/[\w-]+$/;

/**
 * PR-53 (phase-5-collaboration.md): the Members screen (SCREEN-008, mvp.md §5). The ADMIN, alone,
 * reads why they cannot leave and invites from the screen; their mother Awa joins and links
 * herself; the ADMIN sees "Awa · your mother", makes her `Read only`, renews a pending invitation
 * (its new link shown once) and revokes it; Awa, who has no management action, leaves the Family
 * and lands on Family creation; her Person stays in the tree, no longer linked. At phone width, in
 * French and English.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`family members (${language})`, () => {
    test.use({ locale });

    test('the ADMIN manages the members, and a relative leaves', async ({
      page,
      browser,
      request,
      baseURL,
    }) => {
      const { awaLink, profileUrl, membersUrl, familyName } = await familyWithAwa(
        page,
        request,
        language,
      );

      // Alone: themselves, their role, and why they cannot leave.
      await page.goto(membersUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'family:members.title'),
      );
      const list = memberList(page, language);
      await expect(list.getByRole('listitem')).toHaveCount(1);
      await expect(list.getByText(t(language, 'person:kinship.label.SELF'))).toBeVisible();
      await expect(list.getByText(t(language, 'family:members.roles.ADMIN'))).toBeVisible();
      await expect(page.getByText(t(language, 'family:members.onlyAdmin'))).toBeVisible();
      await expect(
        page.getByRole('button', { name: t(language, 'family:members.leave') }),
      ).toHaveCount(0);

      // `Invite a relative`: a link for someone who is not in the tree.
      await page.getByRole('link', { name: t(language, 'family:members.invite') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:invite.title'),
      );
      await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
      const firstLink = String(await page.getByText(LINK).textContent());

      // Awa, in her own browser, joins with her link and links herself.
      const context = await browser.newContext({ locale, viewport: PHONE, baseURL });
      const awaSignedOut = await context.newPage();
      await awaSignedOut.goto(awaLink);
      await awaSignedOut
        .getByRole('button', { name: t(language, 'invitation:join.submit') })
        .click();
      await openRegistration(awaSignedOut);
      const awa = await registerAndVerifyInAnotherTab(awaSignedOut, request, newUser());
      await awa.getByRole('button', { name: t(language, 'invitation:onboarding.yes') }).click();
      await expect(
        awa.getByRole('region', {
          name: t(language, 'family:home.welcome.title', { name: familyName }),
        }),
      ).toBeVisible();

      // The ADMIN sees Awa as their mother, and makes her `Read only`.
      await page.goto(membersUrl);
      const awaRow = memberList(page, language)
        .getByRole('listitem')
        .filter({ hasText: 'Awa Ngo' });
      await expect(awaRow.getByText(t(language, 'person:kinship.label.MOTHER'))).toBeVisible();
      const permission = awaRow.getByRole('combobox', {
        name: t(language, 'family:members.roleOf', { name: 'Awa Ngo' }),
      });
      await expect(permission).toHaveValue('CONTRIBUTOR');
      await expectAccessibleControls(page);
      const patched = page.waitForResponse(
        (response) => response.request().method() === 'PATCH' && response.ok(),
      );
      await permission.selectOption('VIEWER');
      await patched;
      await page.reload();
      await expect(permission).toHaveValue('VIEWER');

      // The pending invitation: its new link is shown once, then it is revoked.
      const pending = page.getByRole('region', {
        name: t(language, 'family:members.invitations.title'),
      });
      const sharedLink = t(language, 'family:members.invitations.sharedLink');
      await expect(pending.getByRole('listitem')).toHaveCount(1);
      await expect(pending.getByText(sharedLink)).toBeVisible();
      await pending
        .getByRole('button', {
          name: t(language, 'family:members.invitations.renewFor', { target: sharedLink }),
        })
        .click();
      const renewed = page.getByRole('dialog', { name: sharedLink });
      const newLink = String(await renewed.getByText(LINK).textContent());
      expect(newLink).not.toBe(firstLink);
      await expectAccessibleControls(page);
      await renewed.getByRole('button', { name: t(language, 'family:members.close') }).click();
      await expect(page.getByText(newLink)).toHaveCount(0);

      await pending
        .getByRole('button', {
          name: t(language, 'family:members.invitations.revokeFor', { target: sharedLink }),
        })
        .click();
      await page
        .getByRole('dialog')
        .getByRole('button', { name: t(language, 'family:members.invitations.revokeAction') })
        .click();
      await expect(pending).toHaveCount(0);

      // Awa, `Read only`: no management action; she leaves and lands on Family creation.
      await awa.goto(membersUrl);
      const herself = memberList(awa, language)
        .getByRole('listitem')
        .filter({ hasText: t(language, 'person:kinship.label.SELF') });
      await expect(herself.getByText(t(language, 'invitation:invite.roles.VIEWER'))).toBeVisible();
      await expect(awa.getByRole('combobox')).toHaveCount(0);
      await expect(
        awa.getByRole('link', { name: t(language, 'family:members.invite') }),
      ).toHaveCount(0);
      await expect(
        awa.getByRole('region', { name: t(language, 'family:members.invitations.title') }),
      ).toHaveCount(0);
      await expect(awa.getByText(t(language, 'family:members.onlyAdmin'))).toHaveCount(0);
      await expectAccessibleControls(awa);
      await awa.getByRole('button', { name: t(language, 'family:members.leave') }).click();
      const leave = awa.getByRole('dialog', { name: t(language, 'family:members.leaveConfirm') });
      await expect(leave.getByText(t(language, 'family:members.leaveHelp'))).toBeVisible();
      await leave.getByRole('button', { name: t(language, 'family:members.leaveAction') }).click();
      await expect(awa).toHaveURL(/\/families\/new$/);
      await expect(awa.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'family:create.title'),
      );
      await context.close();

      // The ADMIN is alone again; Awa's Person stays, linked to no one, and can be invited again.
      await page.goto(membersUrl);
      await expect(memberList(page, language).getByRole('listitem')).toHaveCount(1);
      await page.goto(profileUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa Ngo');
      await expect(
        page.getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) }),
      ).toBeVisible();
    });
  });
}

function memberList(page: Page, language: Language): Locator {
  return page.getByRole('list', { name: t(language, 'family:members.title') });
}

/**
 * A new ADMIN creates a Family with themselves and their mother Awa Ngo, invites Awa by a
 * `Can contribute` link from her profile (SCREEN-005, SCREEN-009), then opens `Members` from the
 * navigation.
 */
async function familyWithAwa(
  page: Page,
  request: Parameters<typeof registerAndVerify>[1],
  language: Language,
) {
  await page.goto('/');
  await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
  await registerAndVerify(page, request, newUser());
  const familyName = `E2E ${language} members`;
  await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
  await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
  await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
  const familyUrl = page.url();
  await openStartWithMe(page, language);
  await firstName(page, language).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

  await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
  await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
  await firstName(page, language).fill('Awa');
  await page.getByLabel(t(language, 'person:form.lastName'), { exact: true }).fill('Ngo');
  await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
  await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
  const profileUrl = page.url();
  await page
    .getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) })
    .click();
  await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
  const awaLink = String(await page.getByText(LINK).textContent());

  await page.goto(familyUrl);
  await page
    .getByRole('navigation', { name: t(language, 'family:navigation.label') })
    .getByRole('link', { name: t(language, 'family:navigation.members') })
    .click();
  await expect(page).toHaveURL(/\/members$/);
  return { awaLink, profileUrl, membersUrl: page.url(), familyName };
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}
