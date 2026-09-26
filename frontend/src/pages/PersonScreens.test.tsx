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
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail' },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const PROFILE = `/families/${ADJI_ID}/persons/${MARIE_ID}`;

function family(myRole = 'ADMIN') {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    stats: { personCount: 1, memoryCount: 0, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function person(overrides: Record<string, unknown> = {}) {
  return {
    id: MARIE_ID,
    familyId: ADJI_ID,
    firstName: 'Marie',
    middleNames: 'Jeanne',
    lastName: 'Adji',
    preferredName: null,
    displayName: 'Marie Adji',
    gender: 'FEMALE',
    birth: { precision: 'EXACT', date: '1954-03-12' },
    isDeceased: true,
    death: { precision: 'YEAR_ONLY', year: 2020 },
    status: 'ACTIVE',
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 2,
    biography: 'Institutrice à Ebolowa.',
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

/** A tree node, as returned by `GET /tree` (PersonSummary + continuation indicators). */
function treeNode(id: string, firstName: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    familyId: ADJI_ID,
    firstName,
    displayName: firstName,
    gender: 'UNKNOWN',
    birth: { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 0,
    hasMoreParents: false,
    hasMoreChildren: false,
    ...overrides,
  };
}

function parentOf(source: string, target: string) {
  return {
    relationshipId: `${source}-${target}`,
    type: 'PARENT_OF',
    sourcePersonId: source,
    targetPersonId: target,
    version: 0,
  };
}

function partners(source: string, target: string) {
  return {
    relationshipId: `${source}-${target}`,
    type: 'PARTNER_OF',
    sourcePersonId: source,
    targetPersonId: target,
    version: 0,
  };
}

/** Marie alone: no relative yet. */
function lonelyTree() {
  return { focusPersonId: MARIE_ID, nodes: [treeNode(MARIE_ID, 'Marie')], edges: [] };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`). */
function fakeApi(handlers: Record<string, Handler>) {
  const requests: Request[] = [];
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({ id: 'u1', email: 'alice@mbia.local', preferredLocale: 'fr' });
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    all: () => requests,
    patches: () => requests.filter((request) => request.method === 'PATCH'),
    claims: () => requests.filter((request) => new URL(request.url).pathname.endsWith('/claim')),
    trees: () => requests.filter((request) => new URL(request.url).pathname.endsWith('/tree')),
    removals: () => requests.filter((request) => request.method === 'DELETE'),
    restores: () =>
      requests.filter((request) => new URL(request.url).pathname.endsWith('/restore')),
    archivedLists: () =>
      requests.filter((request) =>
        new URL(request.url).pathname.endsWith('/archived-relationships'),
      ),
  };
}

function personApi({
  role = 'ADMIN',
  get = () => jsonResponse(person()),
  patch,
  claim,
  unclaim,
  tree = () => jsonResponse(lonelyTree()),
  archived = () => jsonResponse([]),
  other = {},
}: {
  role?: string;
  get?: Handler;
  patch?: Handler;
  claim?: Handler;
  unclaim?: Handler;
  tree?: Handler;
  archived?: Handler;
  other?: Record<string, Handler>;
} = {}) {
  return fakeApi({
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/persons/${MARIE_ID}`]: get,
    [`GET /families/${ADJI_ID}/tree`]: tree,
    [`GET /families/${ADJI_ID}/persons/${MARIE_ID}/archived-relationships`]: archived,
    ...other,
    ...(patch ? { [`PATCH /families/${ADJI_ID}/persons/${MARIE_ID}`]: patch } : {}),
    ...(claim ? { [`POST /families/${ADJI_ID}/persons/${MARIE_ID}/claim`]: claim } : {}),
    ...(unclaim ? { [`DELETE /families/${ADJI_ID}/persons/${MARIE_ID}/claim`]: unclaim } : {}),
  });
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

function renderAppWithUnmount(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  return render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
}

function type(name: string, value: string) {
  fireEvent.change(screen.getByRole('textbox', { name }), { target: { value } });
}

describe('Person screens', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('Person profile (SCREEN-005)', () => {
    it('shows the header and About, with an empty Family section and no Memories', async () => {
      personApi();
      renderApp(PROFILE);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Marie Adji' }),
      ).toBeInTheDocument();
      expect(screen.getByText('Naissance : 1954')).toBeInTheDocument();
      expect(screen.getByText('Décès : 2020')).toBeInTheDocument();
      expect(screen.getByRole('heading', { level: 2, name: 'Famille' })).toBeInTheDocument();
      expect(
        await screen.findByText("Aucun proche n'est encore relié à cette personne."),
      ).toBeInTheDocument();
      const about = screen.getByRole('region', { name: 'À propos' });
      expect(within(about).getByText('Jeanne')).toBeInTheDocument();
      expect(within(about).getByText('12 mars 1954')).toBeInTheDocument();
      expect(within(about).getByText('2020')).toBeInTheDocument();
      expect(within(about).getByText('Institutrice à Ebolowa.')).toBeInTheDocument();
      expect(screen.queryByText(/souvenir|photo/i)).not.toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Modifier' })).toHaveAttribute(
        'href',
        `${PROFILE}/edit`,
      );
    });

    it('lists parents, partners, children and siblings with what they are to me', async () => {
      const [jeanne, paul, andre, tony, awa, samuel, zoe] = [
        'a0000000-0000-4000-8000-000000000001',
        'a0000000-0000-4000-8000-000000000002',
        'a0000000-0000-4000-8000-000000000003',
        'a0000000-0000-4000-8000-000000000004',
        'a0000000-0000-4000-8000-000000000005',
        'a0000000-0000-4000-8000-000000000006',
        'a0000000-0000-4000-8000-000000000007',
      ];
      const requests = personApi({
        tree: () =>
          jsonResponse({
            focusPersonId: MARIE_ID,
            nodes: [
              treeNode(MARIE_ID, 'Marie', { relationshipToCurrentUser: 'MOTHER' }),
              treeNode(jeanne, 'Jeanne', {
                gender: 'FEMALE',
                relationshipToCurrentUser: 'GRANDMOTHER',
                birth: { precision: 'YEAR_ONLY', year: 1930 },
                isDeceased: true,
                death: { precision: 'YEAR_ONLY', year: 2001 },
              }),
              treeNode(paul, 'Paul', { gender: 'MALE', relationshipToCurrentUser: 'GRANDFATHER' }),
              treeNode(andre, 'André', { gender: 'MALE', relationshipToCurrentUser: 'FATHER' }),
              treeNode(samuel, 'Samuel', {
                gender: 'MALE',
                relationshipToCurrentUser: 'NONE_KNOWN',
              }),
              treeNode(tony, 'Tony', { gender: 'MALE', relationshipToCurrentUser: 'SELF' }),
              treeNode(awa, 'Awa', { gender: 'FEMALE', relationshipToCurrentUser: 'SISTER' }),
              treeNode(zoe, 'Zoé', { gender: 'FEMALE', relationshipToCurrentUser: 'RELATED' }),
            ],
            edges: [
              parentOf(jeanne, MARIE_ID),
              partners(andre, MARIE_ID),
              parentOf(MARIE_ID, tony),
              parentOf(paul, MARIE_ID),
              partners(MARIE_ID, samuel),
              parentOf(MARIE_ID, awa),
              parentOf(paul, zoe),
            ],
          }),
      });
      renderApp(PROFILE);

      // Each row: avatar initial, name, badge, years.
      const parents = await screen.findByRole('list', { name: 'Parents' });
      expect(
        within(parents)
          .getAllByRole('link')
          .map((link) => link.textContent),
      ).toEqual(['JJeanneVotre grand-mèreNaissance : 1930Décès : 2001', 'PPaulVotre grand-père']);
      expect(within(parents).getByRole('link', { name: /Jeanne/ })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/${jeanne}`,
      );
      const partnersList = screen.getByRole('list', { name: 'Partenaires' });
      // In relationship creation order; no badge for NONE_KNOWN.
      expect(
        within(partnersList)
          .getAllByRole('link')
          .map((link) => link.textContent),
      ).toEqual(['AAndréVotre père', 'SSamuel']);
      const children = screen.getByRole('list', { name: 'Enfants' });
      expect(
        within(children)
          .getAllByRole('link')
          .map((link) => link.textContent),
      ).toEqual(['TTonyVous', 'AAwaVotre sœur']);
      const siblings = screen.getByRole('list', { name: 'Frères et sœurs' });
      expect(within(siblings).getByText('Membre de votre famille')).toBeInTheDocument();
      expect(screen.queryByText("Aucun proche n'est encore relié à cette personne.")).toBeNull();
      expect(requests.trees()[0]?.url).toContain(`focusPersonId=${MARIE_ID}`);

      await act(() => i18n.changeLanguage('en'));
      expect(await screen.findByRole('list', { name: 'Siblings' })).toBeInTheDocument();
      expect(screen.getByRole('list', { name: 'Partners' })).toBeInTheDocument();
      expect(screen.getByRole('list', { name: 'Children' })).toBeInTheDocument();
      expect(screen.getByText('Your grandmother')).toBeInTheDocument();
    });

    it('lists no relative when the tree is not centred on the Person', async () => {
      personApi({
        tree: () =>
          jsonResponse({
            focusPersonId: 'a0000000-0000-4000-8000-000000000001',
            nodes: [treeNode('a0000000-0000-4000-8000-000000000001', 'Tony')],
            edges: [],
          }),
      });
      renderApp(PROFILE);

      expect(
        await screen.findByText("Aucun proche n'est encore relié à cette personne."),
      ).toBeInTheDocument();
      expect(screen.queryByText('Tony')).toBeNull();
    });

    it('does not load the relatives of an archived Person', async () => {
      const requests = personApi({ get: () => jsonResponse(person({ status: 'ARCHIVED' })) });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('heading', { level: 2, name: 'Famille' })).toBeNull();
      expect(requests.trees()).toHaveLength(0);
    });

    it('translates an error while loading the relatives', async () => {
      personApi({ tree: () => problemResponse('VALIDATION_FAILED', 400) });
      renderApp(PROFILE);

      expect(await screen.findByRole('button', { name: /réessayer/i })).toBeInTheDocument();
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });

    it('shows the real name next to a preferred name, and unknown dates in English', async () => {
      personApi({
        get: () =>
          jsonResponse(
            person({
              preferredName: 'Mamie',
              displayName: 'Mamie',
              middleNames: null,
              birth: { precision: 'UNKNOWN' },
              death: { precision: 'UNKNOWN' },
              relationshipToCurrentUser: 'SELF',
            }),
          ),
      });
      renderApp(PROFILE);
      await screen.findByRole('heading', { level: 1, name: 'Mamie' });
      await act(() => i18n.changeLanguage('en'));

      expect(await screen.findByText('You')).toBeInTheDocument();
      expect(screen.getByText('Death: Unknown')).toBeInTheDocument();
      const about = screen.getByRole('region', { name: 'About' });
      expect(within(about).getByText('Marie Adji')).toBeInTheDocument();
      expect(within(about).getAllByText('Unknown')).toHaveLength(2);
    });

    it('explains that no relationship is known yet', async () => {
      personApi({ get: () => jsonResponse(person({ relationshipToCurrentUser: 'NONE_KNOWN' })) });
      renderApp(PROFILE);

      expect(
        await screen.findByText("Aucun lien connu dans Mbia pour l'instant"),
      ).toBeInTheDocument();
    });

    it('shows what the Person is to the current User, in both languages', async () => {
      personApi({ get: () => jsonResponse(person({ relationshipToCurrentUser: 'GRANDMOTHER' })) });
      renderApp(PROFILE);

      expect(await screen.findByText('Votre grand-mère')).toBeInTheDocument();
      await act(() => i18n.changeLanguage('en'));
      expect(await screen.findByText('Your grandmother')).toBeInTheDocument();
    });

    it("genders a first cousin by the Person's gender", async () => {
      personApi({
        get: () =>
          jsonResponse(person({ relationshipToCurrentUser: 'FIRST_COUSIN', gender: 'FEMALE' })),
      });
      renderApp(PROFILE);

      expect(await screen.findByText('Votre cousine')).toBeInTheDocument();
    });

    it('does not offer Edit to a VIEWER', async () => {
      personApi({ role: 'VIEWER' });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
    });

    it('offers This is me to a VIEWER without a linked Person, then shows the link', async () => {
      let claimed = false;
      const api = personApi({
        role: 'VIEWER',
        get: () =>
          jsonResponse(
            claimed
              ? person({ linkedUserId: 'u1', relationshipToCurrentUser: 'SELF', version: 3 })
              : person(),
          ),
        claim: () => {
          claimed = true;
          return jsonResponse(
            person({ linkedUserId: 'u1', relationshipToCurrentUser: 'SELF', version: 3 }),
          );
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: "C'est moi" }));

      expect(
        await screen.findByText('Vous êtes maintenant relié à cette personne.'),
      ).toBeInTheDocument();
      expect(screen.getByText('Vous')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: "Ce n'est pas moi" })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
      const [request] = api.claims();
      expect(request?.method).toBe('POST');
      expect(request?.headers.get('If-Match')).toBe('"2"');
    });

    it('unlinks the current User from their own Person', async () => {
      let released = false;
      const api = personApi({
        role: 'CONTRIBUTOR',
        get: () =>
          jsonResponse(
            released
              ? person({ version: 3 })
              : person({ linkedUserId: 'u1', relationshipToCurrentUser: 'SELF' }),
          ),
        unclaim: () => {
          released = true;
          return jsonResponse(person({ version: 3 }));
        },
      });
      renderApp(PROFILE);

      expect(await screen.findByRole('link', { name: 'Modifier' })).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: "Ce n'est pas moi" }));

      expect(
        await screen.findByText("Cette personne n'est plus reliée à vous."),
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: "C'est moi" })).toBeInTheDocument();
      expect(api.claims()[0]?.method).toBe('DELETE');
      expect(api.claims()[0]?.headers.get('If-Match')).toBe('"2"');
    });

    it('protects a Person linked to another member from a CONTRIBUTOR', async () => {
      personApi({
        role: 'CONTRIBUTOR',
        get: () =>
          jsonResponse(person({ linkedUserId: 'u2', relationshipToCurrentUser: 'NONE_KNOWN' })),
      });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: "C'est moi" })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: "Ce n'est pas moi" })).not.toBeInTheDocument();
    });

    it('lets an ADMIN edit a Person linked to another member', async () => {
      personApi({
        get: () =>
          jsonResponse(person({ linkedUserId: 'u2', relationshipToCurrentUser: 'NONE_KNOWN' })),
      });
      renderApp(PROFILE);

      expect(await screen.findByRole('link', { name: 'Modifier' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: "C'est moi" })).not.toBeInTheDocument();
    });

    it('does not offer This is me to a User who already has a linked Person', async () => {
      personApi({ get: () => jsonResponse(person({ relationshipToCurrentUser: 'NONE_KNOWN' })) });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('button', { name: "C'est moi" })).not.toBeInTheDocument();
    });

    it('translates a refused claim', async () => {
      personApi({ claim: () => problemResponse('PERSON_ALREADY_CLAIMED', 409) });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: "C'est moi" }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Cette personne est déjà reliée à un autre membre de la famille.',
      );
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });

    it('shows a friendly page for a Person of no or another Family', async () => {
      personApi({ get: () => problemResponse('PERSON_NOT_FOUND', 404) });
      renderApp(PROFILE);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Personne introuvable' }),
      ).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Retour à la famille' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}`,
      );
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });
  });

  describe('Remove and restore links (SCREEN-005, SCREEN-COMPONENT-003)', () => {
    const PAUL = 'b0000000-0000-4000-8000-000000000001';
    const CHLOE = 'b0000000-0000-4000-8000-000000000002';
    const TONY = 'b0000000-0000-4000-8000-000000000003';
    const AWA = 'b0000000-0000-4000-8000-000000000004';
    const JEANNE = 'b0000000-0000-4000-8000-000000000005';

    /** Paul father of Marie, Chloé her partner, Tony her son, Awa her sister (through Paul). */
    function marieTree() {
      return {
        focusPersonId: MARIE_ID,
        nodes: [
          treeNode(MARIE_ID, 'Marie'),
          treeNode(PAUL, 'Paul', { gender: 'MALE' }),
          treeNode(CHLOE, 'Chloé'),
          treeNode(TONY, 'Tony'),
          treeNode(AWA, 'Awa'),
        ],
        edges: [
          { ...parentOf(PAUL, MARIE_ID), relationshipId: 'rel-paul', version: 4 },
          partners(CHLOE, MARIE_ID),
          parentOf(MARIE_ID, TONY),
          parentOf(PAUL, AWA),
        ],
      };
    }

    function removedLink(overrides: Record<string, unknown> = {}) {
      return {
        id: 'rel-jeanne',
        type: 'PARENT_OF',
        sourcePersonId: JEANNE,
        targetPersonId: MARIE_ID,
        version: 1,
        archivedAt: '2026-09-20T10:00:00Z',
        relatedPerson: treeNode(JEANNE, 'Jeanne', { gender: 'FEMALE' }),
        ...overrides,
      };
    }

    function restored(warnings: unknown[] = []) {
      return jsonResponse({
        id: 'rel-jeanne',
        familyId: ADJI_ID,
        type: 'PARENT_OF',
        sourcePersonId: JEANNE,
        targetPersonId: MARIE_ID,
        status: 'ACTIVE',
        warnings,
        version: 2,
        createdAt: '2026-09-01T10:00:00Z',
      });
    }

    it.each(['ADMIN', 'CONTRIBUTOR'])(
      'offers Remove link on parents, partners and children to %s, not on siblings',
      async (role) => {
        personApi({ role, tree: () => jsonResponse(marieTree()) });
        renderApp(PROFILE);

        for (const name of ['Paul', 'Chloé', 'Tony']) {
          expect(
            await screen.findByRole('button', { name: `Retirer le lien avec ${name}` }),
          ).toBeInTheDocument();
        }
        expect(screen.queryByRole('button', { name: 'Retirer le lien avec Awa' })).toBeNull();
      },
    );

    it('offers no Remove link to a VIEWER', async () => {
      personApi({ role: 'VIEWER', tree: () => jsonResponse(marieTree()) });
      renderApp(PROFILE);

      await screen.findByRole('list', { name: 'Parents' });
      expect(screen.queryByRole('button', { name: /Retirer le lien/ })).toBeNull();
    });

    it('confirms, removes the link with its version, then refreshes the relatives', async () => {
      let removed = false;
      const api = personApi({
        tree: () =>
          jsonResponse(
            removed
              ? { ...marieTree(), nodes: marieTree().nodes.filter((n) => n.id !== PAUL), edges: [] }
              : marieTree(),
          ),
        other: {
          [`DELETE /families/${ADJI_ID}/relationships/rel-paul`]: () => {
            removed = true;
            return new Response(null, { status: 204 });
          },
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Retirer le lien avec Paul' }));
      const dialog = screen.getByRole('dialog', {
        name: 'Retirer ce lien peut modifier les liens de parenté calculés par Mbia.',
      });
      fireEvent.click(within(dialog).getByRole('button', { name: 'Annuler' }));
      expect(screen.queryByRole('dialog')).toBeNull();
      expect(api.removals()).toHaveLength(0);

      fireEvent.click(screen.getByRole('button', { name: 'Retirer le lien avec Paul' }));
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'Retirer le lien' }),
      );

      expect(await screen.findByText('Le lien avec Paul a été retiré.')).toBeInTheDocument();
      expect(screen.queryByRole('dialog')).toBeNull();
      expect(api.removals()[0]?.headers.get('If-Match')).toBe('"4"');
      expect(
        await screen.findByText("Aucun proche n'est encore relié à cette personne."),
      ).toBeVisible();
      expect(api.trees().length).toBeGreaterThan(1);
    });

    it('translates a refused removal in the confirmation', async () => {
      personApi({
        tree: () => jsonResponse(marieTree()),
        other: {
          [`DELETE /families/${ADJI_ID}/relationships/rel-paul`]: () =>
            problemResponse('CONCURRENT_MODIFICATION', 409),
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Retirer le lien avec Paul' }));
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'Retirer le lien' }),
      );

      expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent(
        "Quelqu'un a modifié ces informations entre-temps.",
      );
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });

    it('shows the removed links to an ADMIN, collapsed, in French then English', async () => {
      personApi({
        archived: () =>
          jsonResponse([
            removedLink(),
            removedLink({
              id: 'rel-tony',
              sourcePersonId: MARIE_ID,
              targetPersonId: TONY,
              relatedPerson: treeNode(TONY, 'Tony', { gender: 'MALE', status: 'ARCHIVED' }),
            }),
          ]),
      });
      renderApp(PROFILE);

      const list = await screen.findByRole('list', { name: 'Liens retirés' });
      expect(list.closest('details')).not.toHaveAttribute('open');
      const [jeanne, tony] = within(list).getAllByRole('listitem');
      if (jeanne === undefined) throw new Error('Jeanne is not listed');
      expect(jeanne).toHaveTextContent('Jeanne est la mère de Marie Adji');
      expect(jeanne).toHaveTextContent('Retiré le 20 septembre 2026');
      expect(within(jeanne).getByRole('link', { name: 'Jeanne' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/${JEANNE}`,
      );
      expect(tony).toHaveTextContent('Fiche archivée');
      expect(tony).toHaveTextContent('Tony est le fils de Marie Adji');

      await act(() => i18n.changeLanguage('en'));
      expect(await screen.findByRole('list', { name: 'Removed links' })).toHaveTextContent(
        "Jeanne is Marie Adji's mother",
      );
      expect(screen.getByText('Archived profile')).toBeInTheDocument();
      expect(screen.getAllByText('Removed on September 20, 2026')).toHaveLength(2);
    });

    it('hides the removed links when there is nothing to restore', async () => {
      personApi();
      renderApp(PROFILE);

      await screen.findByText("Aucun proche n'est encore relié à cette personne.");
      expect(screen.queryByText('Liens retirés')).toBeNull();
    });

    it.each(['CONTRIBUTOR', 'VIEWER'])('shows no removed links to a %s', async (role) => {
      const api = personApi({ role, archived: () => jsonResponse([removedLink()]) });
      renderApp(PROFILE);

      await screen.findByText("Aucun proche n'est encore relié à cette personne.");
      expect(screen.queryByText('Liens retirés')).toBeNull();
      expect(api.archivedLists()).toHaveLength(0);
    });

    it('restores a removed link with its version', async () => {
      let isRestored = false;
      const api = personApi({
        archived: () => jsonResponse(isRestored ? [] : [removedLink()]),
        other: {
          [`POST /families/${ADJI_ID}/relationships/rel-jeanne/restore`]: () => {
            isRestored = true;
            return restored();
          },
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Restaurer le lien avec Jeanne' }));

      expect(await screen.findByText('Le lien a été restauré.')).toBeInTheDocument();
      expect(api.restores()[0]?.headers.get('If-Match')).toBe('"1"');
      await vi.waitFor(() => {
        expect(screen.queryByRole('list', { name: 'Liens retirés' })).toBeNull();
      });
      expect(api.trees().length).toBeGreaterThan(1);
    });

    it('shows only the feedback of the last action after a removal then a restore', async () => {
      personApi({
        tree: () => jsonResponse(marieTree()),
        archived: () => jsonResponse([removedLink()]),
        other: {
          [`DELETE /families/${ADJI_ID}/relationships/rel-paul`]: () =>
            new Response(null, { status: 204 }),
          [`POST /families/${ADJI_ID}/relationships/rel-jeanne/restore`]: () => restored(),
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Retirer le lien avec Paul' }));
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'Retirer le lien' }),
      );
      expect(await screen.findByRole('status')).toHaveTextContent(
        'Le lien avec Paul a été retiré.',
      );
      fireEvent.click(screen.getByRole('button', { name: 'Restaurer le lien avec Jeanne' }));

      expect(await screen.findByText('Le lien a été restauré.')).toBeInTheDocument();
      expect(screen.getAllByRole('status')).toHaveLength(1);
    });

    it('says when the restored link has unusual birth dates', async () => {
      personApi({
        archived: () => jsonResponse([removedLink()]),
        other: {
          [`POST /families/${ADJI_ID}/relationships/rel-jeanne/restore`]: () =>
            restored([
              {
                code: 'PARENT_BORN_AFTER_CHILD',
                context: { parentBirthYear: 1995, childBirthYear: 1990 },
              },
            ]),
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Restaurer le lien avec Jeanne' }));

      expect(
        await screen.findByText(/Le lien a été restauré\. Les dates de naissance/),
      ).toBeInTheDocument();
    });

    it.each([
      [
        'PERSON_NOT_ACTIVE',
        'Ce lien ne peut pas être restauré : la fiche de Jeanne a été archivée ou fusionnée.',
      ],
      [
        'RELATIONSHIP_CREATES_CYCLE',
        'Ce lien ne peut pas être restauré : avec les liens actuels, une personne deviendrait son propre ancêtre.',
      ],
      [
        'RELATIONSHIP_ALREADY_EXISTS',
        'Ce lien ne peut pas être restauré : ces deux personnes sont déjà reliées de cette façon.',
      ],
      [
        'CONCURRENT_MODIFICATION',
        "Quelqu'un a modifié ces informations entre-temps. Rechargez la page et réessayez.",
      ],
    ])('explains a refused restore (%s)', async (code, message) => {
      personApi({
        archived: () => jsonResponse([removedLink()]),
        other: {
          [`POST /families/${ADJI_ID}/relationships/rel-jeanne/restore`]: () =>
            problemResponse(code, 409),
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Restaurer le lien avec Jeanne' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(message);
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
      expect(screen.getByRole('list', { name: 'Liens retirés' })).toBeInTheDocument();
    });
  });

  describe('Archive and restore a Person (SCREEN-005, PR-26)', () => {
    const ARCHIVE = `POST /families/${ADJI_ID}/persons/${MARIE_ID}/archive`;
    const RESTORE = `POST /families/${ADJI_ID}/persons/${MARIE_ID}/restore`;

    function personStatusRequests() {
      return fetchMock.mock.calls
        .map(([input]) => input as Request)
        .filter((request) =>
          /\/persons\/[^/]+\/(archive|restore)$/.test(new URL(request.url).pathname),
        );
    }

    it('offers Archive to the ADMIN only', async () => {
      for (const role of ['CONTRIBUTOR', 'VIEWER']) {
        personApi({ role });
        const { unmount } = renderAppWithUnmount(PROFILE);
        await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
        expect(screen.queryByRole('button', { name: 'Archiver cette fiche' })).toBeNull();
        unmount();
      }

      personApi({ role: 'ADMIN' });
      renderApp(PROFILE);
      expect(await screen.findByRole('button', { name: 'Archiver cette fiche' })).toBeVisible();
    });

    it('does not offer Archive on a Person linked to a member', async () => {
      personApi({ get: () => jsonResponse(person({ linkedUserId: 'u2' })) });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('button', { name: 'Archiver cette fiche' })).toBeNull();
    });

    it('confirms, archives with the version, then shows the archived profile', async () => {
      let archived = false;
      personApi({
        get: () => jsonResponse(person(archived ? { status: 'ARCHIVED', version: 3 } : {})),
        other: {
          [ARCHIVE]: () => {
            archived = true;
            return jsonResponse(person({ status: 'ARCHIVED', version: 3 }));
          },
        },
      });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Archiver cette fiche' }));
      const dialog = screen.getByRole('dialog', {
        name: "Marie Adji n'apparaîtra plus dans l'arbre ni dans la recherche. Un administrateur pourra restaurer cette fiche à tout moment.",
      });
      fireEvent.click(within(dialog).getByRole('button', { name: 'Annuler' }));
      expect(screen.queryByRole('dialog')).toBeNull();
      expect(personStatusRequests()).toHaveLength(0);

      fireEvent.click(screen.getByRole('button', { name: 'Archiver cette fiche' }));
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archiver' }));

      expect(await screen.findByText('La fiche de Marie Adji a été archivée.')).toBeInTheDocument();
      expect(screen.queryByRole('dialog')).toBeNull();
      expect(
        screen.getByText(
          "Cette fiche est archivée. Elle n'apparaît plus dans l'arbre ni dans la recherche.",
        ),
      ).toBeVisible();
      expect(screen.getByRole('button', { name: 'Restaurer cette fiche' })).toBeVisible();
      expect(screen.queryByRole('link', { name: 'Modifier' })).toBeNull();
      const [request] = personStatusRequests();
      expect(request?.method).toBe('POST');
      expect(request?.headers.get('If-Match')).toBe('"2"');
    });

    it('explains a refused archive of a linked Person, without the raw server message', async () => {
      personApi({ other: { [ARCHIVE]: () => problemResponse('PERSON_ALREADY_CLAIMED', 409) } });
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Archiver cette fiche' }));
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archiver' }));

      expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent(
        "Cette fiche est reliée à un membre de la famille. Retirez ce lien avant de l'archiver.",
      );
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });

    it('shows an archived profile without any mutation action to a CONTRIBUTOR', async () => {
      personApi({ role: 'CONTRIBUTOR', get: () => jsonResponse(person({ status: 'ARCHIVED' })) });
      renderApp(PROFILE);

      expect(
        await screen.findByText(
          "Cette fiche est archivée. Elle n'apparaît plus dans l'arbre ni dans la recherche.",
        ),
      ).toBeVisible();
      expect(screen.queryByRole('button', { name: 'Restaurer cette fiche' })).toBeNull();
      expect(screen.queryByRole('link', { name: 'Modifier' })).toBeNull();
      expect(screen.queryByRole('button', { name: "C'est moi" })).toBeNull();
      expect(screen.queryByRole('button', { name: 'Archiver cette fiche' })).toBeNull();
    });

    it('restores an archived Person with its version, in English', async () => {
      let restored = false;
      personApi({
        get: () =>
          jsonResponse(person(restored ? { version: 4 } : { status: 'ARCHIVED', version: 3 })),
        other: {
          [RESTORE]: () => {
            restored = true;
            return jsonResponse(person({ version: 4 }));
          },
        },
      });
      renderApp(PROFILE);
      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      await act(() => i18n.changeLanguage('en'));

      expect(
        await screen.findByText(
          'This profile is archived. It no longer appears in the family tree or in search.',
        ),
      ).toBeVisible();
      fireEvent.click(screen.getByRole('button', { name: 'Restore this profile' }));

      expect(await screen.findByText("Marie Adji's profile was restored.")).toBeInTheDocument();
      expect(screen.queryByText(/This profile is archived/)).toBeNull();
      expect(screen.getByRole('button', { name: 'Archive this profile' })).toBeVisible();
      const [request] = personStatusRequests();
      expect(request?.headers.get('If-Match')).toBe('"3"');
    });
  });

  describe('Merge a duplicate (SCREEN-COMPONENT-004, PR-27)', () => {
    const MARIA_ID = 'c0000000-0000-4000-8000-000000000001';
    const MERGE = `POST /families/${ADJI_ID}/persons/${MARIE_ID}/merge`;
    const maria = person({
      id: MARIA_ID,
      firstName: 'Maria',
      displayName: 'Maria Adji',
      birth: { precision: 'YEAR_ONLY', year: 1954 },
      version: 5,
    });

    function mergeApi(merge: Handler) {
      return personApi({
        other: {
          [`GET /families/${ADJI_ID}/persons`]: () =>
            jsonResponse({
              items: [maria],
              page: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
            }),
          [`GET /families/${ADJI_ID}/persons/${MARIA_ID}`]: () => jsonResponse(maria),
          [`GET /families/${ADJI_ID}/persons/${MARIA_ID}/archived-relationships`]: () =>
            jsonResponse([]),
          [MERGE]: merge,
        },
      });
    }

    function mergeRequests() {
      return fetchMock.mock.calls
        .map(([input]) => input as Request)
        .filter((request) => new URL(request.url).pathname.endsWith('/merge'));
    }

    async function chooseMaria() {
      fireEvent.click(
        await screen.findByRole('button', { name: 'Fusionner avec une autre fiche' }),
      );
      const dialog = screen.getByRole('dialog', {
        name: 'Quelle fiche décrit la même personne que Marie Adji ?',
      });
      fireEvent.click(await within(dialog).findByRole('button', { name: /Maria Adji/ }));
      await within(dialog).findByText('Fiche conservée');
      return dialog;
    }

    it('offers Merge to the ADMIN only', async () => {
      for (const role of ['CONTRIBUTOR', 'VIEWER']) {
        personApi({ role });
        const { unmount } = renderAppWithUnmount(PROFILE);
        await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
        expect(screen.queryByRole('button', { name: 'Fusionner avec une autre fiche' })).toBeNull();
        unmount();
      }

      personApi({ role: 'ADMIN' });
      renderApp(PROFILE);
      expect(
        await screen.findByRole('button', { name: 'Fusionner avec une autre fiche' }),
      ).toBeVisible();
    });

    it('compares both profiles, explains the merge and merges on confirmation', async () => {
      mergeApi(() => jsonResponse(maria));
      const { router } = renderApp(PROFILE);

      const dialog = await chooseMaria();

      expect(within(dialog).getByText('Fiche en double')).toBeInTheDocument();
      expect(within(dialog).getByText('Marie Adji')).toBeInTheDocument();
      expect(within(dialog).getByText('Maria Adji')).toBeInTheDocument();
      expect(within(dialog).getByText('La fiche de Maria Adji est conservée.')).toBeInTheDocument();
      expect(
        within(dialog).getByText(
          "La fiche de Marie Adji est fusionnée dans celle de Maria Adji et n'apparaît plus dans la famille.",
        ),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByText(
          'Les liens familiaux passent sur la fiche conservée, sans doublon.',
        ),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByText(
          'Quand les deux fiches ont une valeur, celle de la fiche conservée est gardée.',
        ),
      ).toBeInTheDocument();
      expect(mergeRequests()).toHaveLength(0);

      fireEvent.click(within(dialog).getByRole('button', { name: 'Fusionner les fiches' }));

      expect(
        await screen.findByText(
          'La fiche en double de Marie Adji a été fusionnée dans la fiche de Maria Adji.',
        ),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/persons/${MARIA_ID}`);
      // The dialog does not reopen on the kept profile.
      expect(screen.queryByRole('dialog')).toBeNull();
      const [request] = mergeRequests();
      expect(await request?.json()).toEqual({
        targetPersonId: MARIA_ID,
        sourceVersion: 2,
        targetVersion: 5,
      });
    });

    it.each([
      [
        'DIFFERENT_LINKED_USERS',
        'Ces deux fiches sont reliées à deux membres différents de la famille : elles ne peuvent pas être fusionnées.',
      ],
      [
        'SELF_RELATIONSHIP',
        "Ces deux fiches sont reliées l'une à l'autre. Retirez d'abord ce lien, puis fusionnez-les.",
      ],
      [
        'PARENTAL_CYCLE',
        "Fusionner ces fiches ferait de quelqu'un son propre ancêtre. Vérifiez d'abord leurs liens familiaux.",
      ],
    ])('explains a refused merge (%s) without forcing it', async (reason, message) => {
      mergeApi(() =>
        jsonResponse(
          {
            code: 'PERSON_MERGE_CONFLICT',
            status: 409,
            title: 'x',
            detail: 'raw server detail',
            details: { reason },
          },
          409,
          'application/problem+json',
        ),
      );
      renderApp(PROFILE);

      const dialog = await chooseMaria();
      fireEvent.click(within(dialog).getByRole('button', { name: 'Fusionner les fiches' }));

      expect(await within(dialog).findByRole('alert')).toHaveTextContent(message);
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
      expect(within(dialog).queryByRole('button', { name: /quand même|forcer/i })).toBeNull();
    });

    it('translates a stale version like any other change', async () => {
      mergeApi(() => problemResponse('CONCURRENT_MODIFICATION', 409));
      renderApp(PROFILE);

      const dialog = await chooseMaria();
      fireEvent.click(within(dialog).getByRole('button', { name: 'Fusionner les fiches' }));

      expect(await within(dialog).findByRole('alert')).toHaveTextContent(
        "Quelqu'un a modifié ces informations entre-temps.",
      );
    });

    it('leads from a merged profile to the kept one, without any action', async () => {
      personApi({
        get: () => jsonResponse(person({ status: 'MERGED', mergedIntoPersonId: MARIA_ID })),
      });
      renderApp(PROFILE);

      expect(
        await screen.findByText(
          'Cette fiche a été fusionnée dans une autre fiche, qui décrit la même personne.',
        ),
      ).toBeVisible();
      expect(screen.getByRole('link', { name: 'Ouvrir la fiche conservée' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/${MARIA_ID}`,
      );
      for (const name of ['Modifier', 'Fusionner avec une autre fiche', 'Archiver cette fiche']) {
        expect(screen.queryByRole('button', { name })).toBeNull();
        expect(screen.queryByRole('link', { name })).toBeNull();
      }
    });
  });

  describe('Edit Person (SCREEN-012)', () => {
    it('sends every field with the loaded version, then shows the profile', async () => {
      const api = personApi({
        patch: () => jsonResponse(person({ lastName: 'Ndongo', displayName: 'Marie Ndongo' })),
      });
      const { router } = renderApp(`${PROFILE}/edit`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Modifier Marie Adji' }),
      ).toBeInTheDocument();
      for (const section of ['Identité', 'Vie', 'À propos']) {
        expect(screen.getByRole('heading', { level: 2, name: section })).toBeInTheDocument();
      }
      expect(screen.getByRole('textbox', { name: 'Autres prénoms' })).toHaveValue('Jeanne');
      expect(screen.getByLabelText('Date exacte de naissance')).toHaveValue('1954-03-12');
      expect(screen.getByRole('textbox', { name: 'Année de décès' })).toHaveValue('2020');
      type('Nom', 'Ndongo');
      type('Autres prénoms', '  ');
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Marie Ndongo' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(PROFILE);
      const [patch] = api.patches();
      expect(patch?.headers.get('If-Match')).toBe('"2"');
      expect(await patch?.json()).toEqual({
        firstName: 'Marie',
        lastName: 'Ndongo',
        middleNames: '',
        preferredName: '',
        gender: 'FEMALE',
        birth: { precision: 'EXACT', date: '1954-03-12' },
        isDeceased: true,
        death: { precision: 'YEAR_ONLY', year: 2020 },
        biography: 'Institutrice à Ebolowa.',
      });
    });

    it('switches a date to year only or unknown', async () => {
      const api = personApi({ patch: () => jsonResponse(person()) });
      renderApp(`${PROFILE}/edit`);
      await screen.findByRole('heading', { level: 1, name: 'Modifier Marie Adji' });

      fireEvent.change(screen.getByRole('combobox', { name: 'Date de naissance' }), {
        target: { value: 'YEAR_ONLY' },
      });
      type('Année de naissance', '1953');
      fireEvent.click(screen.getByRole('checkbox', { name: 'Cette personne est décédée' }));
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      await vi.waitFor(() => {
        expect(api.patches()).toHaveLength(1);
      });
      expect(await api.patches()[0]?.json()).toMatchObject({
        birth: { precision: 'YEAR_ONLY', year: 1953 },
        isDeceased: false,
        death: { precision: 'UNKNOWN' },
      });
    });

    it('on a conflict, reloads the latest version without merging the form', async () => {
      let latest = false;
      const api = personApi({
        get: () =>
          jsonResponse(
            latest
              ? person({ lastName: 'Mbida', displayName: 'Marie Mbida', version: 3 })
              : person(),
          ),
        patch: () => {
          latest = true;
          return problemResponse('CONCURRENT_MODIFICATION', 409);
        },
      });
      renderApp(`${PROFILE}/edit`);
      await screen.findByRole('heading', { level: 1, name: 'Modifier Marie Adji' });
      type('Nom', 'Mine');
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Cette personne a été modifiée depuis que vous avez ouvert la page.',
      );
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
      fireEvent.click(screen.getByRole('button', { name: 'Recharger la dernière version' }));

      await vi.waitFor(() => {
        expect(screen.getByRole('textbox', { name: 'Nom' })).toHaveValue('Mbida');
      });
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
      await vi.waitFor(() => {
        expect(api.patches()).toHaveLength(2);
      });
      expect(api.patches()[1]?.headers.get('If-Match')).toBe('"3"');
    });

    it('translates other server errors', async () => {
      personApi({ patch: () => problemResponse('VALIDATION_FAILED', 400) });
      renderApp(`${PROFILE}/edit`);
      await screen.findByRole('heading', { level: 1, name: 'Modifier Marie Adji' });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Certaines informations ne sont pas valides.',
      );
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });

    it('sends a VIEWER back to the profile', async () => {
      personApi({ role: 'VIEWER' });
      const { router } = renderApp(`${PROFILE}/edit`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Marie Adji' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(PROFILE);
    });

    it("sends a CONTRIBUTOR back from another member's linked Person", async () => {
      personApi({
        role: 'CONTRIBUTOR',
        get: () =>
          jsonResponse(person({ linkedUserId: 'u2', relationshipToCurrentUser: 'NONE_KNOWN' })),
      });
      const { router } = renderApp(`${PROFILE}/edit`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Marie Adji' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(PROFILE);
    });
  });

  describe('Person history (SCREEN-005, PR-28)', () => {
    const HISTORY = `GET /families/${ADJI_ID}/persons/${MARIE_ID}/history`;

    function entry(overrides: Record<string, unknown> = {}) {
      return {
        id: crypto.randomUUID(),
        action: 'PERSON_UPDATED',
        actor: { userId: 'u1', displayName: 'Alice', deleted: false },
        field: null,
        oldValue: null,
        newValue: null,
        occurredAt: '2026-09-26T10:00:00Z',
        ...overrides,
      };
    }

    function page(items: unknown[], pageNumber = 0, totalPages = 1) {
      return {
        items,
        page: { page: pageNumber, size: 20, totalElements: items.length, totalPages },
      };
    }

    function historyRequests(api: ReturnType<typeof personApi>) {
      return api.all().filter((request) => new URL(request.url).pathname.endsWith('/history'));
    }

    async function openHistory() {
      const heading = await screen.findByRole('heading', { level: 2, name: /Historique|History/ });
      const details = heading.closest('details');
      if (details === null) throw new Error('History is not collapsible');
      expect(details).not.toHaveAttribute('open');
      details.open = true;
      fireEvent(details, new Event('toggle'));
      return details;
    }

    it('is collapsed and loads nothing until opened, then shows readable changes', async () => {
      const api = personApi({
        other: {
          [HISTORY]: () =>
            jsonResponse(
              page([
                entry({ action: 'PERSON_CLAIMED' }),
                entry({ field: 'birth', oldValue: '1954', newValue: '1956' }),
                entry({ field: 'birth', oldValue: '1954-03-12', newValue: 'UNKNOWN' }),
                entry({ field: 'lastName', oldValue: null, newValue: 'Adji' }),
                entry({ field: 'gender', oldValue: 'UNKNOWN', newValue: 'FEMALE' }),
                entry({ field: 'isDeceased', oldValue: false, newValue: true }),
                entry({ field: 'biography' }),
                entry({
                  action: 'PERSON_CREATED',
                  actor: { userId: 'u2', displayName: null, deleted: true },
                }),
              ]),
            ),
        },
      });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 2, name: 'Historique' });
      expect(historyRequests(api)).toHaveLength(0);
      const details = await openHistory();

      const list = await within(details).findByRole('list', { name: 'Historique' });
      const items = within(list).getAllByRole('listitem');
      expect(items.map((item) => item.querySelector('p')?.textContent)).toEqual([
        "Fiche reliée au compte d'un membre",
        'Naissance : 1954 → 1956',
        'Naissance : 12 mars 1954 → Inconnue',
        'Nom : (vide) → Adji',
        'Genre : Non précisé → Femme',
        'Décès déclaré : Non → Oui',
        'Biographie modifiée',
        'Fiche créée',
      ]);
      expect(items[0]).toHaveTextContent('26 septembre 2026 · Alice');
      expect(items[7]).toHaveTextContent('Ancien membre');
      expect(list).not.toHaveTextContent(/PERSON_|birth|lastName|u1/);
      expect(historyRequests(api)).toHaveLength(1);

      await act(() => i18n.changeLanguage('en'));
      expect(await screen.findByText('Birth: 1954 → 1956')).toBeInTheDocument();
      expect(screen.getByText('Former member', { exact: false })).toBeInTheDocument();
    });

    it('says when nothing was recorded', async () => {
      personApi({ other: { [HISTORY]: () => jsonResponse(page([])) } });
      renderApp(PROFILE);

      await openHistory();

      expect(
        await screen.findByText("Aucune modification enregistrée pour l'instant."),
      ).toBeInTheDocument();
    });

    it('loads older changes on demand', async () => {
      personApi({
        other: {
          [HISTORY]: (request) =>
            new URL(request.url).searchParams.get('page') === '1'
              ? jsonResponse(page([entry({ action: 'PERSON_CREATED' })], 1, 2))
              : jsonResponse(page([entry({ action: 'PERSON_ARCHIVED' })], 0, 2)),
        },
      });
      renderApp(PROFILE);

      await openHistory();
      fireEvent.click(await screen.findByRole('button', { name: 'Voir plus' }));

      expect(await screen.findByText('Fiche créée')).toBeInTheDocument();
      expect(screen.getByText('Fiche archivée')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Voir plus' })).toBeNull();
    });

    it('translates an error, without the raw server message', async () => {
      personApi({ other: { [HISTORY]: () => problemResponse('PERMISSION_DENIED', 403) } });
      renderApp(PROFILE);

      await openHistory();

      expect(await screen.findByRole('alert')).not.toHaveTextContent('raw server detail');
      expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
    });
  });
});
