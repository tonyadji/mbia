import { expect, test, type Browser, type Locator, type Page } from '@playwright/test';
import { expectAccessibleControls, expectReadableScreen } from './support/accessibility';
import { t, type Language } from './support/i18n';
import {
  newUser,
  openRegistration,
  registerAndVerify,
  registerAndVerifyInAnotherTab,
} from './support/keycloak';
import { lastEmailTo } from './support/mailpit';

const PHONE = { width: 375, height: 812 };
const LINK = /^https?:\/\/\S+\/invitations\/[\w-]+$/;
const ADMIN = 'E2E Tester';

/**
 * PR-57: the Phase 5 journey of phase-5-collaboration.md §1, end to end, at phone width, in French
 * and English. The ADMIN invites their mother Awa from her profile with a link; Awa, signed out,
 * joins, recognises herself and adds a memory about her mother; the ADMIN reads it on Family Home;
 * a cousin invited by email `Read only` joins, first answers `No` on his homonym uncle, finds
 * himself by his parent and links himself; the ADMIN manages the members; the cousin leaves; the
 * ADMIN cannot; used, revoked and expired links explain themselves. SCREEN-008, SCREEN-009,
 * SCREEN-010 and the Family Home feed are checked against design-guidelines.md §9 on every step.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`Phase 5 journey (${language})`, () => {
    test.use({ locale, viewport: PHONE });

    test('from inviting a mother to a cousin who leaves', async ({
      page,
      browser,
      request,
      baseURL,
    }) => {
      test.setTimeout(240_000);
      const open = (target: Browser) => target.newContext({ locale, viewport: PHONE, baseURL });
      const story = stories[language];

      // The ADMIN's Family: me, my mother Awa, my grandmother Marie, her children Paul and Rose,
      // and Rose's son Paul, the cousin, who has his uncle's name.
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, newUser());
      const familyName = `E2E ${language} phase 5`;
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();
      const membersUrl = `${familyUrl}/members`;
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await firstName(page, language).fill('Awa');
      await lastName(page, language).fill('Ngo');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa Ngo');
      const awaUrl = page.url();
      await addRelative(page, language, 'MOTHER', 'Marie');
      const marieUrl = await relativeUrl(page, language, 'parents', 'Marie Ngo');
      await page.goto(marieUrl);
      await addRelative(page, language, 'SON', 'Paul');
      await addRelative(page, language, 'DAUGHTER', 'Rose');
      const roseUrl = await relativeUrl(page, language, 'children', 'Rose Ngo');
      await page.goto(roseUrl);
      await addRelative(page, language, 'SON', 'Paul');
      const cousinUrl = await relativeUrl(page, language, 'children', 'Paul Ngo');

      // From Awa's profile: Invite Awa, a link, `Can contribute` (SCREEN-005, SCREEN-009).
      await page.goto(awaUrl);
      await page
        .getByRole('link', { name: t(language, 'invitation:profile.invite', { name: 'Awa' }) })
        .click();
      await expect(
        page.getByRole('combobox', { name: t(language, 'invitation:invite.role') }),
      ).toHaveValue('CONTRIBUTOR');
      await expectScreen(page, language);
      await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
      const awaLink = String(await page.getByText(LINK).textContent());
      await expectScreen(page, language);

      // Awa, signed out, opens the link: the Family and who invites, never her name (SCREEN-010).
      const awaContext = await open(browser);
      const awaSignedOut = await awaContext.newPage();
      await awaSignedOut.goto(awaLink);
      await expect(awaSignedOut.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:join.title', { family: familyName }),
      );
      await expect(
        awaSignedOut.getByText(t(language, 'invitation:join.invitedBy', { name: ADMIN })),
      ).toBeVisible();
      await expect(awaSignedOut.getByText('Awa')).toHaveCount(0);
      await expectScreen(awaSignedOut, language);

      // She signs up, verifies her email in the mailbox, comes back and recognises herself.
      await awaSignedOut
        .getByRole('button', { name: t(language, 'invitation:join.submit') })
        .click();
      await openRegistration(awaSignedOut);
      const awa = await registerAndVerifyInAnotherTab(awaSignedOut, request, newUser());
      await expect(awa.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:onboarding.areYou', { name: 'Awa Ngo' }),
      );
      await expectScreen(awa, language);
      await awa.getByRole('button', { name: t(language, 'invitation:onboarding.yes') }).click();
      const welcome = awa.getByRole('region', {
        name: t(language, 'family:home.welcome.title', { name: familyName }),
      });
      await expect(welcome).toBeVisible();
      await welcome
        .getByRole('link', { name: t(language, 'family:home.welcome.viewTree') })
        .click();
      await expect(awa.locator('[data-role="focus"]').getByRole('button')).toContainText('Awa Ngo');

      // She adds a memory about her mother.
      await awa.goto(marieUrl);
      await awa
        .getByRole('region', { name: t(language, 'memory:profile.title') })
        .getByRole('link', { name: t(language, 'memory:profile.add') })
        .click();
      await awa.getByLabel(t(language, 'memory:form.titleLabel')).fill(story.title);
      await awa.getByLabel(t(language, 'memory:form.contentLabel')).fill(story.text);
      await awa.getByRole('button', { name: t(language, 'memory:form.publish') }).click();
      await expect(awa).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);

      // The ADMIN reads on Family Home that Awa joined, and her memory, which leads to it.
      await page.goto(familyUrl);
      await expect(
        activity(page, language).getByText(
          t(language, 'family:home.activity.lines.INVITATION_ACCEPTED', { member: 'Awa Ngo' }),
        ),
      ).toBeVisible();
      const memoryLine = activity(page, language).getByRole('link', {
        name: t(language, 'family:home.activity.lines.MEMORY_CREATED', {
          actor: 'Awa Ngo',
          title: story.title,
        }),
      });
      await expect(memoryLine).toBeVisible();
      await expectScreen(page, language);
      await memoryLine.click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(story.title);

      // The ADMIN invites the cousin by email, `Read only`: the email arrives in their language.
      const cousin = newUser();
      await page.goto(membersUrl);
      await page.getByRole('link', { name: t(language, 'family:members.invite') }).click();
      await page
        .getByRole('combobox', { name: t(language, 'invitation:invite.channel') })
        .selectOption('EMAIL');
      await page.getByLabel(t(language, 'invitation:invite.email')).fill(cousin.email);
      await page
        .getByRole('combobox', { name: t(language, 'invitation:invite.role') })
        .selectOption('VIEWER');
      await expectScreen(page, language);
      await page
        .getByRole('button', { name: t(language, 'invitation:invite.submitEmail') })
        .click();
      await expect(
        page.getByRole('heading', { name: t(language, 'invitation:email.sent') }),
      ).toBeVisible();
      await expectScreen(page, language);
      const email = await lastEmailTo(request, cousin.email);
      expect(email.Subject).toBe(
        language === 'fr'
          ? `${ADMIN} vous invite dans la famille ${familyName} sur Mbia`
          : `${ADMIN} invites you to the ${familyName} family on Mbia`,
      );
      const cousinLink = String(/https?:\/\/\S+\/invitations\/[\w-]{43}/.exec(email.Text)?.[0]);

      // The cousin joins, says `No` to his uncle, finds himself by his parent and links himself.
      const cousinContext = await open(browser);
      const cousinSignedOut = await cousinContext.newPage();
      await cousinSignedOut.goto(cousinLink);
      await cousinSignedOut
        .getByRole('button', { name: t(language, 'invitation:join.submit') })
        .click();
      await openRegistration(cousinSignedOut);
      const paul = await registerAndVerifyInAnotherTab(cousinSignedOut, request, cousin, {
        firstName: 'Paul',
        lastName: 'Ngo',
      });
      await expect(paul.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:onboarding.inTreeTitle'),
      );
      const uncle = sonOf(language, 'Marie Ngo');
      const himself = sonOf(language, 'Rose Ngo');
      const candidates = await findPaul(paul, language);
      await expect(candidates).toHaveCount(2);
      await expect(candidates.filter({ hasText: uncle })).toHaveCount(1);
      await expect(candidates.filter({ hasText: himself })).toHaveCount(1);
      await expectScreen(paul, language);
      await candidates.filter({ hasText: uncle }).click();
      await expect(paul.getByRole('heading', { level: 1 })).toHaveText(
        t(language, 'invitation:onboarding.areYou', { name: 'Paul Ngo' }),
      );
      await expect(paul.getByText(uncle)).toBeVisible();
      await expectScreen(paul, language);
      await paul.getByRole('button', { name: t(language, 'invitation:onboarding.no') }).click();
      await (await findPaul(paul, language)).filter({ hasText: himself }).click();
      await expect(paul.getByText(himself)).toBeVisible();
      await paul.getByRole('button', { name: t(language, 'invitation:onboarding.yes') }).click();
      await expect(
        paul.getByRole('region', {
          name: t(language, 'family:home.welcome.title', { name: familyName }),
        }),
      ).toBeVisible();

      // Members: Awa "your mother", the cousin `Read only`, then `Can contribute` (SCREEN-008).
      await page.goto(membersUrl);
      const awaRow = memberRow(page, language, 'Awa Ngo');
      await expect(awaRow.getByText(t(language, 'person:kinship.label.MOTHER'))).toBeVisible();
      const cousinRole = memberRow(page, language, 'Paul Ngo').getByRole('combobox', {
        name: t(language, 'family:members.roleOf', { name: 'Paul Ngo' }),
      });
      await expect(cousinRole).toHaveValue('VIEWER');
      await expect(cousinRole.locator('option:checked')).toHaveText(
        t(language, 'invitation:invite.roles.VIEWER'),
      );
      await expectScreen(page, language);
      const patched = page.waitForResponse(
        (response) => response.request().method() === 'PATCH' && response.ok(),
      );
      await cousinRole.selectOption('CONTRIBUTOR');
      await patched;
      await page.reload();
      await expect(cousinRole).toHaveValue('CONTRIBUTOR');

      // The cousin leaves: he lands on Family creation; his Person stays, no longer linked.
      await paul.goto(membersUrl);
      await expectScreen(paul, language);
      await paul.getByRole('button', { name: t(language, 'family:members.leave') }).click();
      const leave = paul.getByRole('dialog', { name: t(language, 'family:members.leaveConfirm') });
      await expect(leave.getByText(t(language, 'family:members.leaveHelp'))).toBeVisible();
      await expectScreen(paul, language);
      await leave.getByRole('button', { name: t(language, 'family:members.leaveAction') }).click();
      await expect(paul).toHaveURL(/\/families\/new$/);
      await cousinContext.close();
      await page.goto(cousinUrl);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Paul Ngo');
      await expect(
        page.getByRole('link', {
          name: t(language, 'invitation:profile.invite', { name: 'Paul' }),
        }),
      ).toBeVisible();

      // The ADMIN cannot leave, and reads why.
      await page.goto(membersUrl);
      await expect(memberList(page, language).getByRole('listitem')).toHaveCount(2);
      await expect(page.getByText(t(language, 'family:members.onlyAdmin'))).toBeVisible();
      await expect(
        page.getByRole('button', { name: t(language, 'family:members.leave') }),
      ).toHaveCount(0);

      // A revoked link: created, then revoked from Members.
      await page.getByRole('link', { name: t(language, 'family:members.invite') }).click();
      await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
      const revokedLink = String(await page.getByText(LINK).textContent());
      await page.goto(membersUrl);
      const sharedLink = t(language, 'family:members.invitations.sharedLink');
      await page
        .getByRole('button', {
          name: t(language, 'family:members.invitations.revokeFor', { target: sharedLink }),
        })
        .click();
      await expectScreen(page, language);
      await page
        .getByRole('dialog')
        .getByRole('button', { name: t(language, 'family:members.invitations.revokeAction') })
        .click();
      await expect(
        page.getByRole('region', { name: t(language, 'family:members.invitations.title') }),
      ).toHaveCount(0);

      // Used, revoked and expired links explain themselves and suggest asking for a new one. No
      // link can expire during a test (14 days, mvp.md §18): the server's answer to an expired one
      // is played by the browser (PR-57, decided by the human); the expiry itself is tested by the
      // backend.
      await awaContext.close();
      const visitorContext = await open(browser);
      const visitor = await visitorContext.newPage();
      await expectInvalid(visitor, language, awaLink, 'INVITATION_ALREADY_USED');
      await expectInvalid(visitor, language, revokedLink, 'INVITATION_REVOKED');
      await visitor.route('**/api/v1/invitations/*', (route) =>
        route.fulfill({
          status: 410,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'about:blank',
            title: 'Gone',
            status: 410,
            code: 'INVITATION_EXPIRED',
          }),
        }),
      );
      await expectInvalid(
        visitor,
        language,
        awaLink.replace(/[\w-]+$/, 'e'.repeat(43)),
        'INVITATION_EXPIRED',
      );
      await visitorContext.close();
    });
  });
}

const stories = {
  fr: {
    title: 'Le marché de Mokolo',
    text: 'Maman Marie y vendait le plantain chaque samedi.',
  },
  en: {
    title: 'The Mokolo market',
    text: 'Mother Marie sold plantain there every Saturday.',
  },
} satisfies Record<Language, { title: string; text: string }>;

/** From the current profile, `Add a relative` of this kind, first name and the family's last name. */
async function addRelative(
  page: Page,
  language: Language,
  relation: 'MOTHER' | 'SON' | 'DAUGHTER',
  first: string,
) {
  await page.getByRole('button', { name: t(language, 'person:relative.menu') }).click();
  await page
    .getByRole('link', { name: t(language, `person:relative.choices.${relation}`) })
    .click();
  await firstName(page, language).fill(first);
  await lastName(page, language).fill('Ngo');
  await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
  // The cousin has his uncle's name: a possible duplicate, created anyway.
  const duplicate = page.getByRole('button', {
    name: t(language, 'person:duplicate.createAnyway'),
  });
  const added = page.getByRole('status').first();
  await expect(duplicate.or(added)).toBeVisible();
  if (await duplicate.isVisible()) await duplicate.click();
  await expect(added).toContainText(`${first} Ngo`);
}

/** The profile address of a relative listed on the current profile; the last one of that name. */
async function relativeUrl(
  page: Page,
  language: Language,
  list: 'parents' | 'children',
  name: string,
) {
  const link = page
    .getByRole('list', { name: t(language, `person:profile.relatives.${list}`) })
    .getByRole('link', { name: new RegExp(`^${name}`) })
    .last();
  return new URL(String(await link.getAttribute('href')), page.url()).toString();
}

/** "Are you already in this tree?": the Persons found for "Paul". */
async function findPaul(page: Page, language: Language) {
  await page
    .getByRole('searchbox', { name: t(language, 'invitation:onboarding.searchLabel') })
    .fill('Paul');
  return page.getByRole('list', { name: t(language, 'person:search.results') }).getByRole('button');
}

function sonOf(language: Language, parent: string) {
  return t(language, 'person:kinship.step.CHILD.male', { to: 'Paul Ngo', from: parent });
}

function activity(page: Page, language: Language) {
  return page.getByRole('region', { name: t(language, 'family:home.activity.title') });
}

function memberList(page: Page, language: Language) {
  return page.getByRole('list', { name: t(language, 'family:members.title') });
}

function memberRow(page: Page, language: Language, name: string): Locator {
  return memberList(page, language).getByRole('listitem').filter({ hasText: name });
}

async function expectInvalid(page: Page, language: Language, link: string, code: string) {
  await page.goto(link);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    t(language, `invitation:join.invalid.${code}`),
  );
  await expect(page.getByText(t(language, 'invitation:join.invalid.askAgain'))).toBeVisible();
  await expectScreen(page, language);
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}

function lastName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.lastName'), { exact: true });
}

/**
 * design-guidelines.md §9 on the screen (names, touch targets, language, text size, contrast,
 * keyboard), and a page that never scrolls sideways at phone width.
 */
async function expectScreen(page: Page, language: Language) {
  await expectAccessibleControls(page);
  await expectReadableScreen(page, language);
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
