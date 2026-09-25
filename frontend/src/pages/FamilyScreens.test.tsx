import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number, detail = 'raw server detail') {
  return jsonResponse({ code, status, title: code, detail }, status, 'application/problem+json');
}

const me = {
  id: '0b5c1f3e-1c1a-4a57-9a55-8f2f6c1d2e01',
  email: 'alice@mbia.local',
  displayName: 'Alice',
  preferredLocale: 'fr',
};

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const NJOH_ID = '7a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d';

function family(id: string, name: string, personCount = 0, memoryCount = 0, myRole = 'ADMIN') {
  return {
    id,
    name,
    myRole,
    stats: { personCount, memoryCount, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`); `/me` is always Alice. */
function fakeApi(handlers: Record<string, Handler>) {
  const requests: Request[] = [];
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') return jsonResponse(me);
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    requests,
    familyRequests: () => requests.filter((request) => request.url.includes('/families')),
  };
}

function renderApp(path: string, userManager = fakeUserManager(fakeOidcUser())) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App router={router} queryClient={createQueryClient()} userManager={userManager} />);
  return { router, userManager };
}

describe('Family screens', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('after sign-in', () => {
    it('leads a User without a Family to Family creation', async () => {
      fakeApi({ 'GET /families': () => jsonResponse([]) });
      const { router } = renderApp('/home');

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Créer votre famille' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/families/new');
    });

    it('leads a User with one Family to its home', async () => {
      fakeApi({
        'GET /families': () => jsonResponse([family(ADJI_ID, 'ADJI')]),
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI')),
      });
      const { router } = renderApp('/home');

      expect(await screen.findByRole('heading', { level: 1, name: 'ADJI' })).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}`);
      expect(screen.getByText('0 personne')).toBeInTheDocument();
    });

    it('lets a User with several Families choose one', async () => {
      fakeApi({
        'GET /families': () =>
          jsonResponse([family(ADJI_ID, 'ADJI', 12), family(NJOH_ID, 'NJOH', 1)]),
        [`GET /families/${NJOH_ID}`]: () => jsonResponse(family(NJOH_ID, 'NJOH', 1)),
      });
      const { router } = renderApp('/home');

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Vos familles' }),
      ).toBeInTheDocument();
      const links = within(screen.getByRole('list')).getAllByRole('link');
      expect(links.map((link) => link.textContent)).toEqual(['ADJI12 personnes', 'NJOH1 personne']);

      fireEvent.click(screen.getByRole('link', { name: /NJOH/ }));

      expect(await screen.findByRole('heading', { level: 1, name: 'NJOH' })).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/families/${NJOH_ID}`);
    });
  });

  describe('Family creation', () => {
    async function openCreation() {
      const result = renderApp('/families/new');
      await screen.findByRole('heading', { level: 1, name: 'Créer votre famille' });
      return result;
    }

    function submit(name: string) {
      fireEvent.change(screen.getByRole('textbox', { name: 'Nom de la famille' }), {
        target: { value: name },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Créer la famille' }));
    }

    it('requires a name, with a translated message and no request', async () => {
      const api = fakeApi({ 'GET /families': () => jsonResponse([]) });
      await openCreation();

      submit('   ');

      expect(await screen.findByText('Indiquez le nom de la famille.')).toBeInTheDocument();
      expect(api.requests.some((request) => request.method === 'POST')).toBe(false);
    });

    it('refuses a name longer than 200 characters', async () => {
      const api = fakeApi({ 'GET /families': () => jsonResponse([]) });
      await openCreation();

      submit('A'.repeat(201));

      expect(
        await screen.findByText('Le nom ne peut pas dépasser 200 caractères.'),
      ).toBeInTheDocument();
      expect(api.requests.some((request) => request.method === 'POST')).toBe(false);
    });

    it('creates the Family with the trimmed name and opens its home', async () => {
      const created = family(ADJI_ID, 'ADJI');
      const api = fakeApi({
        'GET /families': () => jsonResponse([]),
        'POST /families': () => jsonResponse(created, 201),
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(created),
      });
      const { router } = await openCreation();

      submit('  ADJI  ');

      expect(await screen.findByRole('status')).toHaveTextContent('La famille ADJI a été créée.');
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}`);
      const post = api.requests.find((request) => request.method === 'POST');
      expect(await post?.json()).toEqual({ name: 'ADJI' });
      expect(
        screen.getByRole('heading', { level: 2, name: 'Bienvenue dans la famille ADJI' }),
      ).toBeInTheDocument();
    });

    it('translates a server rejection and never shows the raw message', async () => {
      fakeApi({
        'GET /families': () => jsonResponse([]),
        'POST /families': () => problemResponse('VALIDATION_FAILED', 400),
      });
      await openCreation();

      submit('ADJI');

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Certaines informations ne sont pas valides. Vérifiez-les puis réessayez.',
      );
      expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
    });
  });

  describe('Family home (SCREEN-002)', () => {
    it.each(['ADMIN', 'CONTRIBUTOR'])(
      'welcomes a Family without Persons with both add actions for %s, in French then English',
      async (role) => {
        fakeApi({
          [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', 0, 0, role)),
        });
        renderApp(`/families/${ADJI_ID}`);

        expect(
          await screen.findByRole('heading', { level: 2, name: 'Bienvenue dans la famille ADJI' }),
        ).toBeInTheDocument();
        expect(screen.getByText('Ajoutons la première personne.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Commencer par moi' })).toHaveAttribute(
          'href',
          `/families/${ADJI_ID}/persons/new?mode=me`,
        );
        expect(screen.getByRole('link', { name: "Ajouter quelqu'un d'autre" })).toHaveAttribute(
          'href',
          `/families/${ADJI_ID}/persons/new`,
        );
        expect(screen.queryByRole('status')).not.toBeInTheDocument();

        await act(() => i18n.changeLanguage('en'));

        expect(
          screen.getByRole('heading', { level: 2, name: 'Welcome to the ADJI family' }),
        ).toBeInTheDocument();
        expect(screen.getByText("Let's add the first person.")).toBeInTheDocument();
        expect(screen.getByText('0 people')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Start with me' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add someone else' })).toBeInTheDocument();
      },
    );

    it('shows only the explanatory text to a VIEWER of a Family without Persons', async () => {
      fakeApi({
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', 0, 0, 'VIEWER')),
      });
      renderApp(`/families/${ADJI_ID}`);

      expect(await screen.findByText('Ajoutons la première personne.')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Commencer par moi' })).not.toBeInTheDocument();
      expect(
        screen.queryByRole('link', { name: "Ajouter quelqu'un d'autre" }),
      ).not.toBeInTheDocument();
    });

    it('shows the Person count and Add a person once Persons exist, without Memories', async () => {
      fakeApi({
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', 3, 1)),
      });
      renderApp(`/families/${ADJI_ID}`);

      expect(await screen.findByText('3 personnes')).toBeInTheDocument();
      expect(screen.queryByText(/souvenir/)).not.toBeInTheDocument();
      expect(screen.queryByText('Ajoutons la première personne.')).not.toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Ajouter une personne' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/new`,
      );
      expect(screen.queryByRole('link', { name: 'Commencer par moi' })).not.toBeInTheDocument();
    });

    it('does not offer Add a person to a VIEWER', async () => {
      fakeApi({
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', 3, 0, 'VIEWER')),
      });
      renderApp(`/families/${ADJI_ID}`);

      expect(await screen.findByText('3 personnes')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Ajouter une personne' })).not.toBeInTheDocument();
    });

    it('has a navigation bar with Home and Tree, and opens settings from the avatar', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI')) });
      const { router } = renderApp(`/families/${ADJI_ID}`);
      await screen.findByRole('heading', { level: 1, name: 'ADJI' });

      const navigation = screen.getByRole('navigation', {
        name: 'Navigation de la famille',
      });
      const tabs = within(navigation).getAllByRole('link');
      expect(tabs).toHaveLength(2);
      expect(tabs[0]).toHaveAccessibleName('Accueil');
      expect(tabs[0]).toHaveAttribute('aria-current', 'page');
      expect(tabs[1]).toHaveAccessibleName('Arbre');
      expect(tabs[1]).toHaveAttribute('href', `/families/${ADJI_ID}/tree`);

      fireEvent.click(screen.getByRole('link', { name: 'Paramètres du compte' }));

      expect(router.state.location.pathname).toBe('/settings');
    });

    it('shows a friendly page with a way back for a Family the User cannot see', async () => {
      const api = fakeApi({
        'GET /families': () => jsonResponse([]),
        [`GET /families/${NJOH_ID}`]: () => problemResponse('FAMILY_NOT_FOUND', 404),
      });
      const { router } = renderApp(`/families/${NJOH_ID}`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
      ).toBeInTheDocument();
      expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
      expect(api.familyRequests()).toHaveLength(1);

      fireEvent.click(screen.getByRole('link', { name: 'Retour à mes familles' }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Créer votre famille' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe('/families/new');
    });

    it('treats a malformed Family address as not found, without calling the API', async () => {
      const api = fakeApi({});
      renderApp('/families/not-a-family');

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
      ).toBeInTheDocument();
      expect(api.familyRequests()).toHaveLength(0);
    });

    it('offers to try again on other errors', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => problemResponse('SOMETHING_NEW', 400) });
      renderApp(`/families/${ADJI_ID}`);

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "Une erreur inattendue s'est produite. Réessayez.",
      );
      expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
    });
  });

  describe('Add a Person (SCREEN-004)', () => {
    const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';

    function person(overrides: Record<string, unknown> = {}) {
      return {
        id: MARIE_ID,
        familyId: ADJI_ID,
        firstName: 'Marie',
        displayName: 'Marie Adji',
        gender: 'UNKNOWN',
        birth: { precision: 'UNKNOWN' },
        isDeceased: false,
        death: { precision: 'UNKNOWN' },
        status: 'ACTIVE',
        version: 0,
        biography: null,
        createdAt: '2026-09-25T10:00:00Z',
        updatedAt: '2026-09-25T10:00:00Z',
        ...overrides,
      };
    }

    /** The Family has no Person until the POST succeeds. */
    function personApi(post: Handler = () => jsonResponse(person(), 201)) {
      let created = false;
      return fakeApi({
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', created ? 1 : 0)),
        [`POST /families/${ADJI_ID}/persons`]: async (request) => {
          const response = await post(request);
          created = response.ok;
          return response;
        },
      });
    }

    function type(name: string, value: string) {
      fireEvent.change(screen.getByRole('textbox', { name }), { target: { value } });
    }

    async function postedBody(api: ReturnType<typeof fakeApi>) {
      const post = api.requests.find((request) => request.method === 'POST');
      return (await post?.json()) as Record<string, unknown>;
    }

    it('starts with me from the empty Family Home, then shows 1 person', async () => {
      const api = personApi();
      const { router } = renderApp(`/families/${ADJI_ID}`);
      fireEvent.click(await screen.findByRole('link', { name: 'Commencer par moi' }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Commencer par moi' }),
      ).toBeInTheDocument();
      expect(screen.getAllByRole('textbox').map((box) => box.getAttribute('name'))).toEqual([
        'firstName',
        'lastName',
      ]);
      expect(screen.queryByText(/photo/i)).not.toBeInTheDocument();
      type('Prénom', '  Marie ');
      type('Nom', 'Adji');
      fireEvent.click(screen.getByRole('button', { name: "M'ajouter à la famille" }));

      expect(await screen.findByRole('status')).toHaveTextContent(
        'Vous faites maintenant partie de la famille ADJI.',
      );
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}`);
      expect(screen.getByRole('link', { name: 'Voir le profil' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/${MARIE_ID}`,
      );
      expect(await screen.findByText('1 personne')).toBeInTheDocument();
      expect(await postedBody(api)).toEqual({
        firstName: 'Marie',
        lastName: 'Adji',
        gender: 'UNKNOWN',
        birth: { precision: 'UNKNOWN' },
        isDeceased: false,
        death: { precision: 'UNKNOWN' },
        linkToCurrentUser: true,
        confirmPossibleDuplicate: false,
      });
    });

    it('adds someone else with optional details, in English', async () => {
      const api = personApi(() =>
        jsonResponse(person({ firstName: 'Paul', displayName: 'Papa Paul' }), 201),
      );
      renderApp(`/families/${ADJI_ID}/persons/new`);
      await screen.findByRole('heading', { level: 1, name: 'Ajouter une personne' });
      await act(() => i18n.changeLanguage('en'));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Add a person' }),
      ).toBeInTheDocument();
      type('First name', 'Paul');
      fireEvent.click(screen.getByRole('button', { name: 'More information' }));
      type('Preferred name or nickname', 'Papa Paul');
      fireEvent.change(screen.getByRole('combobox', { name: 'Gender' }), {
        target: { value: 'MALE' },
      });
      fireEvent.change(screen.getByRole('combobox', { name: 'Date of birth' }), {
        target: { value: 'YEAR_ONLY' },
      });
      type('Year of birth', '1950');
      fireEvent.click(screen.getByRole('checkbox', { name: 'This person has died' }));
      fireEvent.change(screen.getByRole('combobox', { name: 'Date of death' }), {
        target: { value: 'EXACT' },
      });
      fireEvent.change(screen.getByLabelText('Exact date of death'), {
        target: { value: '2020-05-04' },
      });
      type('Biography', 'Teacher.');
      fireEvent.click(screen.getByRole('button', { name: 'Add to the family' }));

      expect(await screen.findByRole('status')).toHaveTextContent(
        'Papa Paul is now part of the family.',
      );
      expect(await postedBody(api)).toEqual({
        firstName: 'Paul',
        preferredName: 'Papa Paul',
        gender: 'MALE',
        birth: { precision: 'YEAR_ONLY', year: 1950 },
        isDeceased: true,
        death: { precision: 'EXACT', date: '2020-05-04' },
        biography: 'Teacher.',
        linkToCurrentUser: false,
        confirmPossibleDuplicate: false,
      });
    });

    it('requires a first name, with no request', async () => {
      const api = personApi();
      renderApp(`/families/${ADJI_ID}/persons/new`);
      await screen.findByRole('heading', { level: 1, name: 'Ajouter une personne' });

      type('Prénom', '   ');
      fireEvent.click(screen.getByRole('button', { name: 'Ajouter à la famille' }));

      expect(await screen.findByText('Indiquez le prénom.')).toBeInTheDocument();
      expect(screen.getByRole('textbox', { name: 'Prénom' })).toHaveAttribute(
        'aria-invalid',
        'true',
      );
      expect(api.requests.some((request) => request.method === 'POST')).toBe(false);
    });

    it('opens More information to show an invalid year, with no request', async () => {
      const api = personApi();
      renderApp(`/families/${ADJI_ID}/persons/new`);
      await screen.findByRole('heading', { level: 1, name: 'Ajouter une personne' });
      type('Prénom', 'Marie');
      fireEvent.click(screen.getByRole('button', { name: "Plus d'informations" }));
      fireEvent.change(screen.getByRole('combobox', { name: 'Date de naissance' }), {
        target: { value: 'YEAR_ONLY' },
      });
      type('Année de naissance', '19a4');
      fireEvent.click(screen.getByRole('button', { name: "Plus d'informations" }));
      expect(screen.getByRole('button', { name: "Plus d'informations" })).toHaveAttribute(
        'aria-expanded',
        'false',
      );

      fireEvent.click(screen.getByRole('button', { name: 'Ajouter à la famille' }));

      expect(await screen.findByText('Indiquez une année entre 1 et 9999.')).toBeVisible();
      expect(api.requests.some((request) => request.method === 'POST')).toBe(false);
    });

    it('translates a refused Start with me and never shows the raw message', async () => {
      personApi(() => problemResponse('USER_ALREADY_LINKED', 409));
      renderApp(`/families/${ADJI_ID}/persons/new?mode=me`);
      await screen.findByRole('heading', { level: 1, name: 'Commencer par moi' });

      type('Prénom', 'Marie');
      fireEvent.click(screen.getByRole('button', { name: "M'ajouter à la famille" }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "Vous faites déjà partie de l'arbre de cette famille.",
      );
      expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
    });

    it('treats a malformed Family address as not found', async () => {
      const api = fakeApi({});
      renderApp('/families/not-a-family/persons/new');

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
      ).toBeInTheDocument();
      expect(api.familyRequests()).toHaveLength(0);
    });
  });
});
