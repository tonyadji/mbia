import { act, fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { App } from './App';
import { createQueryClient } from './queryClient';
import { routes } from './routes';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: object, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number) {
  return jsonResponse({ code, status, title: code }, status, 'application/problem+json');
}

const alice = { id: '0b5c1f3e-1c1a-4a57-9a55-8f2f6c1d2e01', email: 'alice@mbia.local' };

function renderApp(path: string, userManager = fakeUserManager()) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App router={router} queryClient={createQueryClient()} userManager={userManager} />);
  return { router, userManager };
}

describe('App', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('welcome screen (SCREEN-001)', () => {
    it('shows the value proposition and both actions in French', async () => {
      renderApp('/');

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Conservez ce qui vous relie.' }),
      ).toBeInTheDocument();
      expect(screen.getByText('Mbia')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Créer ma famille' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
    });

    it('switches to English', async () => {
      renderApp('/');
      await screen.findByRole('button', { name: 'Créer ma famille' });

      await act(() => i18n.changeLanguage('en'));

      expect(
        screen.getByRole('heading', { level: 1, name: 'Keep what connects you.' }),
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Create my family' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    });

    it('sends a visitor to the Keycloak registration page in the UI language', async () => {
      await act(() => i18n.changeLanguage('en'));
      const { userManager } = renderApp('/');

      fireEvent.click(await screen.findByRole('button', { name: 'Create my family' }));

      expect(userManager.signinRedirect).toHaveBeenCalledWith({
        state: { returnTo: '/home' },
        prompt: 'create',
        ui_locales: 'en',
      });
    });

    it('sends a visitor to the Keycloak sign-in page in the UI language', async () => {
      const { userManager } = renderApp('/');

      fireEvent.click(await screen.findByRole('button', { name: 'Se connecter' }));

      expect(userManager.signinRedirect).toHaveBeenCalledWith({
        state: { returnTo: '/home' },
        ui_locales: 'fr',
      });
    });

    it('takes a signed-in User straight to the app', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ ...alice, displayName: 'Alice', preferredLocale: 'fr' }),
      );
      const userManager = fakeUserManager(fakeOidcUser());
      const { router } = renderApp('/', userManager);
      // Let the provider read the stored session.
      await act(() => Promise.resolve());

      fireEvent.click(screen.getByRole('button', { name: 'Créer ma famille' }));

      expect(router.state.location.pathname).toBe('/home');
      expect(userManager.signinRedirect).not.toHaveBeenCalled();
    });
  });

  describe('protected route', () => {
    it('starts sign-in and comes back to the requested page', async () => {
      const { userManager } = renderApp('/home?tab=1');

      await vi.waitFor(() => {
        expect(userManager.signinRedirect).toHaveBeenCalledWith({
          state: { returnTo: '/home?tab=1' },
          ui_locales: 'fr',
        });
      });
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('greets the signed-in User and applies their stored language', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ ...alice, displayName: 'Alice', preferredLocale: 'en' }),
      );
      renderApp('/home', fakeUserManager(fakeOidcUser({ access_token: 'token-alice' })));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Hello Alice' }),
      ).toBeInTheDocument();
      expect(i18n.language).toBe('en');
      const request = fetchMock.mock.calls[0]?.[0] as Request;
      expect(request.url).toMatch(/\/me$/);
      expect(request.headers.get('Authorization')).toBe('Bearer token-alice');
    });

    it('greets a User without a display name', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ ...alice, displayName: null, preferredLocale: 'fr' }),
      );
      renderApp('/home', fakeUserManager(fakeOidcUser()));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Bonjour !' }),
      ).toBeInTheDocument();
    });

    it('signs out without starting a new sign-in', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ ...alice, displayName: 'Alice', preferredLocale: 'fr' }),
      );
      const userManager = fakeUserManager(fakeOidcUser());
      userManager.signoutRedirect.mockImplementation(() => userManager.removeUser());
      renderApp('/home', userManager);

      fireEvent.click(await screen.findByRole('button', { name: 'Se déconnecter' }));

      await vi.waitFor(() => {
        expect(userManager.removeUser).toHaveBeenCalled();
      });
      expect(userManager.signoutRedirect).toHaveBeenCalledWith({
        extraQueryParams: { ui_locales: 'fr' },
      });
      expect(userManager.signinRedirect).not.toHaveBeenCalled();
    });

    it('restarts sign-in when the API answers 401', async () => {
      fetchMock.mockResolvedValue(problemResponse('AUTHENTICATION_REQUIRED', 401));
      const userManager = fakeUserManager(fakeOidcUser());
      renderApp('/home', userManager);

      await vi.waitFor(() => {
        expect(userManager.signinRedirect).toHaveBeenCalledWith({
          state: { returnTo: '/home' },
          ui_locales: 'fr',
        });
      });
      expect(userManager.removeUser).toHaveBeenCalled();
    });

    it('asks to check the inbox when the email is not verified', async () => {
      fetchMock.mockResolvedValue(problemResponse('EMAIL_NOT_VERIFIED', 403));
      const userManager = fakeUserManager(fakeOidcUser());
      renderApp('/home', userManager);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Vérifiez votre boîte mail' }),
      ).toBeInTheDocument();
      expect(fetchMock).toHaveBeenCalledTimes(1);

      fireEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }));

      expect(userManager.signoutRedirect).toHaveBeenCalled();
    });

    it('shows a translated message and a retry on other errors', async () => {
      fetchMock.mockResolvedValue(problemResponse('SOMETHING_NEW', 400));
      renderApp('/home', fakeUserManager(fakeOidcUser()));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "Une erreur inattendue s'est produite. Réessayez.",
      );
      expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
    });
  });

  describe('sign-in callback', () => {
    it('goes to the page requested before sign-in', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ ...alice, displayName: 'Alice', preferredLocale: 'fr' }),
      );
      const userManager = fakeUserManager();
      userManager.signinRedirectCallback.mockResolvedValue(
        fakeOidcUser({ state: { returnTo: '/home?tab=1' } }),
      );
      const { router } = renderApp('/auth/callback?code=abc&state=xyz', userManager);

      await vi.waitFor(() => {
        expect(router.state.location.pathname + router.state.location.search).toBe('/home?tab=1');
      });
      expect(userManager.signinRedirectCallback).toHaveBeenCalledTimes(1);
    });

    it('never leaves the app after sign-in', async () => {
      const userManager = fakeUserManager();
      userManager.signinRedirectCallback.mockResolvedValue(
        fakeOidcUser({ state: { returnTo: '//evil.example' } }),
      );
      const { router } = renderApp('/auth/callback?code=abc&state=xyz', userManager);

      await vi.waitFor(() => {
        expect(router.state.location.pathname).toBe('/home');
      });
    });

    it('shows a translated message when sign-in fails', async () => {
      const userManager = fakeUserManager();
      userManager.signinRedirectCallback.mockRejectedValue(new Error('No matching state found'));
      renderApp('/auth/callback?error=access_denied', userManager);

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "La connexion n'a pas abouti. Réessayez.",
      );
      expect(screen.queryByText(/No matching state/)).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: "Retour à l'accueil" })).toBeInTheDocument();
    });
  });
});
