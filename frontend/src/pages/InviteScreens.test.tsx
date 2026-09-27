import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { canInvitePerson } from './PersonProfilePage';

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
const INVITATION_ID = '3a4b5c6d-7e8f-4a0b-9c1d-2e3f4a5b6c7d';
const PROFILE = `/families/${ADJI_ID}/persons/${AWA_ID}`;
const INVITE = `/families/${ADJI_ID}/invitations/new?person=${AWA_ID}`;
const LINK = 'http://localhost:5173/invitations/Zk3-first-token';
const RENEWED_LINK = 'http://localhost:5173/invitations/Qw8-renewed-token';

function family(myRole = 'ADMIN') {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    stats: { personCount: 2, memoryCount: 0, activeMemberCount: 1 },
    limits: { maxPhotosPerMemory: 3 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function awa(overrides: Record<string, unknown> = {}) {
  return {
    id: AWA_ID,
    familyId: ADJI_ID,
    firstName: 'Awa',
    lastName: 'Ngo',
    displayName: 'Awa Ngo',
    gender: 'FEMALE',
    birth: { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    linkedUserId: null,
    relationshipToCurrentUser: 'MOTHER',
    profilePictureUrl: null,
    version: 1,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

function invitation(overrides: Record<string, unknown> = {}) {
  return {
    id: INVITATION_ID,
    familyId: ADJI_ID,
    channel: 'LINK',
    email: null,
    role: 'CONTRIBUTOR',
    status: 'PENDING',
    person: { id: AWA_ID, displayName: 'Awa Ngo' },
    emailDelivery: null,
    invitedBy: { userId: 'u1', displayName: 'Tony', deleted: false },
    expiresAt: '2026-10-11T10:00:00Z',
    createdAt: '2026-09-27T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`). */
function fakeApi({
  role = 'ADMIN',
  locale = 'fr',
  person = () => jsonResponse(awa()),
  invitations = () => jsonResponse([]),
  create = () => jsonResponse({ ...invitation(), inviteUrl: LINK }, 201),
  renew = () => jsonResponse({ ...invitation({ version: 1 }), inviteUrl: RENEWED_LINK }),
}: {
  role?: string;
  /** The User's preferred language, which the application switches to after sign-in. */
  locale?: string;
  person?: Handler;
  invitations?: Handler;
  create?: Handler;
  renew?: Handler;
} = {}) {
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}`]: person,
    [`GET /families/${ADJI_ID}/invitations`]: invitations,
    [`POST /families/${ADJI_ID}/invitations`]: create,
    [`POST /families/${ADJI_ID}/invitations/${INVITATION_ID}/renew`]: renew,
  };
  const requests: Request[] = [];
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({
        id: 'u1',
        email: 'tony@mbia.local',
        displayName: 'Tony',
        preferredLocale: locale,
      });
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    posts: (suffix: string) =>
      requests.filter(
        (request) => request.method === 'POST' && new URL(request.url).pathname.endsWith(suffix),
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

const share = vi.fn<(data: ShareData) => Promise<void>>();
const writeText = vi.fn<(text: string) => Promise<void>>();

function setShare(available: boolean) {
  Object.defineProperty(navigator, 'share', {
    configurable: true,
    value: available ? share : undefined,
  });
}

describe('Invite screens', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    share.mockReset().mockResolvedValue(undefined);
    writeText.mockReset().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    setShare(true);
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('canInvitePerson', () => {
    const living = awa() as Parameters<typeof canInvitePerson>[0];

    it('allows only the ADMIN, on an ACTIVE, living Person linked to no User', () => {
      expect(canInvitePerson(living, 'ADMIN')).toBe(true);
      expect(canInvitePerson(living, 'CONTRIBUTOR')).toBe(false);
      expect(canInvitePerson(living, 'VIEWER')).toBe(false);
      expect(canInvitePerson(living, undefined)).toBe(false);
      expect(canInvitePerson({ ...living, isDeceased: true }, 'ADMIN')).toBe(false);
      expect(canInvitePerson({ ...living, linkedUserId: 'u2' }, 'ADMIN')).toBe(false);
      expect(canInvitePerson({ ...living, status: 'ARCHIVED' }, 'ADMIN')).toBe(false);
      expect(canInvitePerson({ ...living, status: 'MERGED' }, 'ADMIN')).toBe(false);
    });
  });

  describe('Person profile (SCREEN-005)', () => {
    it('offers the ADMIN `Invite Awa`, leading to SCREEN-009 with Awa', async () => {
      fakeApi();
      const { router } = renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('link', { name: 'Inviter Awa' }));

      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/invitations/new`);
      expect(router.state.location.search).toBe(`?person=${AWA_ID}`);
      expect(
        await screen.findByRole('heading', { level: 1, name: 'Inviter Awa Ngo' }),
      ).toBeInTheDocument();
    });

    it.each([
      ['a CONTRIBUTOR', 'CONTRIBUTOR', awa()],
      ['a VIEWER', 'VIEWER', awa()],
      ['a deceased Person', 'ADMIN', awa({ isDeceased: true })],
      ['a Person linked to a User', 'ADMIN', awa({ linkedUserId: 'u2' })],
      ['an archived Person', 'ADMIN', awa({ status: 'ARCHIVED' })],
    ])('offers no invitation for %s', async (_, role, person) => {
      fakeApi({ role, person: () => jsonResponse(person) });
      renderApp(PROFILE);

      expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Awa Ngo');
      expect(screen.queryByRole('link', { name: 'Inviter Awa' })).not.toBeInTheDocument();
      expect(screen.queryByText('Invitation en attente')).not.toBeInTheDocument();
    });

    it('shows `Invitation pending` with `Renew`, whose new link is shown once', async () => {
      const api = fakeApi({ invitations: () => jsonResponse([invitation()]) });
      renderApp(PROFILE);

      expect(await screen.findByText('Invitation en attente')).toBeInTheDocument();
      expect(screen.getByText('Expire le 11 octobre 2026')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Inviter Awa' })).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: "Renouveler l'invitation de Awa" }));

      expect(
        await screen.findByText("Nouveau lien créé. L'ancien lien ne fonctionne plus."),
      ).toBeInTheDocument();
      expect(screen.getByText(RENEWED_LINK)).toBeInTheDocument();
      expect(
        screen.getByText(
          `Tony t'invite dans la famille ADJI sur Mbia, ton profil Awa Ngo t'y attend : ${RENEWED_LINK}`,
        ),
      ).toBeInTheDocument();
      const [renewal] = api.posts('/renew');
      expect(renewal?.headers.get('If-Match')).toBe('"0"');
    });

    it('explains a refused renewal in human language', async () => {
      fakeApi({
        invitations: () => jsonResponse([invitation()]),
        renew: () => problemResponse('INVITATION_ALREADY_USED', 410),
      });
      renderApp(PROFILE);

      fireEvent.click(
        await screen.findByRole('button', { name: "Renouveler l'invitation de Awa" }),
      );

      expect(
        await screen.findByText(
          "Cette invitation vient d'être utilisée : Awa a sans doute rejoint la famille.",
        ),
      ).toHaveAttribute('role', 'alert');
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });
  });

  describe('Invite Member (SCREEN-009)', () => {
    it('creates a link for Awa as Can contribute, then copies and shares it', async () => {
      const api = fakeApi();
      renderApp(INVITE);

      const permission = await screen.findByRole('combobox', { name: 'Permission' });
      expect(permission).toHaveValue('CONTRIBUTOR');
      expect(within(permission).getByRole('option', { selected: true })).toHaveTextContent(
        'Peut contribuer',
      );
      fireEvent.click(screen.getByRole('button', { name: 'Créer le lien' }));

      expect(await screen.findByRole('heading', { name: 'Le lien est prêt' })).toBeInTheDocument();
      const [creation] = api.posts('/invitations');
      expect(await creation?.json()).toEqual({
        channel: 'LINK',
        role: 'CONTRIBUTOR',
        personId: AWA_ID,
      });
      expect(screen.getByText(LINK)).toBeInTheDocument();
      expect(
        screen.getByText("Ce lien ne fonctionne qu'une fois et expire dans 14 jours."),
      ).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Copier le lien' }));
      expect(await screen.findByRole('status')).toHaveTextContent('Lien copié.');
      expect(writeText).toHaveBeenCalledWith(LINK);

      fireEvent.click(screen.getByRole('button', { name: 'Partager' }));
      expect(share).toHaveBeenCalledWith({
        text: `Tony t'invite dans la famille ADJI sur Mbia, ton profil Awa Ngo t'y attend : ${LINK}`,
      });
    });

    it('writes the message in English, and invites as Read only', async () => {
      const api = fakeApi({
        locale: 'en',
        create: () => jsonResponse({ ...invitation({ role: 'VIEWER' }), inviteUrl: LINK }, 201),
      });
      renderApp(INVITE);

      fireEvent.change(await screen.findByRole('combobox', { name: 'Permission' }), {
        target: { value: 'VIEWER' },
      });
      expect(
        screen.getByText('Can look at the family, without changing anything.'),
      ).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Create the link' }));

      expect(
        await screen.findByText(
          `Tony invites you to the ADJI family on Mbia, your profile Awa Ngo is waiting for you: ${LINK}`,
        ),
      ).toBeInTheDocument();
      const [creation] = api.posts('/invitations');
      expect(await creation?.json()).toMatchObject({ role: 'VIEWER' });
    });

    it('offers no `Share` without a device share sheet, and names no Person without one', async () => {
      setShare(false);
      fakeApi({
        create: () => jsonResponse({ ...invitation({ person: null }), inviteUrl: LINK }, 201),
      });
      renderApp(`/families/${ADJI_ID}/invitations/new`);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Inviter un proche' }),
      ).toBeInTheDocument();
      fireEvent.click(await screen.findByRole('button', { name: 'Créer le lien' }));

      expect(
        await screen.findByText(`Tony t'invite dans la famille ADJI sur Mbia : ${LINK}`),
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Copier le lien' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Partager' })).not.toBeInTheDocument();
    });

    it('starts a new invitation without Person with `Invite someone else`', async () => {
      const { posts } = fakeApi();
      const { router } = renderApp(INVITE);

      fireEvent.click(await screen.findByRole('button', { name: 'Créer le lien' }));
      fireEvent.click(await screen.findByRole('button', { name: "Inviter quelqu'un d'autre" }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Inviter un proche' }),
      ).toBeInTheDocument();
      expect(router.state.location.search).toBe('');
      expect(screen.queryByText(LINK)).not.toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: 'Permission' })).toHaveValue('CONTRIBUTOR');
      expect(posts('/invitations')).toHaveLength(1);
    });

    it.each([
      [
        'INVITATION_ALREADY_PENDING',
        409,
        'Une invitation pour Awa Ngo est déjà en attente. Renouvelez-la depuis son profil pour obtenir un nouveau lien.',
      ],
      [
        'PERSON_ALREADY_CLAIMED',
        409,
        "Awa Ngo est déjà lié(e) à un membre de la famille : il n'y a personne à inviter.",
      ],
      [
        'PERSON_NOT_FOUND',
        404,
        'Awa Ngo a été archivé(e) ou fusionné(e) : cette personne ne peut pas être invitée.',
      ],
    ])('explains %s in human language', async (code, status, message) => {
      fakeApi({ create: () => problemResponse(code, status) });
      renderApp(INVITE);

      fireEvent.click(await screen.findByRole('button', { name: 'Créer le lien' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(message);
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Retour au profil de Awa Ngo' })).toHaveAttribute(
        'href',
        PROFILE,
      );
    });

    it('explains why a linked Person cannot be invited, without the form', async () => {
      fakeApi({ person: () => jsonResponse(awa({ linkedUserId: 'u2' })) });
      renderApp(INVITE);

      expect(
        await screen.findByText(
          "Awa Ngo est déjà lié(e) à un membre de la famille : il n'y a personne à inviter.",
        ),
      ).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Créer le lien' })).not.toBeInTheDocument();
    });

    it('tells a member who is not the ADMIN that only the administrator invites', async () => {
      const { posts } = fakeApi({ role: 'CONTRIBUTOR' });
      renderApp(INVITE);

      expect(
        await screen.findByText("Seul l'administrateur de la famille peut inviter des proches."),
      ).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Créer le lien' })).not.toBeInTheDocument();
      expect(posts('/invitations')).toHaveLength(0);
    });
  });

  describe('Invite by email (PR-51)', () => {
    const ADDRESS = 'awa@example.com';

    function emailInvitation(overrides: Record<string, unknown> = {}) {
      return {
        ...invitation({ channel: 'EMAIL', email: ADDRESS, emailDelivery: 'SENT', ...overrides }),
        inviteUrl: LINK,
      };
    }

    function setLargeScreen(large: boolean | null) {
      Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value:
          large === null
            ? undefined
            : (query: string) => ({
                matches: large && query === '(min-width: 768px)',
                media: query,
              }),
      });
    }

    afterEach(() => {
      setLargeScreen(null);
    });

    it('offers `Share a link` first on a phone, without email field', async () => {
      fakeApi();
      setLargeScreen(false);
      renderApp(INVITE);
      const phone = await screen.findByRole('combobox', { name: 'Comment inviter' });
      expect(phone).toHaveValue('LINK');
      expect(
        within(phone)
          .getAllByRole('option')
          .map((option) => option.textContent),
      ).toEqual(['Partager un lien', 'Envoyer par e-mail']);
      expect(screen.queryByLabelText('Adresse e-mail')).not.toBeInTheDocument();
    });

    it('preselects `Send by email` on a larger screen', async () => {
      fakeApi();
      setLargeScreen(true);
      renderApp(INVITE);

      expect(await screen.findByRole('combobox', { name: 'Comment inviter' })).toHaveValue('EMAIL');
      expect(screen.getByLabelText('Adresse e-mail')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: "Envoyer l'invitation" })).toBeInTheDocument();
    });

    it('requires a valid address, then sends the email in the current language', async () => {
      const api = fakeApi({ create: () => jsonResponse(emailInvitation(), 201) });
      renderApp(INVITE);

      fireEvent.change(await screen.findByRole('combobox', { name: 'Comment inviter' }), {
        target: { value: 'EMAIL' },
      });
      fireEvent.click(screen.getByRole('button', { name: "Envoyer l'invitation" }));
      expect(await screen.findByText("Saisissez l'adresse e-mail.")).toBeInTheDocument();
      fireEvent.change(screen.getByLabelText('Adresse e-mail'), { target: { value: 'awa@' } });
      fireEvent.click(screen.getByRole('button', { name: "Envoyer l'invitation" }));
      expect(
        await screen.findByText(
          'Saisissez une adresse e-mail valide, par exemple awa@example.com.',
        ),
      ).toBeInTheDocument();
      expect(api.posts('/invitations')).toHaveLength(0);

      fireEvent.change(screen.getByLabelText('Adresse e-mail'), {
        target: { value: ` ${ADDRESS} ` },
      });
      fireEvent.click(screen.getByRole('button', { name: "Envoyer l'invitation" }));

      expect(
        await screen.findByRole('heading', { name: 'Invitation envoyée' }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "Le lien a été envoyé à awa@example.com. Il ne fonctionne qu'une fois et expire dans 14 jours.",
        ),
      ).toBeInTheDocument();
      // The link is not shown: Mbia sent it.
      expect(screen.queryByText(LINK)).not.toBeInTheDocument();
      const [creation] = api.posts('/invitations');
      expect(await creation?.json()).toEqual({
        channel: 'EMAIL',
        role: 'CONTRIBUTOR',
        personId: AWA_ID,
        email: ADDRESS,
        locale: 'fr',
      });
    });

    it('asks for the email in English for an inviter using Mbia in English', async () => {
      const api = fakeApi({ locale: 'en', create: () => jsonResponse(emailInvitation(), 201) });
      renderApp(INVITE);

      fireEvent.change(await screen.findByRole('combobox', { name: 'How to invite' }), {
        target: { value: 'EMAIL' },
      });
      fireEvent.change(screen.getByLabelText('Email address'), { target: { value: ADDRESS } });
      fireEvent.click(screen.getByRole('button', { name: 'Send the invitation' }));

      expect(await screen.findByRole('heading', { name: 'Invitation sent' })).toBeInTheDocument();
      const [creation] = api.posts('/invitations');
      expect(await creation?.json()).toMatchObject({ channel: 'EMAIL', locale: 'en' });
    });

    it('says when the email could not be sent, the invitation being saved', async () => {
      fakeApi({ create: () => jsonResponse(emailInvitation({ emailDelivery: 'FAILED' }), 201) });
      renderApp(INVITE);

      fireEvent.change(await screen.findByRole('combobox', { name: 'Comment inviter' }), {
        target: { value: 'EMAIL' },
      });
      fireEvent.change(screen.getByLabelText('Adresse e-mail'), { target: { value: ADDRESS } });
      fireEvent.click(screen.getByRole('button', { name: "Envoyer l'invitation" }));

      expect(
        await screen.findByRole('heading', { name: "L'e-mail n'a pas pu être envoyé" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "L'invitation est enregistrée. Renouvelez-la plus tard pour envoyer l'e-mail à nouveau.",
        ),
      ).toBeInTheDocument();
      expect(screen.queryByText('Invitation envoyée')).not.toBeInTheDocument();
    });

    it('shows an address refused by the server on its field', async () => {
      fakeApi({
        create: () =>
          jsonResponse(
            {
              code: 'VALIDATION_FAILED',
              status: 400,
              title: 'VALIDATION_FAILED',
              detail: 'raw server detail',
              fieldErrors: [{ field: 'email', code: 'EMAIL', message: 'raw' }],
            },
            400,
            'application/problem+json',
          ),
      });
      renderApp(INVITE);

      fireEvent.change(await screen.findByRole('combobox', { name: 'Comment inviter' }), {
        target: { value: 'EMAIL' },
      });
      fireEvent.change(screen.getByLabelText('Adresse e-mail'), { target: { value: ADDRESS } });
      fireEvent.click(screen.getByRole('button', { name: "Envoyer l'invitation" }));

      expect(
        await screen.findByText(
          'Saisissez une adresse e-mail valide, par exemple awa@example.com.',
        ),
      ).toBeInTheDocument();
      expect(screen.queryByText(/décédé/)).not.toBeInTheDocument();
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    });

    it('says on the profile that the email of the pending invitation could not be sent', async () => {
      fakeApi({
        invitations: () => jsonResponse([emailInvitation({ emailDelivery: 'FAILED' })]),
      });
      renderApp(PROFILE);

      expect(await screen.findByText('Invitation en attente')).toBeInTheDocument();
      expect(screen.getByText("L'e-mail n'a pas pu être envoyé")).toBeInTheDocument();
    });

    it('sends the email again on `Renew`, without showing the link', async () => {
      const api = fakeApi({
        invitations: () => jsonResponse([emailInvitation({ emailDelivery: 'FAILED' })]),
        renew: () => jsonResponse({ ...emailInvitation({ version: 2 }), inviteUrl: RENEWED_LINK }),
      });
      renderApp(PROFILE);

      fireEvent.click(
        await screen.findByRole('button', { name: "Renouveler l'invitation de Awa" }),
      );

      expect(
        await screen.findByRole('heading', { name: 'Invitation envoyée' }),
      ).toBeInTheDocument();
      expect(screen.queryByText(RENEWED_LINK)).not.toBeInTheDocument();
      expect(api.posts('/renew')).toHaveLength(1);
    });
  });
});
