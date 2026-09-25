import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
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

function jsonResponse(body: object, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number, detail = 'raw server detail') {
  return jsonResponse({ code, status, title: code, detail }, status, 'application/problem+json');
}

interface StoredUser {
  id: string;
  email: string;
  displayName: string | null;
  preferredLocale: 'fr' | 'en';
}

/** Alice's only Family, where `/home` leads. */
const adjiFamily = {
  id: '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b',
  name: 'ADJI',
  myRole: 'ADMIN',
  stats: { personCount: 0, memoryCount: 0, activeMemberCount: 1 },
  version: 0,
  createdAt: '2026-09-25T10:00:00Z',
  updatedAt: '2026-09-25T10:00:00Z',
};

/** An in-memory `/me` that keeps what `PATCH /me` stores, like the backend. */
function fakeMeApi(initial: Partial<StoredUser> = {}) {
  const stored: StoredUser = {
    id: '0b5c1f3e-1c1a-4a57-9a55-8f2f6c1d2e01',
    email: 'alice@mbia.local',
    displayName: 'Alice',
    preferredLocale: 'fr',
    ...initial,
  };
  const patches: unknown[] = [];
  let failNextPatch: Response | null = null;
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const path = new URL(request.url).pathname;
    if (path.endsWith('/families')) return jsonResponse([adjiFamily]);
    if (path.includes('/families/')) return jsonResponse(adjiFamily);
    if (request.method === 'PATCH') {
      const body = (await request.json()) as Partial<StoredUser>;
      patches.push(body);
      if (failNextPatch) {
        const failure = failNextPatch;
        failNextPatch = null;
        return failure;
      }
      Object.assign(stored, body);
    }
    return jsonResponse({ ...stored });
  });
  return {
    stored,
    patches,
    failNextPatchWith: (response: Response) => {
      failNextPatch = response;
    },
  };
}

function renderApp(path: string, userManager = fakeUserManager(fakeOidcUser())) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App router={router} queryClient={createQueryClient()} userManager={userManager} />);
  return { router, userManager };
}

async function openSettings() {
  const result = renderApp('/settings');
  await screen.findByRole('heading', { level: 1, name: 'Paramètres du compte' });
  return result;
}

describe('Account settings (SCREEN-011)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    vi.stubEnv('VITE_SUPPORT_EMAIL', 'support@mbia.local');
    // Browser language: French.
    await act(() => i18n.changeLanguage('fr'));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('is reached from the Family home through the avatar', async () => {
    fakeMeApi();
    const { router } = renderApp('/home');

    const avatar = await screen.findByRole('link', { name: 'Paramètres du compte' });
    expect(avatar).toHaveTextContent('A');
    fireEvent.click(avatar);

    expect(router.state.location.pathname).toBe('/settings');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Paramètres du compte' }),
    ).toBeInTheDocument();
  });

  it('shows the display name to edit and the email read-only', async () => {
    fakeMeApi();
    await openSettings();

    expect(screen.getByRole('textbox', { name: 'Nom affiché' })).toHaveValue('Alice');
    expect(screen.getByText('alice@mbia.local')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('alice@mbia.local')).not.toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
  });

  describe('display name', () => {
    it('saves the trimmed name and confirms it', async () => {
      const api = fakeMeApi();
      await openSettings();

      fireEvent.change(screen.getByRole('textbox', { name: 'Nom affiché' }), {
        target: { value: '  Alice Mbarga  ' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(await screen.findByRole('status')).toHaveTextContent('Votre nom a été enregistré.');
      expect(api.patches).toEqual([{ displayName: 'Alice Mbarga' }]);
      expect(screen.getByRole('textbox', { name: 'Nom affiché' })).toHaveValue('Alice Mbarga');
    });

    it('requires a name, with a translated message and no request', async () => {
      const api = fakeMeApi();
      await openSettings();
      const field = screen.getByRole('textbox', { name: 'Nom affiché' });

      fireEvent.change(field, { target: { value: '   ' } });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(await screen.findByText('Indiquez le nom à afficher.')).toBeInTheDocument();
      expect(field).toHaveAttribute('aria-invalid', 'true');
      expect(field).toHaveAccessibleDescription('Indiquez le nom à afficher.');
      expect(api.patches).toEqual([]);

      await act(() => i18n.changeLanguage('en'));
      expect(screen.getByText('Enter the name to display.')).toBeInTheDocument();
    });

    it('refuses a name longer than 200 characters', async () => {
      const api = fakeMeApi();
      await openSettings();

      fireEvent.change(screen.getByRole('textbox', { name: 'Nom affiché' }), {
        target: { value: 'a'.repeat(201) },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(
        await screen.findByText('Le nom ne peut pas dépasser 200 caractères.'),
      ).toBeInTheDocument();
      expect(api.patches).toEqual([]);
    });

    it('translates a server rejection and never shows the raw message', async () => {
      const api = fakeMeApi();
      api.failNextPatchWith(problemResponse('VALIDATION_FAILED', 400));
      await openSettings();

      fireEvent.change(screen.getByRole('textbox', { name: 'Nom affiché' }), {
        target: { value: 'Alice M.' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Certaines informations ne sont pas valides. Vérifiez-les puis réessayez.',
      );
      expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });
  });

  describe('language', () => {
    it('switches the UI at once and stores the choice on the User', async () => {
      const api = fakeMeApi();
      await openSettings();

      fireEvent.change(screen.getByRole('combobox', { name: 'Langue' }), {
        target: { value: 'en' },
      });

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Account settings' }),
      ).toBeInTheDocument();
      expect(document.documentElement.lang).toBe('en');
      await vi.waitFor(() => {
        expect(api.patches).toEqual([{ preferredLocale: 'en' }]);
      });
      expect(screen.getByRole('combobox', { name: 'Language' })).toHaveValue('en');
    });

    it('keeps the stored choice on the next sign-in, on another browser', async () => {
      const api = fakeMeApi();
      await openSettings();
      fireEvent.change(screen.getByRole('combobox', { name: 'Langue' }), {
        target: { value: 'en' },
      });
      await vi.waitFor(() => {
        expect(api.stored.preferredLocale).toBe('en');
      });

      // Another browser: nothing is kept locally, the browser language is French.
      cleanup();
      await act(() => i18n.changeLanguage('fr'));
      renderApp('/home');

      expect(
        await screen.findByRole('heading', { level: 2, name: 'Welcome to the ADJI family' }),
      ).toBeInTheDocument();
      expect(i18n.language).toBe('en');
    });

    it('goes back to the previous language when the choice cannot be stored', async () => {
      const api = fakeMeApi();
      api.failNextPatchWith(problemResponse('SOMETHING_NEW', 500));
      await openSettings();

      fireEvent.change(screen.getByRole('combobox', { name: 'Langue' }), {
        target: { value: 'en' },
      });

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "Une erreur inattendue s'est produite. Réessayez.",
      );
      expect(i18n.language).toBe('fr');
      expect(screen.getByRole('combobox', { name: 'Langue' })).toHaveValue('fr');
    });
  });

  it('links to the Keycloak account page to change the password, with a way back', async () => {
    fakeMeApi();
    await openSettings();

    const link = screen.getByRole('link', { name: 'Changer mon mot de passe' });
    const url = new URL(link.getAttribute('href') ?? '');
    expect(url.origin + url.pathname).toBe('http://localhost:8081/realms/mbia/account');
    expect(url.searchParams.get('referrer')).toBe('mbia-web');
    expect(url.searchParams.get('referrer_uri')).toBe('http://localhost:3000/settings');
  });

  it('signs out', async () => {
    fakeMeApi();
    const { userManager } = await openSettings();

    fireEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }));

    expect(userManager.signoutRedirect).toHaveBeenCalledWith({
      extraQueryParams: { ui_locales: 'fr' },
    });
  });

  describe('delete my account', () => {
    it('explains the procedure and links to the support contact', async () => {
      fakeMeApi();
      await openSettings();

      const section = screen
        .getByRole('heading', { name: 'Supprimer mon compte' })
        .closest('section');
      if (section === null) throw new Error('The deletion section is missing');
      expect(within(section).getByText(/traitée sous 30 jours/)).toBeInTheDocument();
      expect(within(section).getByText(/« Ancien membre »/)).toBeInTheDocument();
      expect(within(section).getByRole('link', { name: "Contacter l'assistance" })).toHaveAttribute(
        'href',
        `mailto:support@mbia.local?subject=${encodeURIComponent('Suppression de mon compte Mbia')}`,
      );
    });

    it('shows no contact link when no support address is configured', async () => {
      vi.stubEnv('VITE_SUPPORT_EMAIL', '');
      fakeMeApi();
      await openSettings();

      expect(
        screen.queryByRole('link', { name: "Contacter l'assistance" }),
      ).not.toBeInTheDocument();
      expect(
        screen.getByText("L'adresse de l'assistance n'est pas encore disponible."),
      ).toBeInTheDocument();
    });
  });
});
