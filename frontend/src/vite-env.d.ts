interface ImportMetaEnv {
  /** Base URL of the Mbia API, including `/api/v1`. */
  readonly VITE_API_BASE_URL?: string;
  /** OIDC issuer: the Keycloak realm URL. */
  readonly VITE_OIDC_AUTHORITY?: string;
  /** OIDC public client of the web application. */
  readonly VITE_OIDC_CLIENT_ID?: string;
  /** Support contact for account deletion requests (mvp.md §30); no contact link when unset. */
  readonly VITE_SUPPORT_EMAIL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
