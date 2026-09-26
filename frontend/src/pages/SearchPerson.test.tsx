import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { addRelativePath } from '../persons/relatives';
import { searchTerm } from '../persons/usePersonSearch';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { archivedPeoplePath } from './ArchivedPeoplePage';
import { searchPath } from './SearchPage';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number, details?: Record<string, unknown>) {
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail', details },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const ELOISE_ID = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const PAUL_ID = '2b3c4d5e-6f70-4b8c-9d0e-1f2a3b4c5d6e';

function family(overrides: Record<string, unknown> = {}) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole: 'ADMIN',
    myLinkedPersonId: MARIE_ID,
    stats: { personCount: 3, memoryCount: 0, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

function person(overrides: Record<string, unknown> = {}) {
  return {
    id: MARIE_ID,
    familyId: ADJI_ID,
    firstName: 'Marie',
    lastName: 'Adji',
    displayName: 'Marie Adji',
    gender: 'FEMALE',
    birth: { precision: 'YEAR_ONLY', year: 1990 },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: 'SELF',
    profilePictureUrl: null,
    version: 0,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

const marie = person();
const eloise = person({
  id: ELOISE_ID,
  firstName: 'Éloïse',
  lastName: 'Ngo',
  displayName: 'Éloïse Ngo',
  birth: { precision: 'YEAR_ONLY', year: 1948 },
  isDeceased: true,
  death: { precision: 'YEAR_ONLY', year: 2019 },
  relationshipToCurrentUser: 'MOTHER',
});
const paul = person({
  id: PAUL_ID,
  firstName: 'Paul',
  displayName: 'Paul Adji',
  gender: 'MALE',
  birth: { precision: 'UNKNOWN' },
  relationshipToCurrentUser: 'NONE_KNOWN',
});

function page(items: unknown[], pageNumber = 0, totalPages = 1) {
  return {
    items,
    page: { page: pageNumber, size: 20, totalElements: items.length, totalPages },
  };
}

type Handler = (request: Request, url: URL) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), recording every request. */
function fakeApi({
  search = (url: URL) =>
    jsonResponse(
      page(url.searchParams.get('search') === 'Eloise' ? [eloise] : [eloise, marie, paul]),
    ),
  relationships = [],
  role = 'ADMIN',
}: {
  search?: (url: URL) => Response;
  relationships?: Handler[];
  role?: string;
} = {}) {
  const requests: { method: string; url: URL; body: unknown }[] = [];
  let relationshipCalls = 0;
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const text = await request.clone().text();
    requests.push({ method: request.method, url, body: text ? JSON.parse(text) : null });
    const key = `${request.method} ${path}`;
    if (key === 'GET /me') {
      return jsonResponse({ id: 'u1', email: 'marie@mbia.local', preferredLocale: 'fr' });
    }
    if (key === `GET /families/${ADJI_ID}`) return jsonResponse(family({ myRole: role }));
    if (key === `GET /families/${ADJI_ID}/persons`) return search(url);
    if (key === `GET /families/${ADJI_ID}/persons/${MARIE_ID}`) return jsonResponse(marie);
    if (key === `GET /families/${ADJI_ID}/tree`) {
      const focus = url.searchParams.get('focusPersonId') ?? MARIE_ID;
      const node = { ...(focus === ELOISE_ID ? eloise : marie), hasMoreParents: false };
      return jsonResponse({
        focusPersonId: focus,
        nodes: [{ ...node, hasMoreChildren: false }],
        edges: [],
      });
    }
    if (key === `POST /families/${ADJI_ID}/relationships`) {
      const handler = relationships[relationshipCalls];
      relationshipCalls += 1;
      if (handler) return handler(request, url);
      return jsonResponse(
        { id: 'r1', familyId: ADJI_ID, status: 'ACTIVE', warnings: [], version: 0, createdAt: '' },
        201,
      );
    }
    return problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    searches: () =>
      requests
        .filter((request) => request.method === 'GET' && request.url.pathname.endsWith('/persons'))
        .map((request) => request.url.searchParams),
    sent: (method: string, suffix: string) =>
      requests.filter(
        (request) => request.method === method && request.url.pathname.endsWith(suffix),
      ),
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

async function type(label: string, text: string) {
  fireEvent.change(await screen.findByRole('searchbox', { name: label }), {
    target: { value: text },
  });
}

describe('Search Person (SCREEN-007)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  it('searches only from 2 characters, trimmed', () => {
    expect(searchTerm('')).toBe('');
    expect(searchTerm(' É ')).toBe('');
    expect(searchTerm(' Él ')).toBe('Él');
  });

  describe('from general navigation', () => {
    it('is reached from the Family home', async () => {
      fakeApi();
      renderApp(`/families/${ADJI_ID}`);

      expect(
        await screen.findByRole('link', { name: 'Rechercher dans la famille…' }),
      ).toHaveAttribute('href', searchPath(ADJI_ID));
    });

    it('lists the first page, then searches after typing, debounced', async () => {
      const api = fakeApi();
      renderApp(searchPath(ADJI_ID));

      const results = await screen.findByRole('list', { name: 'Personnes trouvées' });
      expect(within(results).getAllByRole('button')).toHaveLength(3);
      expect(api.searches()[0]?.get('search')).toBeNull();

      await type('Nom', 'E');
      await type('Nom', 'Eloise');
      await vi.waitFor(() => {
        expect(within(results).getAllByRole('button')).toHaveLength(1);
      });
      // Only the whole text after the pause was searched, never "E".
      expect(api.searches().map((params) => params.get('search'))).toEqual([null, 'Eloise']);
    });

    it('shows name, years and relationship, and opens the profile', async () => {
      fakeApi();
      const { router } = renderApp(searchPath(ADJI_ID));

      const result = await screen.findByRole('button', { name: /^Éloïse Ngo/ });
      expect(within(result).getByText('1948 – 2019')).toBeInTheDocument();
      expect(within(result).getByText('Votre mère')).toBeInTheDocument();
      expect(
        within(screen.getByRole('button', { name: /^Paul Adji/ })).queryByText(/Votre/),
      ).not.toBeInTheDocument();

      fireEvent.click(result);

      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/persons/${ELOISE_ID}`);
    });

    it('explains when no one matches', async () => {
      fakeApi({ search: () => jsonResponse(page([])) });
      renderApp(searchPath(ADJI_ID));

      await type('Nom', 'Zz');

      expect(
        await screen.findByText('Personne dans cette famille ne correspond à « Zz ».'),
      ).toBeInTheDocument();
    });

    it('loads the next page on demand', async () => {
      const api = fakeApi({
        search: (url) =>
          url.searchParams.get('page') === '1'
            ? jsonResponse(page([paul], 1, 2))
            : jsonResponse(page([eloise, marie], 0, 2)),
      });
      renderApp(searchPath(ADJI_ID));

      fireEvent.click(await screen.findByRole('button', { name: 'Afficher plus' }));

      expect(await screen.findByRole('button', { name: /^Paul Adji/ })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Afficher plus' })).not.toBeInTheDocument();
      expect(api.searches().map((params) => params.get('page'))).toEqual(['0', '1']);
    });

    it('translates an error, never the raw server message', async () => {
      fakeApi({ search: () => problemResponse('FORBIDDEN', 403) });
      renderApp(searchPath(ADJI_ID));

      expect(await screen.findByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });
  });

  describe('from the tree', () => {
    beforeEach(() => {
      // The tree canvas scrolls to its focus; jsdom has no layout.
      Element.prototype.scrollTo = () => undefined;
    });

    it('opens from the tree header and recenters the tree on the result', async () => {
      fakeApi();
      const { router } = renderApp(`/families/${ADJI_ID}/tree`);

      fireEvent.click(await screen.findByRole('button', { name: 'Rechercher dans la famille' }));
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/search`);
      expect(screen.getByRole('link', { name: 'Retour' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/tree?focus=${MARIE_ID}`,
      );

      fireEvent.click(await screen.findByRole('button', { name: /^Éloïse Ngo/ }));

      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/tree`);
      expect(router.state.location.search).toBe(`?focus=${ELOISE_ID}`);
    });
  });
});

describe('Archived people (SCREEN-007, ADMIN, PR-26)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  function archivedSearch(url: URL) {
    return url.searchParams.get('status') === 'ARCHIVED'
      ? jsonResponse(
          page([{ ...paul, status: 'ARCHIVED', relationshipToCurrentUser: 'NONE_KNOWN' }]),
        )
      : jsonResponse(page([eloise, marie]));
  }

  it('is offered to the ADMIN from general search, never from the tree', async () => {
    fakeApi({ search: archivedSearch });
    renderApp(searchPath(ADJI_ID));
    expect(await screen.findByRole('link', { name: 'Personnes archivées' })).toHaveAttribute(
      'href',
      archivedPeoplePath(ADJI_ID),
    );
  });

  it('is not offered from the tree search', async () => {
    fakeApi({ search: archivedSearch });
    renderApp(searchPath(ADJI_ID, { focus: MARIE_ID }));

    await screen.findByRole('list', { name: 'Personnes trouvées' });
    expect(screen.queryByRole('link', { name: 'Personnes archivées' })).toBeNull();
  });

  it('is not offered to a CONTRIBUTOR or a VIEWER', async () => {
    for (const role of ['CONTRIBUTOR', 'VIEWER']) {
      fakeApi({ search: archivedSearch, role });
      const { unmount } = render(
        <App
          router={createMemoryRouter(routes, { initialEntries: [searchPath(ADJI_ID)] })}
          queryClient={createQueryClient()}
          userManager={fakeUserManager(fakeOidcUser())}
        />,
      );
      await screen.findByRole('list', { name: 'Personnes trouvées' });
      expect(screen.queryByRole('link', { name: 'Personnes archivées' })).toBeNull();
      unmount();
    }
  });

  it('lists only archived Persons, searches them, and opens the archived profile', async () => {
    const api = fakeApi({ search: archivedSearch });
    const { router } = renderApp(archivedPeoplePath(ADJI_ID));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Personnes archivées' }),
    ).toBeVisible();
    const results = await screen.findByRole('list', { name: 'Personnes trouvées' });
    expect(within(results).getAllByRole('button')).toHaveLength(1);
    expect(api.searches()[0]?.get('status')).toBe('ARCHIVED');

    await type('Nom', 'Paul');
    await vi.waitFor(() => {
      expect(api.searches().at(-1)?.get('search')).toBe('Paul');
    });
    expect(api.searches().at(-1)?.get('status')).toBe('ARCHIVED');

    fireEvent.click(within(results).getByRole('button', { name: /^Paul Adji/ }));
    expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/persons/${PAUL_ID}`);
  });

  it('says when there is no archived Person, in English', async () => {
    fakeApi({ search: () => jsonResponse(page([])) });
    renderApp(archivedPeoplePath(ADJI_ID));
    await screen.findByRole('heading', { level: 1, name: 'Personnes archivées' });
    await act(() => i18n.changeLanguage('en'));

    expect(await screen.findByText('No archived profile.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Archived people' })).toBeVisible();
  });

  it('is reserved to the ADMIN when reached directly', async () => {
    const api = fakeApi({ search: archivedSearch, role: 'VIEWER' });
    renderApp(archivedPeoplePath(ADJI_ID));

    expect(
      await screen.findByText('Seul un administrateur peut voir les fiches archivées.'),
    ).toBeInTheDocument();
    expect(api.searches()).toHaveLength(0);
  });
});

describe('Add Relative with an existing Person (SCREEN-004)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  it('offers search first, then a new Person, and lists no one before typing', async () => {
    const api = fakeApi();
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'MOTHER', 'profile'));

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Déjà dans la famille ?' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 2, name: 'Ou ajouter une nouvelle personne' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Prénom' })).toBeInTheDocument();
    expect(api.searches()).toHaveLength(0);
  });

  it('links the chosen Person as the mother, without creating anyone', async () => {
    const api = fakeApi();
    const { router } = renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'MOTHER', 'profile'));

    await type('Rechercher dans la famille', 'Eloise');
    fireEvent.click(await screen.findByRole('button', { name: /^Éloïse Ngo/ }));
    expect(screen.queryByRole('textbox', { name: 'Prénom' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Relier Éloïse Ngo à Marie Adji' }));

    expect(
      await screen.findByText('Éloïse Ngo est maintenant relié à Marie Adji.'),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/persons/${MARIE_ID}`);
    expect(api.sent('POST', '/persons')).toHaveLength(0);
    expect(api.sent('POST', '/relationships').map((request) => request.body)).toEqual([
      {
        type: 'PARENT_OF',
        sourcePersonId: ELOISE_ID,
        targetPersonId: MARIE_ID,
        confirmWarnings: false,
      },
    ]);
  });

  it.each([
    ['SON', 'PARENT_OF', MARIE_ID, PAUL_ID],
    ['PARTNER', 'PARTNER_OF', MARIE_ID, PAUL_ID],
  ] as const)(
    'links a %s with the derived relationship',
    async (relation, kind, source, target) => {
      const api = fakeApi();
      renderApp(addRelativePath(ADJI_ID, MARIE_ID, relation, 'tree'));

      await type('Rechercher dans la famille', 'Paul');
      fireEvent.click(await screen.findByRole('button', { name: /^Paul Adji/ }));
      fireEvent.click(screen.getByRole('button', { name: 'Relier Paul Adji à Marie Adji' }));

      expect(
        await screen.findByText('Paul Adji est maintenant relié à Marie Adji.'),
      ).toBeInTheDocument();
      expect(api.sent('POST', '/relationships')[0]?.body).toEqual({
        type: kind,
        sourcePersonId: source,
        targetPersonId: target,
        confirmWarnings: false,
      });
    },
  );

  it('never offers the Person the relative is added to', async () => {
    fakeApi();
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));

    await type('Rechercher dans la famille', 'Adji');

    expect(await screen.findByRole('button', { name: /^Paul Adji/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Marie Adji/ })).not.toBeInTheDocument();
  });

  it('explains a date warning, and links anyway on confirmation', async () => {
    const api = fakeApi({
      relationships: [
        () =>
          problemResponse('RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED', 422, {
            warnings: [
              {
                code: 'PARENT_BORN_AFTER_CHILD',
                context: { parentBirthYear: 1995, childBirthYear: 1990 },
              },
            ],
          }),
      ],
    });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'MOTHER', 'profile'));
    await type('Rechercher dans la famille', 'Eloise');
    fireEvent.click(await screen.findByRole('button', { name: /^Éloïse Ngo/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Relier Éloïse Ngo à Marie Adji' }));

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('Ces dates semblent inhabituelles')).toBeInTheDocument();
    fireEvent.click(within(alert).getByRole('button', { name: 'Ajouter le lien quand même' }));

    await screen.findByText('Éloïse Ngo est maintenant relié à Marie Adji.');
    expect(api.sent('POST', '/relationships').map((request) => request.body)).toEqual([
      expect.objectContaining({ confirmWarnings: false }),
      expect.objectContaining({ confirmWarnings: true }),
    ]);
  });

  it('goes back to the choice from a date warning', async () => {
    fakeApi({
      relationships: [
        () =>
          problemResponse('RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED', 422, {
            warnings: [
              {
                code: 'IMPLAUSIBLE_PARENT_AGE',
                context: { parentBirthYear: 1985, childBirthYear: 1990 },
              },
            ],
          }),
      ],
    });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'MOTHER', 'profile'));
    await type('Rechercher dans la famille', 'Eloise');
    fireEvent.click(await screen.findByRole('button', { name: /^Éloïse Ngo/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Relier Éloïse Ngo à Marie Adji' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Corriger les informations' }));

    expect(
      screen.getByRole('searchbox', { name: 'Rechercher dans la famille' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Prénom' })).toBeInTheDocument();
  });

  it('explains a refused link in family language and lets the User choose again', async () => {
    fakeApi({ relationships: [() => problemResponse('RELATIONSHIP_CREATES_CYCLE', 409)] });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'MOTHER', 'profile'));
    await type('Rechercher dans la famille', 'Eloise');
    fireEvent.click(await screen.findByRole('button', { name: /^Éloïse Ngo/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Relier Éloïse Ngo à Marie Adji' }));

    const alert = await screen.findByRole('alert');
    expect(alert).not.toHaveTextContent('raw server detail');
    fireEvent.click(screen.getByRole('button', { name: "Choisir quelqu'un d'autre" }));

    expect(
      screen.getByRole('searchbox', { name: 'Rechercher dans la famille' }),
    ).toBeInTheDocument();
  });
});
