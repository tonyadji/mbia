import type { User, UserManager, UserManagerEvents } from 'oidc-client-ts';
import { createContext, use, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setAccessTokenProvider, setUnauthorizedHandler } from '../api/client';
import { i18n } from '../i18n';

export const HOME_PATH = '/home';

/** Family creation: where `Create my family` leads (SCREEN-001), including after sign-up. */
export const CREATE_FAMILY_PATH = '/families/new';

/** The part of the `oidc-client-ts` UserManager that the application uses. */
export type AuthUserManager = Pick<
  UserManager,
  | 'getUser'
  | 'removeUser'
  | 'signinRedirect'
  | 'signinRedirectCallback'
  | 'signinSilent'
  | 'signoutRedirect'
> & {
  events: Pick<
    UserManagerEvents,
    | 'addUserLoaded'
    | 'removeUserLoaded'
    | 'addUserUnloaded'
    | 'removeUserUnloaded'
    | 'addSilentRenewError'
    | 'removeSilentRenewError'
  >;
};

interface SignInState {
  returnTo: string;
}

interface Auth {
  user: User | null;
  isLoading: boolean;
  isSigningOut: boolean;
  /** Redirects to the Keycloak sign-in page, then back to `returnTo`. */
  signIn: (returnTo?: string) => Promise<void>;
  /** Redirects to the Keycloak registration page, then to Family creation. */
  signUp: () => Promise<void>;
  signOut: () => Promise<void>;
  /** Completes the redirect from Keycloak; resolves to the in-app path to go to. */
  completeSignIn: () => Promise<string>;
}

const AuthContext = createContext<Auth | null>(null);

/** Keycloak pages follow the UI language (`ui_locales`). */
function uiLocales() {
  return i18n.language;
}

/** Only in-app paths are accepted as a destination after sign-in (no open redirect). */
function safeReturnTo(state: unknown): string {
  const returnTo = (state as Partial<SignInState> | undefined)?.returnTo;
  return typeof returnTo === 'string' && returnTo.startsWith('/') && !returnTo.startsWith('//')
    ? returnTo
    : HOME_PATH;
}

async function loadUser(userManager: AuthUserManager): Promise<User | null> {
  const user = await userManager.getUser();
  if (!user?.expired) return user;
  try {
    return await userManager.signinSilent();
  } catch {
    await userManager.removeUser();
    return null;
  }
}

export function AuthProvider({
  userManager,
  children,
}: {
  userManager: AuthUserManager;
  children: ReactNode;
}) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSigningOut, setIsSigningOut] = useState(false);

  const auth = useMemo<Auth>(() => {
    const signIn = (returnTo: string = HOME_PATH) =>
      userManager.signinRedirect({ state: { returnTo }, ui_locales: uiLocales() });
    return {
      user,
      isLoading,
      isSigningOut,
      signIn,
      signUp: () =>
        userManager.signinRedirect({
          state: { returnTo: CREATE_FAMILY_PATH },
          prompt: 'create',
          ui_locales: uiLocales(),
        }),
      signOut: () => {
        // Removing the user must not make a protected page start a new sign-in.
        setIsSigningOut(true);
        return userManager.signoutRedirect({ extraQueryParams: { ui_locales: uiLocales() } });
      },
      completeSignIn: async () => safeReturnTo((await userManager.signinRedirectCallback()).state),
    };
  }, [userManager, user, isLoading, isSigningOut]);

  useEffect(() => {
    let active = true;
    void loadUser(userManager).then((loaded) => {
      if (!active) return;
      setUser(loaded);
      setIsLoading(false);
    });

    const onLoaded = (loaded: User) => {
      setUser(loaded);
    };
    const onUnloaded = () => {
      setUser(null);
    };
    const onRenewError = () => void userManager.removeUser();
    userManager.events.addUserLoaded(onLoaded);
    userManager.events.addUserUnloaded(onUnloaded);
    userManager.events.addSilentRenewError(onRenewError);

    setAccessTokenProvider(async () => (await userManager.getUser())?.access_token ?? null);
    // The session is no longer accepted: forget it, and the protected route starts a new sign-in.
    setUnauthorizedHandler(() => void userManager.removeUser());

    return () => {
      active = false;
      userManager.events.removeUserLoaded(onLoaded);
      userManager.events.removeUserUnloaded(onUnloaded);
      userManager.events.removeSilentRenewError(onRenewError);
      setAccessTokenProvider(() => null);
      setUnauthorizedHandler(() => undefined);
    };
  }, [userManager]);

  return <AuthContext value={auth}>{children}</AuthContext>;
}

export function useAuth(): Auth {
  const auth = use(AuthContext);
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return auth;
}
