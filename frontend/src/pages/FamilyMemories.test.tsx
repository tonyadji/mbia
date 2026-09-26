import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { addMemoryPath } from './AddMemoryPage';
import { familyMemoriesPath } from './FamilyMemoriesPage';
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
const AWA_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';
const MEMORIES = familyMemoriesPath(ADJI_ID);

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

/** A fake API answering by method and path (after `/api/v1`), recording the Memory list requests. */
function fakeApi({
  role = 'ADMIN',
  familyHandler = () => jsonResponse(family(role)),
  memories = () => jsonResponse(page([])),
}: {
  role?: string;
  familyHandler?: Handler;
  memories?: Handler;
} = {}) {
  const listRequests: URL[] = [];
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: familyHandler,
    [`GET /families/${ADJI_ID}/memories`]: memories,
    [`GET /families/${ADJI_ID}/memories/${MEMORY_ID}`]: () => jsonResponse(memory()),
  };
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({ id: 'u1', email: 'tony@mbia.local', preferredLocale: 'fr' });
    }
    if (path === `/families/${ADJI_ID}/memories`) {
      listRequests.push(url);
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
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

describe('Family Memories (SCREEN-015)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  it('lists the story cards, each opening the Memory, from the Memories tab', async () => {
    const api = fakeApi({ memories: () => jsonResponse(page([memory()])) });
    const { router } = renderApp(MEMORIES);

    expect(await screen.findByRole('heading', { level: 1, name: 'Souvenirs' })).toBeInTheDocument();
    const list = await screen.findByRole('list', { name: 'Souvenirs' });
    const card = within(list).getByRole('link', { name: /Le marché de Yaoundé/ });
    expect(card).toHaveAttribute('href', memoryPath(ADJI_ID, MEMORY_ID));
    expect(card).toHaveTextContent('Grand-mère vendait du plantain.');
    const navigation = screen.getByRole('navigation', { name: 'Navigation de la famille' });
    expect(within(navigation).getByRole('link', { name: 'Souvenirs' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    // Most recent first, 20 per page, without a type filter (phase-3-family-memories.md §3.1).
    expect(api.listRequests.map((url) => url.search)).toEqual(['?page=0&size=20']);
    expect(screen.queryByRole('button', { name: /Photos|Récits|Tous/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Photos|Récits/)).not.toBeInTheDocument();

    fireEvent.click(card);

    expect(router.state.location.pathname).toBe(memoryPath(ADJI_ID, MEMORY_ID));
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
    renderApp(MEMORIES);

    const list = await screen.findByRole('list', { name: 'Souvenirs' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(20);
    fireEvent.click(screen.getByRole('button', { name: 'Voir plus' }));

    expect(await within(list).findByRole('link', { name: /Histoire 20/ })).toBeVisible();
    expect(within(list).getAllByRole('listitem')).toHaveLength(21);
    expect(screen.queryByRole('button', { name: 'Voir plus' })).not.toBeInTheDocument();
    expect(api.listRequests.map((url) => url.searchParams.get('page'))).toEqual(['0', '1']);
  });

  it('shows the text of a story as typed, without interpreting it', async () => {
    fakeApi({
      memories: () =>
        jsonResponse(page([memory({ title: '<b>Titre</b>', content: '<i>texte</i> **gras**' })])),
    });
    renderApp(MEMORIES);

    const card = await screen.findByRole('link', { name: /<b>Titre<\/b>/ });
    expect(card).toHaveTextContent('<i>texte</i> **gras**');
    expect(card.querySelector('b, i, strong')).toBeNull();
  });

  it.each(['ADMIN', 'CONTRIBUTOR'])(
    'explains an empty Family and offers Add a memory to %s',
    async (role) => {
      fakeApi({ role });
      renderApp(MEMORIES);

      expect(
        await screen.findByText(
          "La famille n'a pas encore de souvenir. Les histoires racontées sur vos proches apparaîtront ici.",
        ),
      ).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Ajouter un souvenir' })).toHaveAttribute(
        'href',
        addMemoryPath(ADJI_ID),
      );
    },
  );

  it('shows only the explanation to a VIEWER of an empty Family, in French then English', async () => {
    fakeApi({ role: 'VIEWER' });
    renderApp(MEMORIES);

    expect(
      await screen.findByText(
        "La famille n'a pas encore de souvenir. Les histoires racontées sur vos proches apparaîtront ici.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Ajouter un souvenir' })).not.toBeInTheDocument();

    await act(() => i18n.changeLanguage('en'));

    expect(screen.getByRole('heading', { level: 1, name: 'Memories' })).toBeInTheDocument();
    expect(
      screen.getByText(
        'The family has no memory yet. Stories told about your relatives will appear here.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Add a memory' })).not.toBeInTheDocument();
  });

  it('offers Add a memory above the list to a CONTRIBUTOR', async () => {
    fakeApi({ role: 'CONTRIBUTOR', memories: () => jsonResponse(page([memory()])) });
    renderApp(MEMORIES);
    expect(await screen.findByRole('link', { name: 'Ajouter un souvenir' })).toBeInTheDocument();
  });

  it('does not offer Add a memory to a VIEWER when Memories exist', async () => {
    fakeApi({ role: 'VIEWER', memories: () => jsonResponse(page([memory()])) });
    renderApp(MEMORIES);

    expect(await screen.findByRole('link', { name: /Le marché de Yaoundé/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Ajouter un souvenir' })).not.toBeInTheDocument();
  });

  it('shows the Family not found page for a Family the User cannot see', async () => {
    fakeApi({
      familyHandler: () => problemResponse('FAMILY_NOT_FOUND', 404),
      memories: () => problemResponse('FAMILY_NOT_FOUND', 404),
    });
    renderApp(MEMORIES);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
  });

  it('treats a malformed Family address as not found, without calling the list', async () => {
    const api = fakeApi();
    renderApp('/families/not-a-uuid/memories');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
    ).toBeInTheDocument();
    expect(api.listRequests).toEqual([]);
  });
});
