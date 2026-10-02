import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { pendingInvitation, rememberInvitation } from '../invitations/pendingInvitation';
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

const TOKEN = 'Zk3-first-token';
const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const AWA_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const OTHER_AWA_ID = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const MARIE_ID = '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e';
const INVITATION = `/invitations/${TOKEN}`;
const HOME = `/families/${ADJI_ID}`;
const JOIN = `/families/${ADJI_ID}/join`;

function preview(overrides: Record<string, unknown> = {}) {
  return {
    familyId: ADJI_ID,
    familyName: 'ADJI',
    invitedByDisplayName: 'Tony Adji',
    role: 'CONTRIBUTOR',
    status: 'PENDING',
    expiresAt: '2026-10-11T10:00:00Z',
    ...overrides,
  };
}

function family(myRole = 'CONTRIBUTOR', myLinkedPersonId: string | null = null) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    myLinkedPersonId,
    stats: { personCount: 3, memoryCount: 0, activeMemberCount: 2 },
    limits: { maxPhotosPerMemory: 3 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function person(id: string, firstName: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    familyId: ADJI_ID,
    firstName,
    lastName: 'Ngo',
    displayName: `${firstName} Ngo`,
    gender: 'FEMALE',
    birth: { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    linkedUserId: null,
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 2,
    ...overrides,
  };
}

function accepted(overrides: Record<string, unknown> = {}) {
  return {
    alreadyMember: false,
    suggestedPerson: { id: AWA_ID, displayName: 'Awa Ngo' },
    family: { id: ADJI_ID, name: 'ADJI', myRole: 'CONTRIBUTOR' },
    membership: { id: 'm1', role: 'CONTRIBUTOR', status: 'ACTIVE' },
    ...overrides,
  };
}

/** Awa, daughter of Marie, centred in the tree. */
function awaTree(awa = person(AWA_ID, 'Awa', { birth: { precision: 'YEAR_ONLY', year: 1962 } })) {
  return {
    focusPersonId: awa.id,
    nodes: [
      { ...awa, hasMoreParents: false, hasMoreChildren: false },
      { ...person(MARIE_ID, 'Marie'), hasMoreParents: false, hasMoreChildren: false },
    ],
    edges: [
      {
        relationshipId: 'r1',
        type: 'PARENT_OF',
        sourcePersonId: MARIE_ID,
        targetPersonId: awa.id,
        version: 0,
      },
    ],
  };
}

/** Two Awa Ngo: one told apart by her mother, the other by her birth year. */
function claimables() {
  return {
    items: [
      { ...person(AWA_ID, 'Awa'), parent: { id: MARIE_ID, displayName: 'Marie Ngo' } },
      {
        ...person(OTHER_AWA_ID, 'Awa', { birth: { precision: 'YEAR_ONLY', year: 1970 } }),
        parent: null,
      },
    ],
    page: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

function fakeApi(handlers: Record<string, Handler> = {}) {
  let linkedPersonId: string | null = null;
  const defaults: Record<string, Handler> = {
    [`GET ${INVITATION}`]: () => jsonResponse(preview()),
    [`POST ${INVITATION}/accept`]: () => jsonResponse(accepted()),
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family('CONTRIBUTOR', linkedPersonId)),
    [`GET /families/${ADJI_ID}/tree`]: () => jsonResponse(awaTree()),
    [`GET /families/${ADJI_ID}/claimable-persons`]: () => jsonResponse(claimables()),
    [`POST /families/${ADJI_ID}/persons/${AWA_ID}/claim`]: () => {
      linkedPersonId = AWA_ID;
      return jsonResponse(person(AWA_ID, 'Awa', { version: 3 }));
    },
    [`POST /families/${ADJI_ID}/persons/${OTHER_AWA_ID}/claim`]: () => {
      linkedPersonId = OTHER_AWA_ID;
      return jsonResponse(person(OTHER_AWA_ID, 'Awa', { version: 3 }));
    },
    ...handlers,
  };
  const requests: Request[] = [];
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({
        id: 'u2',
        email: 'awa@mbia.local',
        displayName: 'Awa',
        preferredLocale: i18n.language,
      });
    }
    const handler = defaults[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    requests: (method: string, suffix: string) =>
      requests.filter(
        (request) => request.method === method && new URL(request.url).pathname.endsWith(suffix),
      ),
  };
}

function renderApp(
  path: string,
  { signedIn = true, state }: { signedIn?: boolean; state?: unknown } = {},
) {
  const url = new URL(path, 'http://localhost');
  const router = createMemoryRouter(routes, {
    initialEntries: [{ pathname: url.pathname, search: url.search, state }],
  });
  const userManager = fakeUserManager(signedIn ? fakeOidcUser() : null);
  render(<App router={router} queryClient={createQueryClient()} userManager={userManager} />);
  return { router, userManager };
}

describe('Join screens', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    localStorage.clear();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('SCREEN-010 — Accept Invitation', () => {
    it('shows the Family, who invites and the permission, never the Person, signed out', async () => {
      fakeApi();
      renderApp(INVITATION, { signedIn: false });

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Rejoindre la famille ADJI' }),
      ).toBeInTheDocument();
      expect(
        screen.getByText('Tony Adji vous invite à rejoindre la famille sur Mbia.'),
      ).toBeVisible();
      expect(screen.getByText('Peut contribuer')).toBeVisible();
      expect(screen.getByText('Cette invitation expire le 11 octobre 2026.')).toBeVisible();
      expect(screen.queryByText(/Awa/)).not.toBeInTheDocument();
      expect(pendingInvitation()).toBe(TOKEN);
    });

    it('signed out, `Join the family` signs in and comes back to the same invitation to join', async () => {
      fakeApi();
      const { userManager } = renderApp(INVITATION, { signedIn: false });

      fireEvent.click(await screen.findByRole('button', { name: 'Rejoindre la famille' }));

      await waitFor(() => {
        expect(userManager.signinRedirect).toHaveBeenCalledWith(
          expect.objectContaining({ state: { returnTo: `${INVITATION}?join=1` } }),
        );
      });
      expect(pendingInvitation()).toBe(TOKEN);
    });

    it('back from sign-in, joins without another tap, forgets the token and asks "Are you Awa Ngo?"', async () => {
      const api = fakeApi();
      rememberInvitation(TOKEN);
      const { router } = renderApp(`${INVITATION}?join=1`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Êtes-vous Awa Ngo ?' }),
      ).toBeInTheDocument();
      expect(api.requests('POST', '/accept')).toHaveLength(1);
      expect(router.state.location.pathname).toBe(JOIN);
      expect(router.state.location.search).toBe(`?suggested=${AWA_ID}`);
      expect(pendingInvitation()).toBeNull();
    });

    it('signed in, `Join the family` accepts; without a suggested Person, asks "Are you already in this tree?"', async () => {
      fakeApi({
        [`POST ${INVITATION}/accept`]: () => jsonResponse(accepted({ suggestedPerson: null })),
      });
      const { router } = renderApp(INVITATION);

      fireEvent.click(await screen.findByRole('button', { name: 'Rejoindre la famille' }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Êtes-vous déjà dans cet arbre ?' }),
      ).toBeInTheDocument();
      expect(router.state.location.search).toBe('');
    });

    it('an ACTIVE member goes to Family Home directly, and the token is forgotten', async () => {
      fakeApi({
        [`POST ${INVITATION}/accept`]: () =>
          jsonResponse(accepted({ alreadyMember: true, suggestedPerson: null })),
      });
      const { router } = renderApp(INVITATION);

      fireEvent.click(await screen.findByRole('button', { name: 'Rejoindre la famille' }));

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(HOME);
      });
      expect(await screen.findByRole('heading', { level: 1, name: 'ADJI' })).toBeInTheDocument();
      expect(screen.queryByText('Bienvenue dans la famille ADJI')).not.toBeInTheDocument();
      expect(pendingInvitation()).toBeNull();
    });

    it.each([
      ['INVITATION_EXPIRED', 410, 'Cette invitation a expiré'],
      ['INVITATION_REVOKED', 410, 'Cette invitation a été annulée'],
      ['INVITATION_ALREADY_USED', 410, 'Cette invitation a déjà été utilisée'],
      ['INVITATION_NOT_FOUND', 404, 'Ce lien ne fonctionne plus'],
    ])(
      '%s explains itself, suggests a new link and forgets the token',
      async (code, status, title) => {
        fakeApi({ [`GET ${INVITATION}`]: () => problemResponse(code, status) });
        rememberInvitation(TOKEN);
        renderApp(INVITATION, { signedIn: false });

        expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
        expect(
          screen.getByText("Demandez un nouveau lien à l'administrateur de la famille."),
        ).toBeVisible();
        expect(
          screen.queryByRole('button', { name: 'Rejoindre la famille' }),
        ).not.toBeInTheDocument();
        expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
        await waitFor(() => {
          expect(pendingInvitation()).toBeNull();
        });
      },
    );

    it('a link used between the preview and the acceptance explains itself', async () => {
      fakeApi({
        [`POST ${INVITATION}/accept`]: () => problemResponse('INVITATION_ALREADY_USED', 410),
      });
      renderApp(INVITATION);

      fireEvent.click(await screen.findByRole('button', { name: 'Rejoindre la famille' }));

      expect(
        await screen.findByRole('heading', { name: 'Cette invitation a déjà été utilisée' }),
      ).toBeInTheDocument();
      await waitFor(() => {
        expect(pendingInvitation()).toBeNull();
      });
    });

    it('an email not verified yet asks to check the inbox', async () => {
      fakeApi({ [`POST ${INVITATION}/accept`]: () => problemResponse('EMAIL_NOT_VERIFIED', 403) });
      renderApp(INVITATION);

      fireEvent.click(await screen.findByRole('button', { name: 'Rejoindre la famille' }));

      expect(
        await screen.findByRole('heading', { name: 'Vérifiez votre boîte mail' }),
      ).toBeInTheDocument();
      expect(pendingInvitation()).toBe(TOKEN);
    });

    it('in English', async () => {
      await act(() => i18n.changeLanguage('en'));
      fakeApi({ [`GET ${INVITATION}`]: () => jsonResponse(preview({ role: 'VIEWER' })) });
      renderApp(INVITATION, { signedIn: false });

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Join the ADJI family' }),
      ).toBeInTheDocument();
      expect(screen.getByText('Read only')).toBeVisible();
      expect(screen.getByRole('button', { name: 'Join the family' })).toBeEnabled();
    });
  });

  describe('coming back to a pending invitation (OQ-050)', () => {
    it('Welcome leads to the invitation, for example after verifying the email in another tab', async () => {
      fakeApi();
      rememberInvitation(TOKEN);
      const { router } = renderApp('/', { signedIn: false });

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Rejoindre la famille ADJI' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(INVITATION);
    });

    it('the signed-in landing leads to the invitation', async () => {
      fakeApi();
      rememberInvitation(TOKEN);
      const { router } = renderApp('/home');

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(INVITATION);
      });
    });
  });

  describe('"Are you {displayName}?"', () => {
    it("shows the parent sentence; `Yes, it's me` links the member and Family Home welcomes them", async () => {
      const api = fakeApi();
      const { router } = renderApp(`${JOIN}?suggested=${AWA_ID}`);

      expect(await screen.findByText('Awa Ngo est la fille de Marie Ngo')).toBeVisible();
      fireEvent.click(screen.getByRole('button', { name: "Oui, c'est moi" }));

      const welcome = await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' });
      expect(router.state.location.pathname).toBe(HOME);
      const [claim] = api.requests('POST', `/persons/${AWA_ID}/claim`);
      expect(claim?.headers.get('If-Match')).toBe('"2"');
      const treeLink = within(welcome).getByRole('link', { name: "Voir l'arbre de la famille" });
      await waitFor(() => {
        expect(treeLink).toHaveAttribute('href', `${HOME}/tree?focus=${AWA_ID}`);
      });
      expect(within(welcome).getByRole('link', { name: 'Ajouter un souvenir' })).toBeVisible();
    });

    it('without a known parent, shows the birth year', async () => {
      fakeApi({
        [`GET /families/${ADJI_ID}/tree`]: () =>
          jsonResponse({
            ...awaTree(),
            nodes: [awaTree().nodes[0]],
            edges: [],
          }),
      });
      renderApp(`${JOIN}?suggested=${AWA_ID}`);

      expect(await screen.findByText('1962 –')).toBeVisible();
    });

    it('`No` asks "Are you already in this tree?"; `Later` goes to Family Home with its welcome', async () => {
      const api = fakeApi();
      renderApp(`${JOIN}?suggested=${AWA_ID}`);

      fireEvent.click(await screen.findByRole('button', { name: 'Non' }));
      expect(
        await screen.findByRole('heading', { level: 1, name: 'Êtes-vous déjà dans cet arbre ?' }),
      ).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Plus tard' }));

      expect(
        await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' }),
      ).toBeInTheDocument();
      expect(api.requests('POST', '/claim')).toHaveLength(0);
    });

    it('a suggested Person no longer available leaves only the search', async () => {
      fakeApi({
        [`GET /families/${ADJI_ID}/tree`]: () =>
          jsonResponse(awaTree(person(AWA_ID, 'Awa', { linkedUserId: 'u3' }))),
      });
      renderApp(`${JOIN}?suggested=${AWA_ID}`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Êtes-vous déjà dans cet arbre ?' }),
      ).toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveTextContent(
        "Le profil préparé pour vous n'est plus disponible.",
      );
    });

    it('a Person claimed by someone else in the meantime leads back to the search', async () => {
      fakeApi({
        [`POST /families/${ADJI_ID}/persons/${AWA_ID}/claim`]: () =>
          problemResponse('PERSON_ALREADY_CLAIMED', 409),
      });
      renderApp(`${JOIN}?suggested=${AWA_ID}`);

      fireEvent.click(await screen.findByRole('button', { name: "Oui, c'est moi" }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Êtes-vous déjà dans cet arbre ?' }),
      ).toBeInTheDocument();
      expect(screen.getByText(/Awa Ngo ne peut plus être choisi/)).toBeVisible();
    });
  });

  describe('"Are you already in this tree?"', () => {
    it('tells two Persons with the same name apart, and asks to confirm before linking', async () => {
      const api = fakeApi();
      renderApp(JOIN);

      const results = await screen.findByRole('list', { name: 'Personnes trouvées' });
      const items = within(results).getAllByRole('button');
      expect(items).toHaveLength(2);
      expect(items[0]).toHaveTextContent('Awa Ngo est la fille de Marie Ngo');
      expect(items[1]).toHaveTextContent('1970 –');
      expect(api.requests('GET', '/claimable-persons')).not.toHaveLength(0);

      fireEvent.click(within(results).getByRole('button', { name: /1970/ }));
      expect(
        await screen.findByRole('heading', { level: 1, name: 'Êtes-vous Awa Ngo ?' }),
      ).toBeInTheDocument();
      expect(api.requests('POST', '/claim')).toHaveLength(0);
      fireEvent.click(screen.getByRole('button', { name: "Oui, c'est moi" }));

      expect(
        await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' }),
      ).toBeInTheDocument();
      expect(api.requests('POST', `/persons/${OTHER_AWA_ID}/claim`)).toHaveLength(1);
    });

    it('`No` in the confirmation goes back to the list', async () => {
      fakeApi();
      renderApp(JOIN);

      const results = await screen.findByRole('list', { name: 'Personnes trouvées' });
      fireEvent.click(within(results).getByRole('button', { name: /Marie Ngo/ }));
      fireEvent.click(await screen.findByRole('button', { name: 'Non' }));

      expect(await screen.findByRole('list', { name: 'Personnes trouvées' })).toBeInTheDocument();
    });

    it('a CONTRIBUTOR who is not in the tree adds themselves ("Start with me"), then is welcomed', async () => {
      fakeApi();
      const { router } = renderApp(JOIN);

      fireEvent.click(await screen.findByRole('button', { name: "Je ne suis pas dans l'arbre" }));

      expect(router.state.location.pathname).toBe(`${HOME}/persons/new`);
      expect(router.state.location.search).toBe('?mode=me');
      expect(router.state.location.state).toEqual({ joined: true });
    });

    it('after adding themselves, the new member is welcomed on Family Home', async () => {
      const api = fakeApi({
        [`POST /families/${ADJI_ID}/persons`]: () =>
          jsonResponse(person(AWA_ID, 'Awa', { linkedUserId: 'u2', version: 0 }), 201),
      });
      const { router } = renderApp(`${HOME}/persons/new?mode=me`, { state: { joined: true } });

      fireEvent.change(await screen.findByLabelText(/^Prénom/), { target: { value: 'Awa' } });
      fireEvent.click(screen.getByRole('button', { name: "M'ajouter à la famille" }));

      expect(
        await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(HOME);
      const [created] = api.requests('POST', `/families/${ADJI_ID}/persons`);
      expect(await created?.json()).toMatchObject({ firstName: 'Awa', linkToCurrentUser: true });
    });

    it('a VIEWER cannot create a Person: the explanation, then `Later`', async () => {
      const api = fakeApi({
        [`GET /families/${ADJI_ID}`]: () => jsonResponse(family('VIEWER')),
      });
      const { router } = renderApp(JOIN);

      fireEvent.click(await screen.findByRole('button', { name: "Je ne suis pas dans l'arbre" }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Un contributeur peut vous ajouter' }),
      ).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Plus tard' }));
      const welcome = await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' });
      expect(router.state.location.pathname).toBe(HOME);
      expect(
        within(welcome).queryByRole('link', { name: 'Ajouter un souvenir' }),
      ).not.toBeInTheDocument();
      expect(api.requests('POST', '/persons')).toHaveLength(0);
    });

    it('a member already linked goes to Family Home', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family('CONTRIBUTOR', AWA_ID)) });
      const { router } = renderApp(JOIN);

      expect(
        await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(HOME);
    });
  });

  describe('SCREEN-002 — the welcome after joining', () => {
    it('is shown once, and can be closed', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family('CONTRIBUTOR', AWA_ID)) });
      const { router } = renderApp(HOME, { state: { joined: true } });

      const welcome = await screen.findByRole('region', { name: 'Bienvenue dans la famille ADJI' });
      await waitFor(() => {
        expect(router.state.location.state).toEqual({ joined: undefined });
      });
      fireEvent.click(
        within(welcome).getByRole('button', { name: 'Fermer le message de bienvenue' }),
      );

      expect(
        screen.queryByRole('region', { name: 'Bienvenue dans la famille ADJI' }),
      ).not.toBeInTheDocument();
    });

    it('is not shown on an ordinary arrival', async () => {
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family('CONTRIBUTOR', AWA_ID)) });
      renderApp(HOME);

      expect(await screen.findByRole('heading', { level: 1, name: 'ADJI' })).toBeInTheDocument();
      expect(
        screen.queryByRole('region', { name: 'Bienvenue dans la famille ADJI' }),
      ).not.toBeInTheDocument();
    });

    it('in English', async () => {
      await act(() => i18n.changeLanguage('en'));
      fakeApi({ [`GET /families/${ADJI_ID}`]: () => jsonResponse(family('CONTRIBUTOR', AWA_ID)) });
      renderApp(HOME, { state: { joined: true } });

      const welcome = await screen.findByRole('region', { name: 'Welcome to the ADJI family' });
      expect(within(welcome).getByRole('link', { name: 'View the family tree' })).toBeVisible();
      expect(
        within(welcome).getByRole('button', { name: 'Close the welcome message' }),
      ).toBeVisible();
    });
  });
});
