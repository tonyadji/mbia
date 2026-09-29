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
import {
  addPersonOnTheWay,
  publishStory,
  stripEntry,
  writeStory,
  yearStrip,
} from './support/story';

/** A PNG photo of the Phase 3 fixtures (phase-3-family-memories.md §3.8). */
const PHOTO = fileURLToPath(
  new URL('../../backend/src/test/resources/media/gps.png', import.meta.url),
);
const PHONE = { width: 375, height: 812 };
const LINK = /^https?:\/\/\S+\/invitations\/[\w-]+$/;
/** Local test user of the realm (infrastructure/keycloak/README.md), language `en`. */
const BOB = { email: 'bob@mbia.local', password: 'bob-local-1' };

/**
 * North star: the MVP release journey of mbia-specs/product/mvp.md §28 (the family story first),
 * played once in order by a real family, one step per line of §28, then "Cross-Family access must
 * fail" played by two real members of two Families, the family story included. The MVP is ready
 * when it passes (PR-57, PR-63).
 */
test.describe('MVP release criteria (mvp.md §28)', () => {
  test.describe.configure({ mode: 'serial' });
  test.use({ locale: 'fr-FR', viewport: PHONE });

  test('a real family from sign-up to derived kinship, its story included, isolated from another family', async ({
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

    await test.step('3. tell a first Memory about a Person created on the way, with a photo, its story and its year', async () => {
      await page.getByRole('link', { name: fr('family:home.tellFirstMemory') }).click();
      const subject = page.getByRole('group', { name: fr('memory:form.subject.question') });
      await subject
        .getByRole('button', { name: fr('memory:form.subject.me'), exact: true })
        .click();
      await subject.getByRole('textbox', { name: fr('person:form.firstName') }).fill('Alice');
      await writeStory(page, 'fr', {
        title: 'Mon premier vélo',
        text: 'Papa me tenait par la selle.',
        date: { year: '1975' },
      });
      await page.getByTestId('memory-photo-input').setInputFiles(PHOTO);
      await expect(
        page.getByRole('img', { name: fr('memory:form.photos.thumbnail', { position: '1' }) }),
      ).toBeVisible({ timeout: 15_000 });
      await publishStory(page, 'fr');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Mon premier vélo');
      await expect(page.getByText('Papa me tenait par la selle.')).toBeVisible();
      await expect(
        page.getByText(fr('memory:screen.happenedIn', { year: '1975' }), { exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole('img', { name: fr('memory:screen.photos.alt', { n: '1', total: '1' }) }),
      ).toBeVisible();
      await expect(page.getByRole('link', { name: /Alice/ })).toBeVisible();
    });

    await test.step('4. add people', async () => {
      // Marie and Awa are not in the tree yet: added on the way, with a memory of 12 March 1962.
      await page.goto(familyUrl);
      await tellMemory(page).click();
      await writeStory(page, 'fr', {
        title: 'Le marché de Mokolo',
        text: 'Grand-mère y vendait le plantain chaque samedi.',
        date: { exact: '1962-03-12' },
      });
      await page
        .getByRole('button', { name: fr('memory:form.removePerson', { name: 'Alice' }) })
        .click();
      await addPersonOnTheWay(page, 'fr', 'Marie Ngo');
      await addPersonOnTheWay(page, 'fr', 'Awa Ngo');
      storyUrl = await publishStory(page, 'fr');
      marieUrl = await personLink(page, 'Marie Ngo');
      awaUrl = await personLink(page, 'Awa Ngo');
      await page.goto(familyUrl);
      await expect(
        page.getByText(fr('family:home.personCount_other', { count: '3' }), { exact: true }),
      ).toBeVisible();
    });

    await test.step('5. add memories', async () => {
      await page.goto(marieUrl);
      await page
        .getByRole('region', { name: fr('memory:profile.title') })
        .getByRole('link', { name: fr('memory:profile.add') })
        .click();
      await writeStory(page, 'fr', {
        title: 'La recette du ndolé',
        text: 'Elle ne l’a jamais écrite.',
      });
      await publishStory(page, 'fr');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('La recette du ndolé');
    });

    await test.step('6. view the family story, and what happened in a year', async () => {
      await page.goto(familyUrl);
      const entries = yearStrip(page, 'fr').getByRole('link');
      await expect(entries).toHaveText([
        stripEntry('fr', '1962', 1),
        stripEntry('fr', '1975', 1),
        stripEntry('fr', 'undated', 1),
      ]);
      await entries.first().click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        fr('memory:story.yearTitle', { year: '1962' }),
      );
      await expect(page.getByRole('link', { name: /Le marché de Mokolo/ })).toBeVisible();
    });

    await test.step('7. invite relative', async () => {
      await page.goto(awaUrl);
      await page
        .getByRole('link', { name: fr('invitation:profile.invite', { name: 'Awa' }) })
        .click();
      await page.getByRole('button', { name: fr('invitation:invite.submit') }).click();
      invitation = String(await page.getByText(LINK).textContent());
    });

    await test.step('8. relative joins', async () => {
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

    await test.step('9. relative links themselves to existing Person', async () => {
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

    await test.step('10. relative contributes a Memory, which appears in the family story', async () => {
      const awa = defined(relative);
      await awa.goto(familyUrl);
      await tellMemory(awa).click();
      await writeStory(awa, 'fr', {
        title: 'Les dimanches chez maman',
        text: 'Elle cuisinait le ndolé.',
        date: { year: '1980' },
      });
      const contribution = await publishStory(awa, 'fr');

      // The ADMIN finds it in the family story, in 1980.
      await page.goto(familyUrl);
      await expect(yearStrip(page, 'fr').getByRole('link')).toHaveText([
        stripEntry('fr', '1962', 1),
        stripEntry('fr', '1975', 1),
        stripEntry('fr', '1980', 1),
        stripEntry('fr', 'undated', 1),
      ]);
      await yearStrip(page, 'fr')
        .getByRole('link', { name: stripEntry('fr', '1980', 1) })
        .click();
      await page.getByRole('link', { name: /Les dimanches chez maman/ }).click();
      await expect(page).toHaveURL(contribution);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Les dimanches chez maman');
    });

    await test.step('11. create relationships (parents, a grandparent)', async () => {
      // Marie, Awa's mother: my grandmother.
      await page.goto(awaUrl);
      await page.getByRole('button', { name: fr('person:relative.menu') }).click();
      await page
        .getByRole('link', { name: fr('person:relative.choices.MOTHER'), exact: true })
        .click();
      await linkExisting(page, 'Marie Ngo', 'Awa Ngo');
      // Awa, already in the Family, becomes my mother; Paul, new, my father.
      await page.goto(familyUrl);
      await page.getByRole('button', { name: fr('family:home.addRelative') }).click();
      await page.getByRole('link', { name: fr('family:home.relatives.MOTHER') }).click();
      await linkExisting(page, 'Awa Ngo', 'Alice');
      await page.getByRole('button', { name: fr('family:home.addRelative') }).click();
      await page.getByRole('link', { name: fr('family:home.relatives.FATHER') }).click();
      await firstName(page).fill('Paul');
      await lastName(page).fill('Ngo');
      await page.getByRole('button', { name: fr('person:form.submit') }).click();
      await expect(page).toHaveURL(familyUrl);
    });

    await test.step('12. view tree', async () => {
      await page.goto(familyUrl);
      await page.getByRole('link', { name: fr('family:home.viewTree') }).click();
      await expect(card(page, 'focus')).toContainText('Alice');
      await expect(card(page, 'parent')).toHaveCount(2);
    });

    await test.step('13. see derived kinship', async () => {
      await card(page, 'parent').filter({ hasText: 'Awa' }).click();
      await page
        .getByRole('dialog', { name: 'Awa Ngo' })
        .getByRole('button', { name: fr('tree:quickView.center', { name: 'Awa Ngo' }) })
        .click();
      await card(page, 'parent').filter({ hasText: 'Marie' }).click();
      // Marie was added on the way, without gender: "your grandparent"
      // (localization-and-kinship-labels.md §2, §3).
      await expect(page.getByRole('dialog', { name: 'Marie Ngo' })).toContainText(
        fr('person:kinship.label.GRANDPARENT'),
      );
      await page.keyboard.press('Escape');
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
      // Bob's Family starts with a memory of 1975 about himself.
      await bob.getByRole('link', { name: t('en', 'family:home.tellFirstMemory') }).click();
      const subject = bob.getByRole('group', { name: t('en', 'memory:form.subject.question') });
      await subject
        .getByRole('button', { name: t('en', 'memory:form.subject.me'), exact: true })
        .click();
      await subject.getByRole('textbox', { name: t('en', 'person:form.firstName') }).fill('Bobby');
      await writeStory(bob, 'en', {
        title: 'Bobby’s harbour',
        text: 'The boats left at dawn.',
        date: { year: '1975' },
      });
      await publishStory(bob, 'en');
      const bobbyUrl = await personLink(bob, 'Bobby');

      // The relative, a real member of Family A, reaches nothing of Family B.
      for (const url of [
        bobFamilyUrl,
        `${bobFamilyUrl}/tree`,
        bobbyUrl,
        `${bobFamilyUrl}/members`,
        `${bobFamilyUrl}/story/1975`,
        `${bobFamilyUrl}/story/undated`,
      ]) {
        await awa.goto(url);
        await expect(awa.getByRole('heading', { name: fr('family:notFound.title') })).toBeVisible();
        await expect(awa.getByText(bobFamily)).toHaveCount(0);
        await expect(awa.getByText('Bobby')).toHaveCount(0);
        await expect(awa.getByText('harbour')).toHaveCount(0);
      }

      // Bob reaches nothing of Family A.
      for (const url of [
        familyUrl,
        `${familyUrl}/tree`,
        awaUrl,
        storyUrl,
        `${familyUrl}/members`,
        `${familyUrl}/story/1962`,
        `${familyUrl}/story/undated`,
      ]) {
        await bob.goto(url);
        await expect(
          bob.getByRole('heading', { name: t('en', 'family:notFound.title') }),
        ).toBeVisible();
        await expect(bob.getByText(familyName)).toHaveCount(0);
        await expect(bob.getByText('Awa')).toHaveCount(0);
        await expect(bob.getByText('Mokolo')).toHaveCount(0);
        await expect(bob.getByText('ndolé')).toHaveCount(0);
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

function tellMemory(page: Page) {
  return page
    .getByRole('region', { name: t('fr', 'memory:story.title') })
    .getByRole('link', { name: t('fr', 'memory:story.tell') });
}

/** The profile address of a Person linked to the Memory shown (SCREEN-013). */
async function personLink(page: Page, name: string) {
  const link = page.getByRole('link', { name: new RegExp(`^${name}`) });
  return new URL(String(await link.getAttribute('href')), page.url()).toString();
}

/** On the add-relative screen, links a Person already in the Family (SCREEN-004). */
async function linkExisting(page: Page, name: string, anchor: string) {
  await page
    .getByRole('searchbox', { name: t('fr', 'person:relative.existing.search') })
    .fill(name.split(' ')[0] ?? name);
  await page.getByRole('button', { name: new RegExp(`^${name}`) }).click();
  await page
    .getByRole('button', { name: t('fr', 'person:relative.existing.link', { name, anchor }) })
    .click();
  await expect(page.getByRole('status').first()).toContainText(
    t('fr', 'person:relative.linked', { name, anchor }),
  );
}

function firstName(page: Page) {
  return page.getByLabel(t('fr', 'person:form.firstName'), { exact: true });
}

function lastName(page: Page) {
  return page.getByLabel(t('fr', 'person:form.lastName'), { exact: true });
}
