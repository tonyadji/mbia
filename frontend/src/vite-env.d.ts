interface ImportMetaEnv {
  /** Base URL of the Mbia API, including `/api/v1`. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
