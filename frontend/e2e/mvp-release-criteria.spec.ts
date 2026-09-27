import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { t } from './support/i18n';
import {
  newUser,
  openRegistration,
  registerAndVerify,
  registerAndVerifyInAnotherTab,
  signInOnKeycloak,
} from './support/keycloak';

/** A PNG photo of the Phase 3 fixtures (phase-3-family-memories.md §3.8). */
const PHOTO = fileURLToPath(
  new URL('../../backend/src/test/resources/media/gps.png', import.meta.url),
);
const PHONE = { width: 375, height: 812 };
const LINK = /^https?:\/\/\S+\/invitations\/[\w-]+$/;
/** Local test user of the realm (infrastructure/keycloak/README.md), language `en`. */
const BOB = { email: 'bob@mbia.local', password: 'bob-local-1' };

/**
 * North star: the MVP release journey of mbia-specs/product/mvp.md §28, played once in order by a
 * real family, one step per line of §28, then "Cross-Family access must fail" played by two real
 * members of two Families. The MVP is ready when it passes (PR-57).
 */
test.describe('MVP release criteria (mvp.md §28)', () => {
  test.describe.configure({ mode: 'serial' });
  test.use({ locale: 'fr-FR', viewport: PHONE });

  test('a real family from sign-up to the relative’s contribution, isolated from another family', async ({
    page,
    browser,
    request,
    baseURL,
  }) => {
    test.setTimeout(240_000);
    const fr = (key: string, values?: Record<string, string>) => t('fr', key, values);
    let familyUrl = '';
    let familyName = '';
    let awaUrl = '';
    let marieUrl = '';
    let storyUrl = '';
    let invitation = '';
    let relative: Page | undefined;

    await test.step('1. sign up', async () => {
      await page.goto('/');
      await page.getByRole('button', { name: fr('auth:welcome.createFamily') }).click();
      const user = newUser();
      await registerAndVerify(page, request, user);
      familyName = `E2E MVP ${user.email.slice(4, 12)}`;
      await expect(page.getByLabel(fr('family:create.nameLabel'))).toBeVisible();
    });

    await test.step('2. create Family', async () => {
      await page.getByLabel(fr('family:create.nameLabel')).fill(familyName);
      await page.getByRole('button', { name: fr('family:create.submit') }).click();
      await expect(page).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(familyName);
      familyUrl = page.url();
    });

    await test.step('3. create own Person', async () => {
      await page.getByRole('link', { name: fr('family:home.startWithMe') }).click();
      await firstName(page).fill('Alice');
      await page.getByRole('button', { name: fr('person:form.submitMe') }).click();
      await expect(page).toHaveURL(familyUrl);
    });

    await test.step('4. add parents', async () => {
      for (const [relation, name] of [
        ['FATHER', 'Paul'],
        ['MOTHER', 'Awa'],
      ] as const) {
        await page.getByRole('button', { name: fr('family:home.addRelative') }).click();
        await page.getByRole('link', { name: fr(`family:home.relatives.${relation}`) }).click();
        await firstName(page).fill(name);
        await lastName(page).fill('Ngo');
        await page.getByRole('button', { name: fr('person:form.submit') }).click();
        await expect(page).toHaveURL(familyUrl);
      }
      await page.getByRole('link', { name: fr('family:home.viewProfile') }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Awa Ngo');
      awaUrl = page.url();
    });

    await test.step('5. view tree', async () => {
      await page.goto(familyUrl);
      await page.getByRole('link', { name: fr('family:home.viewTree') }).click();
      await expect(card(page, 'focus')).toContainText('Alice');
      await expect(card(page, 'parent')).toHaveCount(2);
    });

    await test.step('6. add grandparent', async () => {
      await page.goto(awaUrl);
      await page.getByRole('button', { name: fr('person:relative.menu') }).click();
      await page.getByRole('link', { name: fr('person:relative.choices.MOTHER') }).click();
      await firstName(page).fill('Marie');
      await lastName(page).fill('Ngo');
      await page.getByRole('button', { name: fr('person:form.submit') }).click();
      await expect(page.getByRole('status').first()).toContainText('Marie Ngo');
      const marie = page
        .getByRole('list', { name: fr('person:profile.relatives.parents') })
        .getByRole('link', { name: /^Marie Ngo/ });
      marieUrl = new URL(String(await marie.getAttribute('href')), page.url()).toString();
    });

    await test.step('7. see derived kinship', async () => {
      await page.goto(familyUrl);
      await page.getByRole('link', { name: fr('family:home.viewTree') }).click();
      await card(page, 'parent').filter({ hasText: 'Awa' }).click();
      await page
        .getByRole('dialog', { name: 'Awa Ngo' })
        .getByRole('button', { name: fr('tree:quickView.center', { name: 'Awa Ngo' }) })
        .click();
      await card(page, 'parent').filter({ hasText: 'Marie' }).click();
      await expect(page.getByRole('dialog', { name: 'Marie Ngo' })).toContainText(
        fr('person:kinship.label.GRANDMOTHER'),
      );
      await page.keyboard.press('Escape');
    });

    await test.step('8. add a Memory with a photo and its story', async () => {
      await page.goto(marieUrl);
      await page
        .getByRole('region', { name: fr('memory:profile.title') })
        .getByRole('link', { name: fr('memory:profile.add') })
        .click();
      await page.getByLabel(fr('memory:form.titleLabel')).fill('Le marché de Mokolo');
      await page
        .getByLabel(fr('memory:form.contentLabel'))
        .fill('Grand-mère y vendait le plantain chaque samedi.');
      await page.getByTestId('memory-photo-input').setInputFiles(PHOTO);
      await expect(
        page.getByRole('img', { name: fr('memory:form.photos.thumbnail', { position: '1' }) }),
      ).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: fr('memory:form.publish') }).click();
      await expect(page).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Le marché de Mokolo');
      await expect(page.getByText('Grand-mère y vendait le plantain chaque samedi.')).toBeVisible();
      await expect(
        page.getByRole('img', {
          name: fr('memory:screen.photos.alt', { n: '1', total: '1' }),
        }),
      ).toBeVisible();
      storyUrl = page.url();
    });

    await test.step('9. invite relative', async () => {
      await page.goto(awaUrl);
      await page
        .getByRole('link', { name: fr('invitation:profile.invite', { name: 'Awa' }) })
        .click();
      await page.getByRole('button', { name: fr('invitation:invite.submit') }).click();
      invitation = String(await page.getByText(LINK).textContent());
    });

    await test.step('10. relative joins', async () => {
      const context = await browser.newContext({ locale: 'fr-FR', viewport: PHONE, baseURL });
      const signedOut = await context.newPage();
      await signedOut.goto(invitation);
      await expect(signedOut.getByRole('heading', { level: 1 })).toHaveText(
        fr('invitation:join.title', { family: familyName }),
      );
      await signedOut.getByRole('button', { name: fr('invitation:join.submit') }).click();
      await openRegistration(signedOut);
      relative = await registerAndVerifyInAnotherTab(signedOut, request, newUser());
      await expect(relative.getByRole('heading', { level: 1 })).toHaveText(
        fr('invitation:onboarding.areYou', { name: 'Awa Ngo' }),
      );
    });

    await test.step('11. relative links themselves to existing Person', async () => {
      const awa = defined(relative);
      await awa.getByRole('button', { name: fr('invitation:onboarding.yes') }).click();
      const welcome = awa.getByRole('region', {
        name: fr('family:home.welcome.title', { name: familyName }),
      });
      await expect(welcome).toBeVisible();
      // Linked: the tree is centred on her.
      await welcome.getByRole('link', { name: fr('family:home.welcome.viewTree') }).click();
      await expect(card(awa, 'focus')).toContainText('Awa Ngo');
    });

    await test.step('12. relative contributes', async () => {
      const awa = defined(relative);
      await awa.goto(marieUrl);
      await awa
        .getByRole('region', { name: fr('memory:profile.title') })
        .getByRole('link', { name: fr('memory:profile.add') })
        .click();
      await awa.getByLabel(fr('memory:form.titleLabel')).fill('Les dimanches chez maman');
      await awa.getByLabel(fr('memory:form.contentLabel')).fill('Elle cuisinait le ndolé.');
      await awa.getByRole('button', { name: fr('memory:form.publish') }).click();
      await expect(awa).toHaveURL(/\/memories\/[0-9a-f-]{36}$/);
      const contribution = awa.url();

      // The ADMIN reads it.
      await page.goto(contribution);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Les dimanches chez maman');
    });

    await test.step('Cross-Family access must fail', async () => {
      const awa = defined(relative);
      // Bob, a real member of his own Family B, with his own Person.
      const bobContext = await browser.newContext({ locale: 'en-US', viewport: PHONE, baseURL });
      const bob = await bobContext.newPage();
      await bob.goto('/');
      await bob.getByRole('button', { name: t('en', 'auth:welcome.signIn') }).click();
      await signInOnKeycloak(bob, BOB);
      await expect(bob).toHaveURL(/localhost:5173\/(home|families)/);
      await bob.goto('/families/new');
      const bobFamily = `E2E Bob ${String(Date.now())}`;
      await bob.getByLabel(t('en', 'family:create.nameLabel')).fill(bobFamily);
      await bob.getByRole('button', { name: t('en', 'family:create.submit') }).click();
      await expect(bob).toHaveURL(/\/families\/[0-9a-f-]{36}$/);
      const bobFamilyUrl = bob.url();
      await bob.getByRole('link', { name: t('en', 'family:home.startWithMe') }).click();
      await bob.getByLabel(t('en', 'person:form.firstName'), { exact: true }).fill('Bobby');
      await bob.getByRole('button', { name: t('en', 'person:form.submitMe') }).click();
      await expect(bob).toHaveURL(bobFamilyUrl);
      await bob.getByRole('link', { name: t('en', 'family:home.viewTree') }).click();
      await bob.locator('[data-role="focus"]').getByRole('button').click();
      await bob.getByRole('link', { name: t('en', 'tree:quickView.viewProfile') }).click();
      await expect(bob.getByRole('heading', { level: 1 })).toHaveText('Bobby');
      const bobbyUrl = bob.url();

      // The relative, a real member of Family A, reaches nothing of Family B.
      for (const url of [
        bobFamilyUrl,
        `${bobFamilyUrl}/tree`,
        bobbyUrl,
        `${bobFamilyUrl}/members`,
      ]) {
        await awa.goto(url);
        await expect(awa.getByRole('heading', { name: fr('family:notFound.title') })).toBeVisible();
        await expect(awa.getByText(bobFamily)).toHaveCount(0);
        await expect(awa.getByText('Bobby')).toHaveCount(0);
      }

      // Bob reaches nothing of Family A.
      for (const url of [
        familyUrl,
        `${familyUrl}/tree`,
        awaUrl,
        storyUrl,
        `${familyUrl}/members`,
      ]) {
        await bob.goto(url);
        await expect(
          bob.getByRole('heading', { name: t('en', 'family:notFound.title') }),
        ).toBeVisible();
        await expect(bob.getByText(familyName)).toHaveCount(0);
        await expect(bob.getByText('Awa')).toHaveCount(0);
        await expect(bob.getByText('Mokolo')).toHaveCount(0);
      }
      await bobContext.close();
    });
  });
});

function defined(page: Page | undefined): Page {
  if (page === undefined) throw new Error('The relative has not joined');
  return page;
}

function card(page: Page, role: 'parent' | 'focus') {
  return page.locator(`[data-role="${role}"]`).getByRole('button');
}

function firstName(page: Page) {
  return page.getByLabel(t('fr', 'person:form.firstName'), { exact: true });
}

function lastName(page: Page) {
  return page.getByLabel(t('fr', 'person:form.lastName'), { exact: true });
}
