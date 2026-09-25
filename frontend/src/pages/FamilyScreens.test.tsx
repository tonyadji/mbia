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

function family(id: string, name: string, personCount = 0, memoryCount = 0) {
  return {
    id,
    name,
    myRole: 'ADMIN',
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
      expect(screen.getByText('0 personne • 0 souvenir')).toBeInTheDocument();
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
    it('welcomes a Family without Persons, without add actions, in French then English', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI')) });
      renderApp(`/families/${ADJI_ID}`);

      expect(
        await screen.findByRole('heading', { level: 2, name: 'Bienvenue dans la famille ADJI' }),
      ).toBeInTheDocument();
      expect(screen.getByText('Ajoutons la première personne.')).toBeInTheDocument();
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
      expect(screen.queryByRole('status')).not.toBeInTheDocument();

      await act(() => i18n.changeLanguage('en'));

      expect(
        screen.getByRole('heading', { level: 2, name: 'Welcome to the ADJI family' }),
      ).toBeInTheDocument();
      expect(screen.getByText("Let's add the first person.")).toBeInTheDocument();
      expect(screen.getByText('0 people • 0 memories')).toBeInTheDocument();
    });

    it('shows the Person and Memory counts, without the empty state once Persons exist', async () => {
      fakeApi({
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', 3, 1)),
      });
      renderApp(`/families/${ADJI_ID}`);

      expect(await screen.findByText('3 personnes • 1 souvenir')).toBeInTheDocument();
      expect(screen.queryByText('Ajoutons la première personne.')).not.toBeInTheDocument();
    });

    it('has a navigation bar with only Home, and opens settings from the avatar', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI')) });
      const { router } = renderApp(`/families/${ADJI_ID}`);
      await screen.findByRole('heading', { level: 1, name: 'ADJI' });

      const navigation = screen.getByRole('navigation', {
        name: 'Navigation de la famille',
      });
      const tabs = within(navigation).getAllByRole('link');
      expect(tabs).toHaveLength(1);
      expect(tabs[0]).toHaveAccessibleName('Accueil');
      expect(tabs[0]).toHaveAttribute('aria-current', 'page');

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
});
