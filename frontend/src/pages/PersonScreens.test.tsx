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
    patches: () => requests.filter((request) => request.method === 'PATCH'),
    claims: () => requests.filter((request) => new URL(request.url).pathname.endsWith('/claim')),
  };
}

function personApi({
  role = 'ADMIN',
  get = () => jsonResponse(person()),
  patch,
  claim,
  unclaim,
}: {
  role?: string;
  get?: Handler;
  patch?: Handler;
  claim?: Handler;
  unclaim?: Handler;
} = {}) {
  return fakeApi({
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/persons/${MARIE_ID}`]: get,
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
        screen.getByText("Aucun proche n'est encore relié à cette personne."),
      ).toBeInTheDocument();
      const about = screen.getByRole('region', { name: 'À propos' });
      expect(within(about).getByText('Jeanne')).toBeInTheDocument();
      expect(within(about).getByText('12 mars 1954')).toBeInTheDocument();
      expect(within(about).getByText('2020')).toBeInTheDocument();
      expect(within(about).getByText('Institutrice à Ebolowa.')).toBeInTheDocument();
      expect(screen.queryByText(/souvenir|historique|photo/i)).not.toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Modifier' })).toHaveAttribute(
        'href',
        `${PROFILE}/edit`,
      );
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
});
