import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
const OTHER_ID = '1b2c3d4e-5f60-4a7b-8c9d-0e1f2a3b4c5d';
const MEMBERS = `/families/${ADJI_ID}/members`;
const AWA_MEMBER = 'a1a1a1a1-0000-4000-8000-000000000001';
const KARIM_MEMBER = 'a1a1a1a1-0000-4000-8000-000000000002';
const TONY_MEMBER = 'a1a1a1a1-0000-4000-8000-000000000003';
const AWA_INVITATION = 'b2b2b2b2-0000-4000-8000-000000000001';
const EMAIL_INVITATION = 'b2b2b2b2-0000-4000-8000-000000000002';
const LINK_INVITATION = 'b2b2b2b2-0000-4000-8000-000000000003';
const RENEWED_LINK = 'http://localhost:5173/invitations/Qw8-renewed-token';

function family(id: string, name: string, myRole: string) {
  return {
    id,
    name,
    myRole,
    stats: { personCount: 3, memoryCount: 0, activeMemberCount: 3 },
    limits: { maxPhotosPerMemory: 3 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function member(overrides: Record<string, unknown>) {
  return {
    status: 'ACTIVE',
    linkedPersonId: null,
    linkedPersonDisplayName: null,
    relationshipToCurrentUser: null,
    joinedAt: '2026-09-25T10:00:00Z',
    version: 3,
    ...overrides,
  };
}

/** Tony, the ADMIN; Awa, his mother; Karim, linked to a Person Tony has no known link with; Léa, a cousin. */
function members(myUserId: string) {
  return [
    member({ id: TONY_MEMBER, userId: 'u1', displayName: 'Tony', role: 'ADMIN' }),
    member({
      id: AWA_MEMBER,
      userId: 'u2',
      displayName: 'Awa',
      role: myUserId === 'u2' ? 'VIEWER' : 'CONTRIBUTOR',
      linkedPersonId: 'p2',
      linkedPersonDisplayName: 'Awa Ngo',
      relationshipToCurrentUser: myUserId === 'u1' ? 'MOTHER' : null,
    }),
    member({
      id: KARIM_MEMBER,
      userId: 'u3',
      displayName: null,
      role: 'VIEWER',
      linkedPersonId: 'p3',
      linkedPersonDisplayName: 'Karim Ngo',
    }),
    member({
      id: 'a1a1a1a1-0000-4000-8000-000000000004',
      userId: 'u4',
      displayName: 'Léa',
      role: 'CONTRIBUTOR',
      linkedPersonId: 'p4',
      linkedPersonDisplayName: 'Léa Adji',
      relationshipToCurrentUser: myUserId === 'u1' ? 'FIRST_COUSIN' : null,
    }),
  ];
}

function invitation(overrides: { id: string } & Record<string, unknown>) {
  return {
    familyId: ADJI_ID,
    channel: 'LINK',
    email: null,
    role: 'CONTRIBUTOR',
    status: 'PENDING',
    person: null,
    emailDelivery: null,
    invitedBy: { userId: 'u1', displayName: 'Tony', deleted: false },
    expiresAt: '2026-10-11T10:00:00Z',
    createdAt: '2026-09-27T10:00:00Z',
    version: 2,
    ...overrides,
  };
}

const INVITATIONS = [
  invitation({ id: AWA_INVITATION, person: { id: 'p5', displayName: 'Marie Ngo' } }),
  invitation({
    id: EMAIL_INVITATION,
    channel: 'EMAIL',
    email: 'cousin@example.com',
    role: 'VIEWER',
    emailDelivery: 'FAILED',
  }),
  invitation({ id: LINK_INVITATION }),
];

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), as the User `userId`. */
function fakeApi({
  userId = 'u1',
  role = 'ADMIN',
  locale = 'fr',
  handlers = {},
}: {
  userId?: string;
  role?: string;
  locale?: string;
  handlers?: Record<string, Handler>;
} = {}) {
  const all: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(ADJI_ID, 'ADJI', role)),
    [`GET /families/${ADJI_ID}/members`]: () => jsonResponse(members(userId)),
    [`GET /families/${ADJI_ID}/invitations`]: () => jsonResponse(INVITATIONS),
    ...handlers,
  };
  const requests: Request[] = [];
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({
        id: userId,
        email: 'someone@mbia.local',
        displayName: null,
        preferredLocale: locale,
      });
    }
    const handler = all[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    requests: (method: string, suffix: string) =>
      requests.filter(
        (request) => request.method === method && new URL(request.url).pathname.endsWith(suffix),
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

async function rowOf(name: string) {
  const title = await screen.findByText(name, { selector: 'p' });
  const row = title.closest('li');
  if (row === null) throw new Error(`no row for ${name}`);
  return within(row);
}

describe('Members screen (SCREEN-008)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
    await act(() => i18n.changeLanguage('fr'));
  });

  it('is the Members tab of the primary navigation, for every role', async () => {
    fakeApi({ userId: 'u2', role: 'VIEWER' });
    renderApp(MEMBERS);

    const tab = await screen.findByRole('link', { name: 'Membres' });
    expect(tab).toHaveAttribute('href', MEMBERS);
    expect(tab).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { level: 1, name: 'Membres' })).toBeInTheDocument();
  });

  it('shows each member with their kinship, or else their linked Person, and their role', async () => {
    fakeApi();
    renderApp(MEMBERS);

    const tony = await rowOf('Tony');
    expect(tony.getByText('Vous')).toBeInTheDocument();
    expect(tony.getByText('Administrateur de la famille')).toBeInTheDocument();
    const awa = await rowOf('Awa');
    expect(awa.getByText('Votre mère')).toBeInTheDocument();
    expect(awa.getByRole('combobox', { name: 'Permission de Awa' })).toHaveValue('CONTRIBUTOR');
    // No User name: the linked Person's name, and no kinship known.
    const karim = await rowOf('Karim Ngo');
    expect(karim.getByText('Profil : Karim Ngo')).toBeInTheDocument();
    expect(karim.getByRole('combobox', { name: 'Permission de Karim Ngo' })).toHaveValue('VIEWER');
    // FIRST_COUSIN without the Person's gender: the neutral form (OQ-062).
    expect((await rowOf('Léa')).getByText('Votre cousin ou cousine')).toBeInTheDocument();
    // Never a member's email (OQ-061).
    expect(within(screen.getByRole('list', { name: 'Membres' })).queryByText(/@/)).toBeNull();
  });

  it('gives the ADMIN no action on themselves, and explains why they cannot leave', async () => {
    fakeApi();
    renderApp(MEMBERS);

    const tony = await rowOf('Tony');
    expect(tony.queryByRole('combobox')).not.toBeInTheDocument();
    expect(tony.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Quitter cette famille' })).not.toBeInTheDocument();
    expect(
      screen.getByText(/Une famille garde toujours son administrateur.*support de Mbia/),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inviter un proche' })).toHaveAttribute(
      'href',
      `/families/${ADJI_ID}/invitations/new`,
    );
  });

  it('changes a role from the version it was listed with', async () => {
    const api = fakeApi({
      handlers: {
        [`PATCH /families/${ADJI_ID}/members/${AWA_MEMBER}`]: () =>
          jsonResponse(member({ id: AWA_MEMBER, userId: 'u2', role: 'VIEWER', version: 4 })),
      },
    });
    renderApp(MEMBERS);

    fireEvent.change((await rowOf('Awa')).getByRole('combobox', { name: 'Permission de Awa' }), {
      target: { value: 'VIEWER' },
    });

    await waitFor(() => {
      expect(api.requests('PATCH', AWA_MEMBER)).toHaveLength(1);
    });
    const [patch] = api.requests('PATCH', AWA_MEMBER);
    expect(patch?.headers.get('If-Match')).toBe('"3"');
    expect(await patch?.json()).toEqual({ role: 'VIEWER' });
  });

  it('removes a member after a confirmation saying that their contributions stay', async () => {
    const api = fakeApi({
      handlers: {
        [`DELETE /families/${ADJI_ID}/members/${AWA_MEMBER}`]: () =>
          new Response(null, { status: 204 }),
      },
    });
    renderApp(MEMBERS);

    fireEvent.click(
      (await rowOf('Awa')).getByRole('button', { name: 'Retirer Awa de la famille' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Retirer Awa de la famille ?' });
    expect(
      within(dialog).getByText(/Les personnes, les liens et les souvenirs ajoutés par Awa restent/),
    ).toBeInTheDocument();
    expect(api.requests('DELETE', AWA_MEMBER)).toHaveLength(0);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Retirer de la famille' }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(api.requests('DELETE', AWA_MEMBER)[0]?.headers.get('If-Match')).toBe('"3"');
  });

  it('explains a removal refused because the member changed, in human language', async () => {
    fakeApi({
      handlers: {
        [`DELETE /families/${ADJI_ID}/members/${AWA_MEMBER}`]: () =>
          problemResponse('CONCURRENT_MODIFICATION', 409),
      },
    });
    renderApp(MEMBERS);

    fireEvent.click(
      (await rowOf('Awa')).getByRole('button', { name: 'Retirer Awa de la famille' }),
    );
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Retirer de la famille' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      "L'adhésion de Awa a changé entre-temps.",
    );
    expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
  });

  it('explains LAST_ADMIN_REQUIRED in human language', async () => {
    fakeApi({
      handlers: {
        [`PATCH /families/${ADJI_ID}/members/${AWA_MEMBER}`]: () =>
          problemResponse('LAST_ADMIN_REQUIRED', 409),
      },
    });
    renderApp(MEMBERS);

    fireEvent.change((await rowOf('Awa')).getByRole('combobox', { name: 'Permission de Awa' }), {
      target: { value: 'VIEWER' },
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Une famille doit toujours garder son administrateur',
    );
  });

  describe('pending invitations', () => {
    it('shows for whom, the role, the expiry and an email that could not be sent', async () => {
      fakeApi();
      renderApp(MEMBERS);

      const section = within(await screen.findByRole('region', { name: 'Invitations en attente' }));
      const items = await section.findAllByRole('listitem');
      expect(items).toHaveLength(3);
      const [forMarie, byEmail, byLink] = items.map((item) => within(item));
      expect(forMarie?.getByText('Pour Marie Ngo')).toBeInTheDocument();
      expect(forMarie?.getByText('Peut contribuer')).toBeInTheDocument();
      expect(forMarie?.getByText('Expire le 11 octobre 2026')).toBeInTheDocument();
      expect(byEmail?.getByText('cousin@example.com')).toBeInTheDocument();
      expect(byEmail?.getByText('Lecture seule')).toBeInTheDocument();
      expect(byEmail?.getByText("L'e-mail n'a pas pu être envoyé")).toBeInTheDocument();
      expect(byLink?.getByText('Lien partagé')).toBeInTheDocument();
      expect(forMarie?.queryByText("L'e-mail n'a pas pu être envoyé")).not.toBeInTheDocument();
    });

    it('shows the new link of a renewed invitation once', async () => {
      const api = fakeApi({
        handlers: {
          [`POST /families/${ADJI_ID}/invitations/${AWA_INVITATION}/renew`]: () =>
            jsonResponse({
              ...invitation({ id: AWA_INVITATION, person: { id: 'p5', displayName: 'Marie Ngo' } }),
              version: 3,
              inviteUrl: RENEWED_LINK,
            }),
        },
      });
      renderApp(MEMBERS);

      fireEvent.click(
        await screen.findByRole('button', { name: "Renouveler l'invitation : Pour Marie Ngo" }),
      );

      const dialog = await screen.findByRole('dialog', { name: 'Pour Marie Ngo' });
      expect(within(dialog).getByText(RENEWED_LINK)).toBeInTheDocument();
      expect(
        within(dialog).getByText("Nouveau lien créé. L'ancien lien ne fonctionne plus."),
      ).toBeInTheDocument();
      expect(api.requests('POST', '/renew')[0]?.headers.get('If-Match')).toBe('"2"');

      fireEvent.click(within(dialog).getByRole('button', { name: 'Fermer' }));
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(screen.queryByText(RENEWED_LINK)).not.toBeInTheDocument();
    });

    it('revokes an invitation after a confirmation; it then leaves the list', async () => {
      let invitations = INVITATIONS;
      const api = fakeApi({
        handlers: {
          [`GET /families/${ADJI_ID}/invitations`]: () => jsonResponse(invitations),
          [`POST /families/${ADJI_ID}/invitations/${LINK_INVITATION}/revoke`]: () => {
            invitations = INVITATIONS.filter((item) => item.id !== LINK_INVITATION);
            return new Response(null, { status: 204 });
          },
        },
      });
      renderApp(MEMBERS);

      fireEvent.click(
        await screen.findByRole('button', { name: "Révoquer l'invitation : Lien partagé" }),
      );
      const dialog = await screen.findByRole('dialog', {
        name: "Révoquer l'invitation ? Lien partagé",
      });
      expect(within(dialog).getByText(/C'est définitif/)).toBeInTheDocument();
      fireEvent.click(within(dialog).getByRole('button', { name: "Révoquer l'invitation" }));

      await waitFor(() => {
        expect(screen.queryByText('Lien partagé')).not.toBeInTheDocument();
      });
      expect(api.requests('POST', '/revoke')[0]?.headers.get('If-Match')).toBe('"2"');
      expect(screen.getByText('Pour Marie Ngo')).toBeInTheDocument();
    });

    it('explains a revocation refused because the invitation was already revoked', async () => {
      fakeApi({
        handlers: {
          [`POST /families/${ADJI_ID}/invitations/${LINK_INVITATION}/revoke`]: () =>
            problemResponse('INVITATION_REVOKED', 410),
        },
      });
      renderApp(MEMBERS);

      fireEvent.click(
        await screen.findByRole('button', { name: "Révoquer l'invitation : Lien partagé" }),
      );
      const dialog = await screen.findByRole('dialog');
      fireEvent.click(within(dialog).getByRole('button', { name: "Révoquer l'invitation" }));

      expect(await within(dialog).findByRole('alert')).toHaveTextContent(
        'Cette invitation était déjà révoquée.',
      );
    });
  });

  for (const role of ['CONTRIBUTOR', 'VIEWER']) {
    it(`gives a ${role} no management action and no invitation`, async () => {
      const api = fakeApi({ userId: 'u2', role });
      renderApp(MEMBERS);

      expect(await screen.findByRole('button', { name: 'Quitter cette famille' })).toBeVisible();
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Retirer/ })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Inviter un proche' })).not.toBeInTheDocument();
      expect(screen.queryByText('Invitations en attente')).not.toBeInTheDocument();
      expect(screen.queryByText(/support de Mbia/)).not.toBeInTheDocument();
      expect(api.requests('GET', '/invitations')).toHaveLength(0);
    });
  }

  it('leaves the family after a confirmation, then lands on the other Family', async () => {
    const api = fakeApi({
      userId: 'u2',
      role: 'VIEWER',
      handlers: {
        [`DELETE /families/${ADJI_ID}/members/${AWA_MEMBER}`]: () =>
          new Response(null, { status: 204 }),
        'GET /families': () => jsonResponse([family(OTHER_ID, 'NGO', 'CONTRIBUTOR')]),
      },
    });
    const { router } = renderApp(MEMBERS);

    fireEvent.click(await screen.findByRole('button', { name: 'Quitter cette famille' }));
    const dialog = await screen.findByRole('dialog', { name: 'Quitter cette famille ?' });
    expect(
      within(dialog).getByText(/Les personnes, les liens et les souvenirs que vous avez ajoutés/),
    ).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Quitter la famille' }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(`/families/${OTHER_ID}`);
    });
    expect(api.requests('DELETE', AWA_MEMBER)[0]?.headers.get('If-Match')).toBe('"3"');
  });

  it('leaves the only family, then lands on Family creation', async () => {
    fakeApi({
      userId: 'u2',
      role: 'CONTRIBUTOR',
      handlers: {
        [`DELETE /families/${ADJI_ID}/members/${AWA_MEMBER}`]: () =>
          new Response(null, { status: 204 }),
        'GET /families': () => jsonResponse([]),
      },
    });
    const { router } = renderApp(MEMBERS);

    fireEvent.click(await screen.findByRole('button', { name: 'Quitter cette famille' }));
    fireEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Quitter la famille' }),
    );

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/families/new');
    });
  });

  it('is shown in English', async () => {
    fakeApi({ locale: 'en' });
    await act(() => i18n.changeLanguage('en'));
    renderApp(MEMBERS);

    expect(await screen.findByRole('heading', { level: 1, name: 'Members' })).toBeInTheDocument();
    expect((await rowOf('Awa')).getByText('Your mother')).toBeInTheDocument();
    expect((await rowOf('Tony')).getByText('Family administrator')).toBeInTheDocument();
    expect(await screen.findByText('For Marie Ngo')).toBeInTheDocument();
  });

  it('answers "not found" for a Family the User is not a member of', async () => {
    fakeApi({
      handlers: {
        [`GET /families/${ADJI_ID}`]: () => problemResponse('FAMILY_NOT_FOUND', 404),
        [`GET /families/${ADJI_ID}/members`]: () => problemResponse('FAMILY_NOT_FOUND', 404),
      },
    });
    renderApp(MEMBERS);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Famille introuvable' }),
    ).toBeInTheDocument();
  });
});
