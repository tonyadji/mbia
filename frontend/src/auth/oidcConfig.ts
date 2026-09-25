import { UserManager, WebStorageStateStore } from 'oidc-client-ts';

const DEFAULT_AUTHORITY = 'http://localhost:8081/realms/mbia';
const DEFAULT_CLIENT_ID = 'mbia-web';

export const CALLBACK_PATH = '/auth/callback';

const authority = () => import.meta.env.VITE_OIDC_AUTHORITY ?? DEFAULT_AUTHORITY;
const clientId = () => import.meta.env.VITE_OIDC_CLIENT_ID ?? DEFAULT_CLIENT_ID;

/**
 * The OIDC client for Keycloak (ADR-005): Authorization Code + PKCE against the public client
 * `mbia-web`, renewed with the refresh token before the access token expires.
 */
export function createUserManager(origin: string = window.location.origin): UserManager {
  return new UserManager({
    authority: authority(),
    client_id: clientId(),
    redirect_uri: `${origin}${CALLBACK_PATH}`,
    post_logout_redirect_uri: `${origin}/`,
    response_type: 'code',
    scope: 'openid profile email',
    automaticSilentRenew: true,
    // The email verification link may open in another tab: the pending sign-in state must be readable there.
    stateStore: new WebStorageStateStore({ store: window.localStorage }),
  });
}

/**
 * The Keycloak account page, where the User changes their password (SCREEN-011), with a way back to
 * Mbia. Its language is chosen by Keycloak (OQ-004).
 */
export function accountPageUrl(origin: string = window.location.origin): string {
  const url = new URL(`${authority()}/account`);
  url.searchParams.set('referrer', clientId());
  url.searchParams.set('referrer_uri', `${origin}/settings`);
  return url.toString();
}
