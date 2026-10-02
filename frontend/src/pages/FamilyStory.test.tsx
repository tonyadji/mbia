import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { familyStoryPath } from '../memories/storyPath';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { addMemoryPath } from './AddMemoryPage';
import { familyHomePath } from './FamilyHomePage';
import { memoryPath } from './MemoryPage';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number) {
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail' },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const OTHER_ID = '1b2c3d4e-5f60-4a7b-8c9d-0e1f2a3b4c5d';
const AWA_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';

function family(myRole: string, personCount = 2) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    myLinkedPersonId: null,
    stats: { personCount, memoryCount: 6, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

const STRIP = {
  years: [
    { year: 1962, memoryCount: 1 },
    { year: 1975, memoryCount: 3 },
  ],
  undatedMemoryCount: 2,
};

function memory(id: string, title: string, happenedAt: Record<string, unknown>) {
  return {
    id,
    familyId: ADJI_ID,
    type: 'STORY',
    photos: [],
    status: 'ACTIVE',
    title,
    content: null,
    happenedAt,
    relatedPersons: [{ id: AWA_ID, displayName: 'Awa Ngo', status: 'ACTIVE' }],
    createdBy: { userId: 'u1', displayName: 'Tony', deleted: false },
    createdAt: '2026-09-20T10:00:00Z',
    updatedAt: '2026-09-20T10:00:00Z',
    version: 0,
  };
}

const MARKET = memory('00000000-0000-4000-8000-000000000001', 'Le marché', {
  precision: 'EXACT',
  date: '1975-03-12',
});
const WEDDING = memory('00000000-0000-4000-8000-000000000002', 'Le mariage', {
  precision: 'YEAR_ONLY',
  year: 1975,
});

function page(items: unknown[], pageNumber = 0, totalElements = items.length) {
  return {
    items,
    page: { page: pageNumber, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) },
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), recording the Memory list requests. */
function fakeApi({
  locale = 'fr',
  role = 'ADMIN',
  personCount = 2,
  strip = () => jsonResponse(STRIP),
  memories = () => jsonResponse(page([MARKET, WEDDING])),
}: {
  locale?: string;
  role?: string;
  personCount?: number;
  strip?: Handler;
  memories?: Handler;
} = {}) {
  const listRequests: URL[] = [];
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role, personCount)),
    [`GET /families/${ADJI_ID}/story/years`]: strip,
    [`GET /families/${ADJI_ID}/memories`]: memories,
  };
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({ id: 'u1', email: 'tony@mbia.local', preferredLocale: locale });
    }
    if (path.endsWith('/memories')) {
      listRequests.push(url);
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('FAMILY_NOT_FOUND', 404);
  });
  return { listRequests };
}

function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
  return { router };
}

describe('Family story', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('Our story on Family Home (SCREEN-002)', () => {
    it('shows the strip first, oldest year first, then the undated Memories', async () => {
      fakeApi();
      renderApp(familyHomePath(ADJI_ID));

      const section = await screen.findByRole('region', { name: 'Notre histoire' });
      const strip = await within(section).findByRole('navigation', {
        name: 'Les années de notre histoire',
      });
      const entries = within(strip).getAllByRole('link');
      expect(entries.map((entry) => entry.textContent)).toEqual([
        '1962 · 1 souvenir',
        '1975 · 3 souvenirs',
        'Souvenirs sans date · 2 souvenirs',
      ]);
      expect(entries.map((entry) => entry.getAttribute('href'))).toEqual([
        familyStoryPath(ADJI_ID, 1962),
        familyStoryPath(ADJI_ID, 1975),
        familyStoryPath(ADJI_ID, 'undated'),
      ]);
      // No entry is current on Family Home.
      expect(entries.filter((entry) => entry.hasAttribute('aria-current'))).toEqual([]);
      // First on the screen: before the search and the tree.
      const search = screen.getByRole('link', { name: 'Rechercher dans la famille…' });
      expect(section.compareDocumentPosition(search) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
    });

    it('writes the years in digits only, and has no undated entry without undated Memory', async () => {
      await act(() => i18n.changeLanguage('en'));
      fakeApi({
        locale: 'en',
        strip: () =>
          jsonResponse({ years: [{ year: 2001, memoryCount: 12 }], undatedMemoryCount: 0 }),
      });
      renderApp(familyHomePath(ADJI_ID));

      const strip = await screen.findByRole('navigation', { name: 'The years of our story' });
      expect(
        within(strip)
          .getAllByRole('link')
          .map((entry) => entry.textContent),
      ).toEqual(['2001 · 12 memories']);
    });

    it.each(['ADMIN', 'CONTRIBUTOR'])(
      'replaces the strip by the invitation to tell a first memory for %s',
      async (role) => {
        fakeApi({ role, strip: () => jsonResponse({ years: [], undatedMemoryCount: 0 }) });
        renderApp(familyHomePath(ADJI_ID));

        const section = await screen.findByRole('region', { name: 'Notre histoire' });
        expect(
          await within(section).findByText(
            "L'histoire de votre famille commence par un premier souvenir.",
          ),
        ).toBeVisible();
        expect(within(section).getByRole('link', { name: 'Raconter un souvenir' })).toHaveAttribute(
          'href',
          addMemoryPath(ADJI_ID),
        );
        expect(within(section).queryByRole('navigation')).not.toBeInTheDocument();
      },
    );

    it('shows the invitation as text only to a VIEWER, who reads the strip otherwise', async () => {
      fakeApi({ role: 'VIEWER', strip: () => jsonResponse({ years: [], undatedMemoryCount: 0 }) });
      renderApp(familyHomePath(ADJI_ID));

      const section = await screen.findByRole('region', { name: 'Notre histoire' });
      expect(
        await within(section).findByText(
          "L'histoire de votre famille commence par un premier souvenir.",
        ),
      ).toBeVisible();
      expect(within(section).queryByRole('link')).not.toBeInTheDocument();
    });

    it('lets a VIEWER read the strip', async () => {
      fakeApi({ role: 'VIEWER' });
      renderApp(familyHomePath(ADJI_ID));

      const strip = await screen.findByRole('navigation', { name: 'Les années de notre histoire' });
      expect(within(strip).getAllByRole('link')).toHaveLength(3);
    });

    it('has no story while the Family has no Person (its empty state stays)', async () => {
      fakeApi({ personCount: 0 });
      renderApp(familyHomePath(ADJI_ID));

      expect(
        await screen.findByRole('link', { name: 'Raconter un premier souvenir' }),
      ).toBeVisible();
      expect(screen.queryByRole('region', { name: 'Notre histoire' })).not.toBeInTheDocument();
    });
  });

  describe('YearStrip keyboard', () => {
    it('moves between the entries with the arrows, Home and End', async () => {
      fakeApi();
      renderApp(familyHomePath(ADJI_ID));

      const strip = await screen.findByRole('navigation', { name: 'Les années de notre histoire' });
      const first = within(strip).getByRole('link', { name: '1962 · 1 souvenir' });
      const second = within(strip).getByRole('link', { name: '1975 · 3 souvenirs' });
      const last = within(strip).getByRole('link', { name: 'Souvenirs sans date · 2 souvenirs' });
      first.focus();
      fireEvent.keyDown(first, { key: 'ArrowRight' });
      expect(second).toHaveFocus();
      fireEvent.keyDown(second, { key: 'ArrowLeft' });
      expect(first).toHaveFocus();
      fireEvent.keyDown(first, { key: 'ArrowLeft' });
      expect(first).toHaveFocus();
      fireEvent.keyDown(first, { key: 'End' });
      expect(last).toHaveFocus();
      fireEvent.keyDown(last, { key: 'ArrowRight' });
      expect(last).toHaveFocus();
      fireEvent.keyDown(last, { key: 'Home' });
      expect(first).toHaveFocus();
    });
  });

  describe('What happened in {year} (SCREEN-016)', () => {
    it('lists the Memories of the year in the server order, each with its day when exact', async () => {
      const api = fakeApi();
      const { router } = renderApp(familyStoryPath(ADJI_ID, 1975));

      expect(
        await screen.findByRole('heading', { level: 1, name: "Ce qui s'est passé en 1975" }),
      ).toHaveFocus();
      const list = await screen.findByRole('list', { name: "Ce qui s'est passé en 1975" });
      const cards = within(list).getAllByRole('link');
      expect(cards.map((card) => card.textContent)).toEqual(['Le marché12 mars', 'Le mariage']);
      expect(api.listRequests.map((url) => url.search)).toEqual(['?year=1975&page=0&size=20']);

      fireEvent.click(within(list).getByRole('link', { name: /Le marché/ }));

      expect(router.state.location.pathname).toBe(memoryPath(ADJI_ID, MARKET.id));
    });

    it('keeps the strip with the current year marked, and moves to the neighbouring years', async () => {
      fakeApi();
      const { router } = renderApp(familyStoryPath(ADJI_ID, 1975));

      const strip = await screen.findByRole('navigation', { name: 'Les années de notre histoire' });
      const current = within(strip).getByRole('link', { current: 'page' });
      expect(current).toHaveTextContent('1975 · 3 souvenirs');
      expect(current.className).toMatch(/font-bold/);
      expect(current.className).toMatch(/underline /);

      fireEvent.click(screen.getByRole('button', { name: 'Année précédente' }));
      expect(router.state.location.pathname).toBe(familyStoryPath(ADJI_ID, 1962));
      expect(await screen.findByRole('button', { name: 'Année précédente' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Année suivante' })).toBeEnabled();

      fireEvent.click(screen.getByRole('button', { name: 'Année suivante' }));
      fireEvent.click(await screen.findByRole('button', { name: 'Année suivante' }));
      expect(router.state.location.pathname).toBe(familyStoryPath(ADJI_ID, 'undated'));

      fireEvent.click(within(strip).getByRole('link', { name: '1962 · 1 souvenir' }));
      expect(router.state.location.pathname).toBe(familyStoryPath(ADJI_ID, 1962));
    });

    it('titles the undated Memories, most recently added first, the last entry of the strip', async () => {
      const undated = memory('00000000-0000-4000-8000-000000000003', 'Sans date', {
        precision: 'UNKNOWN',
      });
      const api = fakeApi({ memories: () => jsonResponse(page([undated])) });
      renderApp(familyStoryPath(ADJI_ID, 'undated'));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Souvenirs sans date' }),
      ).toBeVisible();
      const list = await screen.findByRole('list', { name: 'Souvenirs sans date' });
      expect(within(list).getByRole('link')).toHaveTextContent(/^Sans date$/);
      expect(api.listRequests.map((url) => url.search)).toEqual(['?undated=true&page=0&size=20']);
      expect(screen.getByRole('button', { name: 'Année suivante' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Année précédente' })).toBeEnabled();
      const strip = screen.getByRole('navigation', { name: 'Les années de notre histoire' });
      expect(within(strip).getByRole('link', { current: 'page' })).toHaveTextContent(
        'Souvenirs sans date · 2 souvenirs',
      );
    });

    it('shows 20 Memories, then the next ones with Show more', async () => {
      const first = Array.from({ length: 20 }, (_, i) =>
        memory(
          `00000000-0000-4000-8000-0000000001${String(i).padStart(2, '0')}`,
          `Histoire ${String(i)}`,
          {
            precision: 'YEAR_ONLY',
            year: 1975,
          },
        ),
      );
      const last = memory('00000000-0000-4000-8000-000000000199', 'Histoire 20', {
        precision: 'YEAR_ONLY',
        year: 1975,
      });
      const api = fakeApi({
        memories: (request) =>
          new URL(request.url).searchParams.get('page') === '1'
            ? jsonResponse(page([last], 1, 21))
            : jsonResponse(page(first, 0, 21)),
      });
      renderApp(familyStoryPath(ADJI_ID, 1975));

      const list = await screen.findByRole('list', { name: "Ce qui s'est passé en 1975" });
      expect(within(list).getAllByRole('listitem')).toHaveLength(20);
      fireEvent.click(screen.getByRole('button', { name: 'Voir plus' }));

      expect(await within(list).findByRole('link', { name: /Histoire 20/ })).toBeVisible();
      expect(screen.queryByRole('button', { name: 'Voir plus' })).not.toBeInTheDocument();
      expect(api.listRequests.map((url) => url.searchParams.get('page'))).toEqual(['0', '1']);
    });

    it('explains a year left without Memory and offers the other years', async () => {
      fakeApi({ memories: () => jsonResponse(page([])) });
      renderApp(familyStoryPath(ADJI_ID, 1980));

      expect(
        await screen.findByText(
          "Il n'y a plus de souvenir en 1980. Choisissez une autre année de l'histoire.",
        ),
      ).toBeVisible();
      const strip = screen.getByRole('navigation', { name: 'Les années de notre histoire' });
      expect(within(strip).getAllByRole('link')).toHaveLength(3);
      expect(within(strip).queryByRole('link', { current: 'page' })).not.toBeInTheDocument();
      // Between 1975 and the undated Memories.
      expect(screen.getByRole('button', { name: 'Année précédente' })).toBeEnabled();
      expect(screen.getByRole('button', { name: 'Année suivante' })).toBeEnabled();
    });

    it.each(['0', '10000', 'abc', '0975', '19.5'])(
      'does not find the year %s, without loading any Memory',
      async (year) => {
        const api = fakeApi();
        renderApp(`/families/${ADJI_ID}/story/${year}`);

        expect(
          await screen.findByRole('heading', { level: 1, name: 'Année introuvable' }),
        ).toBeVisible();
        expect(screen.getByRole('link', { name: 'Retour à la famille' })).toHaveAttribute(
          'href',
          familyHomePath(ADJI_ID),
        );
        expect(api.listRequests).toEqual([]);
      },
    );

    it.each(['ADMIN', 'CONTRIBUTOR'])('offers Tell a memory to %s', async (role) => {
      fakeApi({ role });
      renderApp(familyStoryPath(ADJI_ID, 1975));

      expect(await screen.findByRole('link', { name: 'Raconter un souvenir' })).toHaveAttribute(
        'href',
        addMemoryPath(ADJI_ID),
      );
    });

    it('lets a VIEWER read the year, without Tell a memory', async () => {
      fakeApi({ role: 'VIEWER' });
      renderApp(familyStoryPath(ADJI_ID, 1975));

      const list = await screen.findByRole('list', { name: "Ce qui s'est passé en 1975" });
      expect(within(list).getAllByRole('link')).toHaveLength(2);
      expect(screen.queryByRole('link', { name: 'Raconter un souvenir' })).not.toBeInTheDocument();
    });

    it('shows Family not found for the year of another Family, without its data', async () => {
      fakeApi();
      renderApp(familyStoryPath(OTHER_ID, 1975));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
      ).toBeVisible();
      expect(screen.queryByRole('navigation', { name: /années/ })).not.toBeInTheDocument();
      expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('writes the day of a card in English for an English reader', async () => {
      await act(() => i18n.changeLanguage('en'));
      fakeApi({ locale: 'en' });
      renderApp(familyStoryPath(ADJI_ID, 1975));

      const list = await screen.findByRole('list', { name: 'What happened in 1975' });
      expect(within(list).getAllByRole('link')[0]).toHaveTextContent('Le marchéMarch 12');
    });
  });
});
