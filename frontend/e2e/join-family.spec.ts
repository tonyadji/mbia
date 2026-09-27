import { expect, test, type Page } from '@playwright/test';
import { expectAccessibleControls } from './support/accessibility';
import { t, type Language } from './support/i18n';
import {
  newUser,
  openRegistration,
  registerAndVerify,
  registerAndVerifyInAnotherTab,
} from './support/keycloak';

const PHONE = { width: 375, height: 812 };
const PENDING_INVITATION = 'mbia.pendingInvitation';

/**
 * PR-50 (phase-5-collaboration.md): the ADMIN invites their mother Awa by a link; Awa, signed out,
 * opens it, reads who invites her (never her name), signs up on Keycloak, verifies her email from
 * the mailbox in another tab and comes back to the invitation; she joins, answers "Are you Awa
 * Ngo?" with `Yes, it's me`, is welcomed, and the tree is centred on her (SCREEN-010, SCREEN-002,
 * mvp.md §18, OQ-050). The used link then explains itself. At phone width, in French and English.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`join a family from an invitation link (${language})`, () => {
    test.use({ locale });

    test('Awa signs up, verifies her email in another tab, joins and links herself', async ({
      page,
      browser,
      request,
      baseURL,
    }) => {
      const { link, familyName } = await inviteAwa(page, request, language);

      // Awa, in her own browser, signed out.
      const context = await browser.newContext({ locale, viewport: PHONE, baseURL });
      const awa = await context.newPage();
      await awa.goto(link);
      await expect(awa.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:join.title', { family: familyName }),
      );
      await expect(
        awa.getByText(t(language, 'invitation:join.invitedBy', { name: 'E2E Tester' })),
      ).toBeVisible();
      await expect(awa.getByText(t(language, 'invitation:invite.roles.CONTRIBUTOR'))).toBeVisible();
      await expect(awa.getByText('Awa')).toHaveCount(0);
      await expectAccessibleControls(awa);
      await expectNoPageScroll(awa);

      // Join → Keycloak sign-in → sign-up; the email is verified from the mailbox in another tab.
      await awa.getByRole('button', { name: t(language, 'invitation:join.submit') }).click();
      await openRegistration(awa);
      const user = newUser();
      const mailboxTab = await registerAndVerifyInAnotherTab(awa, request, user);

      // Back on the same invitation in that tab, which joins without another tap.
      await expect(mailboxTab.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:onboarding.areYou', { name: 'Awa Ngo' }),
      );
      expect(await pendingToken(mailboxTab)).toBeNull();
      await expectAccessibleControls(mailboxTab);
      await expectNoPageScroll(mailboxTab);

      await mailboxTab
        .getByRole('button', { name: t(language, 'invitation:onboarding.yes') })
        .click();
      const welcome = mailboxTab.getByRole('region', {
        name: t(language, 'family:home.welcome.title', { name: familyName }),
      });
      await expect(welcome).toBeVisible();
      await expect(
        welcome.getByRole('link', { name: t(language, 'family:home.addMemory') }),
      ).toBeVisible();
      await expectAccessibleControls(mailboxTab);
      await expectNoPageScroll(mailboxTab);

      // The tree is centred on her.
      await welcome
        .getByRole('link', { name: t(language, 'family:home.welcome.viewTree') })
        .click();
      await expect(mailboxTab).toHaveURL(/\/tree\?focus=[0-9a-f-]{36}$/);
      await expect(mailboxTab.locator('[data-role="focus"]').getByRole('button')).toContainText(
        'Awa Ngo',
      );

      // The used link explains itself and suggests asking for a new one.
      await mailboxTab.goto(link);
      await expect(mailboxTab.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:join.invalid.INVITATION_ALREADY_USED'),
      );
      await expect(
        mailboxTab.getByText(t(language, 'invitation:join.invalid.askAgain')),
      ).toBeVisible();
      expect(await pendingToken(mailboxTab)).toBeNull();

      await context.close();
    });
  });
}

/**
 * A new ADMIN creates a Family with themselves and their mother Awa Ngo, then invites Awa by a
 * `Can contribute` link from her profile (SCREEN-005, SCREEN-009).
 */
async function inviteAwa(
  page: Page,
  request: Parameters<typeof registerAndVerify>[1],
  language: Language,
) {
  const admin = newUser();
  await page.goto('/');
  await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
  await registerAndVerify(page, request, admin);
  const familyName = `E2E ${admin.email.slice(4, 12)}`;
  await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
  await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
  await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
  await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
  await firstName(page, language).fill('Alice');
  await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();

  await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
  await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
  await firstName(page, language).fill('Awa');
  await page.getByLabel(t(language, 'person:form.lastName'), { exact: true }).fill('Ngo');
  await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
  await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
  await page
    .getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) })
    .click();
  await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
  const link = await page.getByText(/^https?:\/\/\S+\/invitations\/[\w-]+$/).textContent();
  return { link: String(link), familyName };
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}

/** The raw token kept by the browser while the invitation is pending (OQ-050). */
function pendingToken(page: Page) {
  return page.evaluate<string | null>(`localStorage.getItem('${PENDING_INVITATION}')`);
}

/** The page never scrolls sideways at phone width. */
async function expectNoPageScroll(page: Page) {
  // The e2e tsconfig has no DOM types: the expression runs in the page.
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
