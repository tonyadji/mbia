import type { User } from 'oidc-client-ts';
import type { AuthUserManager } from '../auth/AuthProvider';

export function fakeOidcUser(overrides: Partial<User> = {}): User {
  return { access_token: 'access-token', expired: false, state: undefined, ...overrides } as User;
}

/** An in-memory UserManager: no redirect happens, calls are recorded. */
export function fakeUserManager(initialUser: User | null = null) {
  let user = initialUser;
  const unloaded = new Set<() => void>();
  const manager = {
    getUser: vi.fn(() => Promise.resolve(user)),
    removeUser: vi.fn(() => {
      user = null;
      unloaded.forEach((listener) => {
        listener();
      });
      return Promise.resolve();
    }),
    signinRedirect: vi.fn(() => Promise.resolve()),
    signinRedirectCallback: vi.fn(() => Promise.resolve(fakeOidcUser())),
    signinSilent: vi.fn(() => Promise.resolve(null)),
    signoutRedirect: vi.fn(() => Promise.resolve()),
    events: {
      addUserLoaded: vi.fn(),
      removeUserLoaded: vi.fn(),
      addUserUnloaded: vi.fn((listener: () => void) => {
        unloaded.add(listener);
        return () => unloaded.delete(listener);
      }),
      removeUserUnloaded: vi.fn((listener: () => void) => {
        unloaded.delete(listener);
      }),
      addSilentRenewError: vi.fn(),
      removeSilentRenewError: vi.fn(),
    },
  };
  return manager as typeof manager & AuthUserManager;
}
