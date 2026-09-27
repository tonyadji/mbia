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
const LINK = /^https?:\/\/\S+\/invitations\/[\w-]+$/;
const ADMIN = 'E2E Tester';

/**
 * PR-55 (phase-5-collaboration.md): the recent activity of Family Home (SCREEN-002, mvp.md §20,
 * OQ-054). A new Family shows no activity section; the ADMIN adds three people in a row, which is
 * one line; archives one, named without link; adds their mother, a line that leads to her profile;
 * a relative invited `Read only` joins, and reads the same feed with their own arrival. At phone
 * width, in French and English.
 */
for (const { language, locale } of [
  { language: 'fr', locale: 'fr-FR' },
  { language: 'en', locale: 'en-US' },
] satisfies { language: Language; locale: string }[]) {
  test.describe(`recent activity (${language})`, () => {
    test.use({ locale, viewport: PHONE });

    test('the family reads what happened recently', async ({ page, browser, request, baseURL }) => {
      await page.goto('/');
      await page.getByRole('button', { name: t(language, 'auth:welcome.createFamily') }).click();
      await registerAndVerify(page, request, newUser());
      const familyName = `E2E ${language} activity`;
      await page.getByLabel(t(language, 'family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: t(language, 'family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const familyUrl = page.url();

      // Nothing happened yet: no section.
      await expect(page.getByText(t(language, 'family:home.emptyBody'))).toBeVisible();
      await expect(activity(page, language)).toHaveCount(0);

      // Three people added in a row: one line, without link.
      await page.getByRole('link', { name: t(language, 'family:home.startWithMe') }).click();
      await firstName(page, language).fill('Alice');
      await page.getByRole('button', { name: t(language, 'person:form.submitMe') }).click();
      const profiles: string[] = [];
      for (const name of ['Paul', 'Rose']) {
        await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
        await page
          .getByRole('link', { name: t(language, 'family:home.relatives.someoneElse') })
          .click();
        await firstName(page, language).fill(name);
        await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
        const profile = page.getByRole('link', { name: t(language, 'family:home.viewProfile') });
        profiles.push(String(await profile.getAttribute('href')));
      }
      const added = lineText(language, 'groups.PERSON_CREATED', { actor: ADMIN, count: '3' });
      await expect(lines(page, language)).toHaveCount(1);
      await expect(activity(page, language).getByText(added)).toBeVisible();
      await expect(activity(page, language).getByRole('link')).toHaveCount(0);
      await expect(activity(page, language).locator('time')).toHaveText(
        language === 'fr' ? 'maintenant' : 'now',
      );

      // Paul archived: named, without link.
      await page.goto(String(profiles[0]));
      await page.getByRole('button', { name: t(language, 'person:archive.action') }).click();
      await page
        .getByRole('button', { name: t(language, 'person:archive.confirm'), exact: true })
        .click();
      await expect(page.getByText(t(language, 'person:archived.notice'))).toBeVisible();
      await page.goto(familyUrl);
      const archived = lineText(language, 'lines.PERSON_ARCHIVED', { actor: ADMIN, name: 'Paul' });
      await expect(lines(page, language).first()).toHaveText(new RegExp(`^${escape(archived)}`));
      await expect(activity(page, language).getByRole('link')).toHaveCount(0);

      // Their mother: a line that leads to her profile, and the link, named without link.
      await page.getByRole('button', { name: t(language, 'family:home.addRelative') }).click();
      await page.getByRole('link', { name: t(language, 'family:home.relatives.MOTHER') }).click();
      await firstName(page, language).fill('Awa');
      await page.getByRole('button', { name: t(language, 'person:form.submit') }).click();
      await expect(page.getByRole('status')).toContainText(
        t(language, 'person:relative.added', { name: 'Awa', anchor: 'Alice' }),
      );
      await page.goto(familyUrl);
      await expect(lines(page, language).first()).toHaveText(
        new RegExp(
          `^${escape(lineText(language, 'lines.RELATIONSHIP_CREATED', { actor: ADMIN, source: 'Awa', target: 'Alice' }))}`,
        ),
      );
      const awaLine = activity(page, language).getByRole('link', {
        name: lineText(language, 'lines.PERSON_CREATED', { actor: ADMIN, name: 'Awa' }),
      });
      await expectAccessibleControls(page);
      await expectNoHorizontalScroll(page);
      await awaLine.click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa');

      // A relative invited `Read only` joins, and reads the feed with their arrival.
      await page.goto(familyUrl);
      await page
        .getByRole('navigation', { name: t(language, 'family:navigation.label') })
        .getByRole('link', { name: t(language, 'family:navigation.members') })
        .click();
      await page.getByRole('link', { name: t(language, 'family:members.invite') }).click();
      await page
        .getByRole('combobox', { name: t(language, 'invitation:invite.role') })
        .selectOption('VIEWER');
      await page.getByRole('button', { name: t(language, 'invitation:invite.submit') }).click();
      const invitation = String(await page.getByText(LINK).textContent());

      const context = await browser.newContext({ locale, viewport: PHONE, baseURL });
      const signedOut = await context.newPage();
      await signedOut.goto(invitation);
      await signedOut.getByRole('button', { name: t(language, 'invitation:join.submit') }).click();
      await openRegistration(signedOut);
      const viewer = await registerAndVerifyInAnotherTab(signedOut, request, newUser());
      await expect(viewer.getByRole('heading', { level: 1 })).toBeVisible();
      await viewer.goto(familyUrl);
      const joined = lineText(language, 'lines.INVITATION_ACCEPTED', { member: 'Awa Ngo' });
      await expect(lines(viewer, language).first()).toHaveText(new RegExp(`^${escape(joined)}`));
      await expect(activity(viewer, language).getByText(added)).toBeVisible();
      await expect(activity(viewer, language).getByRole('link')).toHaveCount(1);
      await expectNoHorizontalScroll(viewer);
      await context.close();
    });
  });
}

function activity(page: Page, language: Language) {
  return page.getByRole('region', { name: t(language, 'family:home.activity.title') });
}

function lines(page: Page, language: Language) {
  return activity(page, language).getByRole('listitem');
}

function lineText(language: Language, key: string, values: Record<string, string>) {
  return t(language, `family:home.activity.${key}`, values);
}

function firstName(page: Page, language: Language) {
  return page.getByLabel(t(language, 'person:form.firstName'), { exact: true });
}

function escape(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate<number>(
    'document.documentElement.scrollWidth - document.documentElement.clientWidth',
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
