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

function problemResponse(code: string, status: number) {
  return jsonResponse({ code, status, title: code }, status, 'application/problem+json');
}

const FAMILY_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const TONY = uuid(1);
const MARIE = uuid(2);
const PAUL = uuid(3);
const ALICE = uuid(4);
const AWA = uuid(5);
const KOFI = uuid(6);
const LEA = uuid(7);

type Gender = 'MALE' | 'FEMALE';

function person(id: string, name: string, gender: Gender, relationship: string, birth?: number) {
  return {
    id,
    familyId: FAMILY_ID,
    firstName: name,
    displayName: name,
    gender,
    birth: birth ? { precision: 'YEAR_ONLY', year: birth } : { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: relationship,
    version: 0,
    hasMoreParents: false,
    hasMoreChildren: false,
  };
}

const persons = {
  tony: person(TONY, 'Tony', 'MALE', 'SELF', 2000),
  marie: { ...person(MARIE, 'Marie', 'FEMALE', 'MOTHER', 1975), hasMoreParents: true },
  paul: person(PAUL, 'Paul', 'MALE', 'FATHER', 1972),
  alice: person(ALICE, 'Alice', 'FEMALE', 'SISTER', 2005),
  awa: person(AWA, 'Awa', 'FEMALE', 'PARTNER'),
  kofi: person(KOFI, 'Kofi', 'MALE', 'SON'),
  lea: person(LEA, 'Léa', 'FEMALE', 'GRANDMOTHER', 1950),
};

let edgeCount = 0;
const edge = (type: 'PARENT_OF' | 'PARTNER_OF', source: string, target: string) => ({
  relationshipId: uuid(100 + edgeCount++),
  type,
  sourcePersonId: source,
  targetPersonId: target,
  version: 0,
});

/** Tony: parents Marie and Paul (partners), sister Alice, partner Awa, son Kofi with Awa. */
const tonyTree = {
  focusPersonId: TONY,
  nodes: [persons.tony, persons.paul, persons.marie, persons.alice, persons.awa, persons.kofi],
  edges: [
    edge('PARENT_OF', MARIE, TONY),
    edge('PARENT_OF', PAUL, TONY),
    edge('PARTNER_OF', MARIE, PAUL),
    edge('PARENT_OF', MARIE, ALICE),
    edge('PARTNER_OF', TONY, AWA),
    edge('PARENT_OF', TONY, KOFI),
    edge('PARENT_OF', AWA, KOFI),
  ],
};

/** Marie: mother Léa, partner Paul, children Tony and Alice. */
const marieTree = {
  focusPersonId: MARIE,
  nodes: [persons.marie, persons.lea, persons.paul, persons.tony, persons.alice],
  edges: [
    edge('PARENT_OF', LEA, MARIE),
    edge('PARTNER_OF', MARIE, PAUL),
    edge('PARENT_OF', MARIE, TONY),
    edge('PARENT_OF', PAUL, TONY),
    edge('PARENT_OF', MARIE, ALICE),
  ],
};

const aliceTree = {
  focusPersonId: ALICE,
  nodes: [persons.alice, persons.marie, persons.tony],
  edges: [edge('PARENT_OF', MARIE, ALICE), edge('PARENT_OF', MARIE, TONY)],
};

function family(overrides: Record<string, unknown> = {}) {
  return {
    id: FAMILY_ID,
    name: 'ADJI',
    myRole: 'ADMIN',
    myLinkedPersonId: TONY,
    stats: { personCount: 7, memoryCount: 0, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

type Handler = (request: Request, url: URL) => Response | Promise<Response>;

interface FakeApiOptions {
  familyBody?: unknown;
  defaultTree?: unknown;
  handlers?: Record<string, Handler>;
  locale?: string;
}

const trees: Record<string, unknown> = { [TONY]: tonyTree, [MARIE]: marieTree, [ALICE]: aliceTree };

function fakeApi({
  familyBody = family(),
  defaultTree = tonyTree,
  handlers = {},
  locale = 'fr',
}: FakeApiOptions = {}) {
  const treeRequests: (string | null)[] = [];
  fetchMock.mockImplementation((input) => {
    const request = input as Request;
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/api\/v1/, '');
    if (path === '/me') {
      return Promise.resolve(
        jsonResponse({ id: uuid(99), email: 'tony@mbia.local', preferredLocale: locale }),
      );
    }
    const handler = handlers[`${request.method} ${path}`];
    if (handler) return Promise.resolve(handler(request, url));
    return Promise.resolve(answer(path, url));
  });
  function answer(path: string, url: URL) {
    if (path === `/families/${FAMILY_ID}`) return jsonResponse(familyBody);
    if (path === `/families/${FAMILY_ID}/tree`) {
      const focus = url.searchParams.get('focusPersonId');
      treeRequests.push(focus);
      if (focus === null) return jsonResponse(defaultTree);
      const tree = trees[focus];
      return tree ? jsonResponse(tree) : problemResponse('PERSON_NOT_FOUND', 404);
    }
    return problemResponse('RESOURCE_NOT_FOUND', 404);
  }
  return { treeRequests };
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

const treePath = `/families/${FAMILY_ID}/tree`;
const card = (name: string) => screen.getByRole('button', { name: new RegExp(`^${name}`) });
const roleOf = (name: string) => card(name).parentElement?.dataset.role;

describe('Family tree (SCREEN-003)', () => {
  beforeAll(() => {
    Element.prototype.scrollTo = () => undefined;
  });

  beforeEach(async () => {
    fetchMock.mockReset();
    window.localStorage.clear();
    await act(() => i18n.changeLanguage('fr'));
  });

  it('draws parents, the focus with partners and children, and a siblings chip', async () => {
    fakeApi();
    renderApp(treePath);

    expect(await screen.findByRole('heading', { level: 1, name: 'Arbre familial' })).toBeVisible();
    await screen.findByRole('button', { name: /^Tony/ });
    expect(card('Tony')).toHaveAttribute('aria-current', 'true');
    expect(roleOf('Tony')).toBe('focus');
    expect(roleOf('Marie')).toBe('parent');
    expect(roleOf('Paul')).toBe('parent');
    expect(roleOf('Awa')).toBe('partner');
    expect(roleOf('Kofi')).toBe('child');
    expect(card('Tony')).toHaveTextContent('2000 –');
    // Siblings are not drawn as cards.
    expect(screen.queryByRole('button', { name: /^Alice/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Frères et sœurs (1)' })).toBeVisible();
    expect(
      within(card('Marie')).getByText('A des parents au-delà de cette vue'),
    ).toBeInTheDocument();
    // Two parents already: no "Add a parent" slot; partner and child slots for an ADMIN.
    expect(screen.queryByRole('button', { name: 'Ajouter un parent' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ajouter un ou une partenaire' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Ajouter un enfant' })).toBeVisible();
    expect(window.localStorage.getItem(`mbia.tree.focus.${FAMILY_ID}`)).toBe(TONY);
  });

  it('opens the Quick View, then recenters the tree and remembers the focus', async () => {
    const api = fakeApi();
    const { router } = renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: /^Marie/ }));

    const quickView = screen.getByRole('dialog', { name: 'Marie' });
    expect(within(quickView).getByText('Votre mère')).toBeVisible();
    expect(within(quickView).getByText('1975 –')).toBeVisible();
    expect(await within(quickView).findByText('2 enfants')).toBeVisible();
    expect(within(quickView).queryByText(/souvenir/i)).not.toBeInTheDocument();
    expect(within(quickView).getByRole('link', { name: 'Voir le profil' })).toHaveAttribute(
      'href',
      `/families/${FAMILY_ID}/persons/${MARIE}`,
    );

    fireEvent.click(within(quickView).getByRole('button', { name: "Centrer l'arbre sur Marie" }));

    expect(await screen.findByRole('button', { name: /^Léa/ })).toBeVisible();
    expect(roleOf('Marie')).toBe('focus');
    expect(roleOf('Tony')).toBe('child');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(router.state.location.search).toBe(`?focus=${MARIE}`);
    expect(api.treeRequests).toContain(MARIE);
    expect(window.localStorage.getItem(`mbia.tree.focus.${FAMILY_ID}`)).toBe(MARIE);
  });

  it('recenters from the siblings list, keeping the current tree until the next one arrives', async () => {
    let release: ((response: Response) => void) | undefined;
    fakeApi({
      handlers: {
        [`GET /families/${FAMILY_ID}/tree`]: (_, url) => {
          const focus = url.searchParams.get('focusPersonId');
          if (focus !== ALICE) return jsonResponse(tonyTree);
          return new Promise((resolve) => {
            release = resolve;
          });
        },
      },
    });
    renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: 'Frères et sœurs (1)' }));

    const list = screen.getByRole('dialog', { name: 'Frères et sœurs' });
    fireEvent.click(within(list).getByRole('button', { name: /Alice/ }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await vi.waitFor(() => {
      expect(release).toBeDefined();
    });
    expect(card('Tony')).toHaveAttribute('aria-current', 'true');
    release?.(jsonResponse(aliceTree));
    await screen.findByRole('button', { name: /^Alice/ });
    expect(roleOf('Alice')).toBe('focus');
  });

  it('explains a relationship with "See how"', async () => {
    fakeApi({
      handlers: {
        [`GET /families/${FAMILY_ID}/kinship`]: (_, url) => {
          expect(url.searchParams.get('from')).toBe(TONY);
          expect(url.searchParams.get('to')).toBe(LEA);
          return jsonResponse({
            fromPersonId: TONY,
            toPersonId: LEA,
            relationship: 'GRANDMOTHER',
            path: [
              { fromPersonId: TONY, toPersonId: MARIE, relation: 'PARENT' },
              { fromPersonId: MARIE, toPersonId: LEA, relation: 'PARENT' },
            ],
          });
        },
      },
    });
    renderApp(`${treePath}?focus=${MARIE}`);
    fireEvent.click(await screen.findByRole('button', { name: /^Léa/ }));

    const quickView = screen.getByRole('dialog', { name: 'Léa' });
    expect(within(quickView).getByText('Votre grand-mère')).toBeVisible();
    fireEvent.click(within(quickView).getByRole('button', { name: 'Voir le lien' }));

    const steps = await within(quickView).findAllByRole('listitem');
    expect(steps.map((step) => step.textContent)).toEqual([
      'Marie est la mère de Tony',
      'Léa est la mère de Marie',
    ]);
  });

  it('offers no "See how" for the User themselves', async () => {
    fakeApi();
    renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: /^Tony/ }));
    const quickView = screen.getByRole('dialog', { name: 'Tony' });
    expect(within(quickView).getByText('Vous')).toBeVisible();
    expect(within(quickView).queryByRole('button', { name: 'Voir le lien' })).toBeNull();
    // Already the focus: nothing to recenter.
    expect(within(quickView).queryByRole('button', { name: /Centrer/ })).toBeNull();
  });

  it('closes the Quick View with Escape', async () => {
    fakeApi();
    renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: /^Awa/ }));
    expect(screen.getByRole('dialog', { name: 'Awa' })).toBeVisible();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('opens the linked Person first, even with a remembered focus (OQ-018)', async () => {
    window.localStorage.setItem(`mbia.tree.focus.${FAMILY_ID}`, MARIE);
    const api = fakeApi();
    renderApp(treePath);
    await screen.findByRole('button', { name: /^Tony/ });
    expect(api.treeRequests).toEqual([null]);
  });

  it('opens the remembered focus when the User has no linked Person', async () => {
    window.localStorage.setItem(`mbia.tree.focus.${FAMILY_ID}`, MARIE);
    const api = fakeApi({ familyBody: family({ myLinkedPersonId: null }) });
    renderApp(treePath);
    await screen.findByRole('button', { name: /^Léa/ });
    expect(api.treeRequests[0]).toBe(MARIE);
  });

  it('forgets a remembered focus that no longer exists', async () => {
    window.localStorage.setItem(`mbia.tree.focus.${FAMILY_ID}`, uuid(404));
    const api = fakeApi({ familyBody: family({ myLinkedPersonId: null }) });
    renderApp(treePath);
    await screen.findByRole('button', { name: /^Tony/ });
    expect(api.treeRequests).toEqual([uuid(404), null]);
    expect(window.localStorage.getItem(`mbia.tree.focus.${FAMILY_ID}`)).toBe(TONY);
  });

  it('offers the gendered choices of an "Add a child" slot, back to the tree', async () => {
    fakeApi();
    renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: 'Ajouter un enfant' }));
    const choices = screen.getByRole('dialog', { name: 'Ajouter un enfant' });
    expect(within(choices).getByRole('link', { name: 'Fils' })).toHaveAttribute(
      'href',
      `/families/${FAMILY_ID}/persons/new?relativeOf=${TONY}&relation=SON&from=tree`,
    );
    expect(within(choices).getAllByRole('link')).toHaveLength(3);
  });

  it('shows no add action to a VIEWER', async () => {
    fakeApi({ familyBody: family({ myRole: 'VIEWER' }) });
    renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: /^Kofi/ }));
    expect(screen.queryByRole('button', { name: /^Ajouter/ })).not.toBeInTheDocument();
    expect(
      within(screen.getByRole('dialog', { name: 'Kofi' })).queryByRole('button', {
        name: 'Ajouter…',
      }),
    ).toBeNull();
  });

  it('shows the Family Home empty state for a Family without Persons', async () => {
    fakeApi({
      familyBody: family({ myLinkedPersonId: null, stats: { personCount: 0, memoryCount: 0 } }),
      defaultTree: { focusPersonId: null, nodes: [], edges: [] },
    });
    renderApp(treePath);
    expect(
      await screen.findByRole('heading', { name: 'Bienvenue dans la famille ADJI' }),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: 'Commencer par moi' })).toBeVisible();
  });

  it('zooms in and out within limits', async () => {
    fakeApi();
    renderApp(treePath);
    await screen.findByRole('button', { name: /^Tony/ });
    const zoomOut = screen.getByRole('button', { name: 'Dézoomer' });
    fireEvent.click(zoomOut);
    fireEvent.click(zoomOut);
    expect(zoomOut).toBeDisabled();
    const zoomIn = screen.getByRole('button', { name: 'Zoomer' });
    for (let i = 0; i < 4; i++) fireEvent.click(zoomIn);
    expect(zoomIn).toBeDisabled();
  });

  it('is reached from the Family Home "View family tree" action', async () => {
    fakeApi();
    const { router } = renderApp(`/families/${FAMILY_ID}`);
    fireEvent.click(await screen.findByRole('link', { name: "Voir l'arbre familial" }));
    expect(router.state.location.pathname).toBe(treePath);
    expect(await screen.findByRole('button', { name: /^Tony/ })).toBeVisible();
  });

  it('speaks English too', async () => {
    await act(() => i18n.changeLanguage('en'));
    fakeApi({ locale: 'en' });
    renderApp(treePath);
    fireEvent.click(await screen.findByRole('button', { name: /^Marie/ }));
    const quickView = screen.getByRole('dialog', { name: 'Marie' });
    expect(within(quickView).getByText('Your mother')).toBeVisible();
    expect(await within(quickView).findByText('2 children')).toBeVisible();
    expect(
      within(quickView).getByRole('button', { name: 'Center the tree on Marie' }),
    ).toBeVisible();
  });
});
