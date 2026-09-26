import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { addMemoryPath } from './AddMemoryPage';
import { memoryPath } from './MemoryPage';
import { personPath } from './PersonProfilePage';

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
const AWA_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const ELOISE_ID = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';
const PROFILE = personPath(ADJI_ID, AWA_ID);
const MEMORY = memoryPath(ADJI_ID, MEMORY_ID);

function family(myRole: string) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    myLinkedPersonId: null,
    stats: { personCount: 2, memoryCount: 1, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function person(overrides: Record<string, unknown> = {}) {
  return {
    id: AWA_ID,
    familyId: ADJI_ID,
    firstName: 'Awa',
    lastName: 'Ngo',
    displayName: 'Awa Ngo',
    gender: 'FEMALE',
    birth: { precision: 'YEAR_ONLY', year: 1930 },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 0,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

function memory(overrides: Record<string, unknown> = {}) {
  return {
    id: MEMORY_ID,
    familyId: ADJI_ID,
    type: 'STORY',
    status: 'ACTIVE',
    title: 'Le marché de Yaoundé',
    content: 'Grand-mère vendait du plantain.',
    relatedPersons: [{ id: AWA_ID, displayName: 'Awa Ngo', status: 'ACTIVE' }],
    createdBy: { userId: 'u1', displayName: 'Tony', deleted: false },
    createdAt: '2026-09-20T10:00:00Z',
    updatedAt: '2026-09-20T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

function page(items: unknown[], pageNumber = 0, totalElements = items.length) {
  return {
    items,
    page: { page: pageNumber, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) },
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), recording every request. */
function fakeApi({
  role = 'ADMIN',
  awa = person(),
  memories = () => jsonResponse(page([])),
  getMemory = () => jsonResponse(memory()),
  locale = 'fr',
}: {
  role?: string;
  awa?: Record<string, unknown>;
  memories?: Handler;
  getMemory?: Handler;
  locale?: string;
} = {}) {
  const requests: Request[] = [];
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}`]: () => jsonResponse(awa),
    [`GET /families/${ADJI_ID}/tree`]: () =>
      jsonResponse({ focusPersonId: AWA_ID, nodes: [], edges: [] }),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}/archived-relationships`]: () => jsonResponse([]),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}/memories`]: memories,
    [`GET /families/${ADJI_ID}/memories/${MEMORY_ID}`]: getMemory,
  };
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({ id: 'u1', email: 'tony@mbia.local', preferredLocale: locale });
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    memoryPages: () =>
      requests
        .filter((request) => new URL(request.url).pathname.endsWith('/memories'))
        .map((request) => new URL(request.url).searchParams.get('page')),
  };
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

describe('Memory screens', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('Person profile Memories section (SCREEN-005)', () => {
    it('comes first and lists story cards opening the Memory', async () => {
      const api = fakeApi({ memories: () => jsonResponse(page([memory()])) });
      renderApp(PROFILE);

      const section = await screen.findByRole('region', { name: 'Souvenirs' });
      const card = await within(section).findByRole('link', { name: /Le marché de Yaoundé/ });
      expect(card).toHaveAttribute('href', MEMORY);
      expect(card).toHaveTextContent('Grand-mère vendait du plantain.');
      const sections = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
      expect(sections[0]).toBe('Souvenirs');
      expect(sections.indexOf('Famille')).toBeGreaterThan(0);
      expect(api.memoryPages()).toEqual(['0']);
    });

    it('shows 20 Memories, then the next ones with Show more', async () => {
      const first = Array.from({ length: 20 }, (_, i) =>
        memory({
          id: `00000000-0000-4000-8000-0000000000${String(i).padStart(2, '0')}`,
          title: `Histoire ${String(i)}`,
        }),
      );
      const last = memory({ id: '00000000-0000-4000-8000-000000000099', title: 'Histoire 20' });
      const api = fakeApi({
        memories: (request) =>
          new URL(request.url).searchParams.get('page') === '1'
            ? jsonResponse(page([last], 1, 21))
            : jsonResponse(page(first, 0, 21)),
      });
      renderApp(PROFILE);

      const section = await screen.findByRole('region', { name: 'Souvenirs' });
      expect(await within(section).findAllByRole('listitem')).toHaveLength(20);
      fireEvent.click(within(section).getByRole('button', { name: 'Voir plus' }));

      expect(await within(section).findByRole('link', { name: /Histoire 20/ })).toBeVisible();
      expect(within(section).getAllByRole('listitem')).toHaveLength(21);
      expect(within(section).queryByRole('button', { name: 'Voir plus' })).not.toBeInTheDocument();
      expect(api.memoryPages()).toEqual(['0', '1']);
    });

    it.each(['ADMIN', 'CONTRIBUTOR'])(
      'explains an empty section and offers Add a memory to a %s',
      async (role) => {
        fakeApi({ role });
        renderApp(PROFILE);

        const section = await screen.findByRole('region', { name: 'Souvenirs' });
        expect(
          await within(section).findByText("Aucun souvenir n'est encore lié à Awa Ngo."),
        ).toBeInTheDocument();
        expect(within(section).getByRole('link', { name: 'Ajouter un souvenir' })).toHaveAttribute(
          'href',
          addMemoryPath(ADJI_ID, { personId: AWA_ID }),
        );
      },
    );

    it('offers no Add a memory to a VIEWER, who still reads the Memories', async () => {
      fakeApi({ role: 'VIEWER', memories: () => jsonResponse(page([memory()])) });
      renderApp(PROFILE);

      const section = await screen.findByRole('region', { name: 'Souvenirs' });
      expect(await within(section).findByRole('link', { name: /Le marché/ })).toBeVisible();
      expect(screen.queryByRole('link', { name: 'Ajouter un souvenir' })).not.toBeInTheDocument();
    });

    it('keeps the Memories of an archived Person, without Add a memory', async () => {
      fakeApi({
        awa: person({ status: 'ARCHIVED' }),
        memories: () => jsonResponse(page([memory()])),
      });
      renderApp(PROFILE);

      const section = await screen.findByRole('region', { name: 'Souvenirs' });
      expect(await within(section).findByRole('link', { name: /Le marché/ })).toBeVisible();
      expect(screen.queryByRole('link', { name: 'Ajouter un souvenir' })).not.toBeInTheDocument();
    });
  });

  describe('Memory (SCREEN-013)', () => {
    it('shows the title, the text as typed, the Persons, the author and the date', async () => {
      const content =
        'Chaque samedi,\n\n<b>grand-mère</b> allait au **marché**.\n<script>x</script>';
      fakeApi({
        role: 'VIEWER',
        getMemory: () =>
          jsonResponse(
            memory({
              content,
              relatedPersons: [
                { id: AWA_ID, displayName: 'Awa Ngo', status: 'ACTIVE' },
                { id: ELOISE_ID, displayName: 'Éloïse Ngo', status: 'ACTIVE' },
              ],
            }),
          ),
      });
      renderApp(MEMORY);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Le marché de Yaoundé' }),
      ).toBeInTheDocument();
      const text = screen.getByText(/Chaque samedi/);
      expect(text.textContent).toBe(content);
      expect(text).toHaveClass('whitespace-pre-wrap');
      expect(text.querySelector('b, strong, script')).toBeNull();
      expect(screen.getByText('Ajouté par Tony le 20 septembre 2026')).toBeInTheDocument();
      const persons = screen.getByRole('list', { name: 'Personnes concernées' });
      expect(within(persons).getByRole('link', { name: 'Awa Ngo' })).toHaveAttribute(
        'href',
        PROFILE,
      );
      expect(within(persons).getByRole('link', { name: 'Éloïse Ngo' })).toHaveAttribute(
        'href',
        personPath(ADJI_ID, ELOISE_ID),
      );
      expect(screen.queryByRole('button', { name: /Modifier|Archiver/ })).not.toBeInTheDocument();
    });

    const withArchivedAwa = () =>
      jsonResponse(
        memory({
          relatedPersons: [
            { id: AWA_ID, displayName: 'Awa Ngo', status: 'ARCHIVED' },
            { id: ELOISE_ID, displayName: 'Éloïse Ngo', status: 'ACTIVE' },
          ],
        }),
      );

    it.each(['CONTRIBUTOR', 'VIEWER'])(
      'marks an archived Person, without a link for a %s',
      async (role) => {
        fakeApi({ role, getMemory: withArchivedAwa });
        renderApp(MEMORY);

        const persons = await screen.findByRole('list', { name: 'Personnes concernées' });
        expect(within(persons).getByText('Awa Ngo')).toBeInTheDocument();
        expect(within(persons).queryByRole('link', { name: 'Awa Ngo' })).not.toBeInTheDocument();
        expect(within(persons).getByText('Profil archivé')).toBeInTheDocument();
        expect(within(persons).getByRole('link', { name: 'Éloïse Ngo' })).toBeInTheDocument();
      },
    );

    it('marks an archived Person, with a link for the ADMIN', async () => {
      fakeApi({ role: 'ADMIN', getMemory: withArchivedAwa });
      renderApp(MEMORY);

      const persons = await screen.findByRole('list', { name: 'Personnes concernées' });
      expect(within(persons).getByRole('link', { name: 'Awa Ngo' })).toHaveAttribute(
        'href',
        PROFILE,
      );
      expect(within(persons).getByText('Profil archivé')).toBeInTheDocument();
    });

    it('names a deleted author "Former member"', async () => {
      fakeApi({
        getMemory: () =>
          jsonResponse(memory({ createdBy: { userId: 'u9', displayName: null, deleted: true } })),
      });
      renderApp(MEMORY);

      expect(
        await screen.findByText('Ajouté par Ancien membre le 20 septembre 2026'),
      ).toBeInTheDocument();
    });

    it('explains a Memory that does not exist or was archived', async () => {
      fakeApi({ getMemory: () => problemResponse('MEMORY_NOT_FOUND', 404) });
      renderApp(MEMORY);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Souvenir introuvable' }),
      ).toBeInTheDocument();
      expect(screen.queryByText(/raw server detail|404/)).not.toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Retour à la famille' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}`,
      );
    });

    it('shows the family not found page for another Family', async () => {
      fakeApi({ getMemory: () => problemResponse('FAMILY_NOT_FOUND', 404) });
      renderApp(MEMORY);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
      ).toBeInTheDocument();
    });

    it('goes back to the profile it was opened from', async () => {
      fakeApi({ memories: () => jsonResponse(page([memory()])) });
      const { router } = renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('link', { name: /Le marché de Yaoundé/ }));
      fireEvent.click(await screen.findByRole('button', { name: 'Retour' }));

      expect(await screen.findByRole('heading', { level: 1, name: 'Awa Ngo' })).toBeVisible();
      expect(router.state.location.pathname).toBe(PROFILE);
    });

    it('shows the Memory in English', async () => {
      fakeApi({ locale: 'en', getMemory: withArchivedAwa });
      renderApp(MEMORY);

      expect(await screen.findByRole('list', { name: 'People in this memory' })).toBeVisible();
      expect(screen.getByText('Archived profile')).toBeInTheDocument();
      expect(screen.getByText('Added by Tony on September 20, 2026')).toBeInTheDocument();
    });
  });
});
