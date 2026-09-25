interface ImportMetaEnv {
  /** Base URL of the Mbia API, including `/api/v1`. */
  readonly VITE_API_BASE_URL?: string;
  /** OIDC issuer: the Keycloak realm URL. */
  readonly VITE_OIDC_AUTHORITY?: string;
  /** OIDC public client of the web application. */
  readonly VITE_OIDC_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
