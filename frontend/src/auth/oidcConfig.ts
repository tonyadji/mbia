import { UserManager, WebStorageStateStore } from 'oidc-client-ts';

const DEFAULT_AUTHORITY = 'http://localhost:8081/realms/mbia';
const DEFAULT_CLIENT_ID = 'mbia-web';

export const CALLBACK_PATH = '/auth/callback';

/**
 * The OIDC client for Keycloak (ADR-005): Authorization Code + PKCE against the public client
 * `mbia-web`, renewed with the refresh token before the access token expires.
 */
export function createUserManager(origin: string = window.location.origin): UserManager {
  return new UserManager({
    authority: import.meta.env.VITE_OIDC_AUTHORITY ?? DEFAULT_AUTHORITY,
    client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? DEFAULT_CLIENT_ID,
    redirect_uri: `${origin}${CALLBACK_PATH}`,
    post_logout_redirect_uri: `${origin}/`,
    response_type: 'code',
    scope: 'openid profile email',
    automaticSilentRenew: true,
    // The email verification link may open in another tab: the pending sign-in state must be readable there.
    stateStore: new WebStorageStateStore({ store: window.localStorage }),
  });
}
