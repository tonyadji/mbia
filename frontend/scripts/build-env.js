// Build-time settings of the frontend (Phase 7 plan PR-66, ADR-010): the local defaults and the staging build.
// Used by vite.config.ts; kept apart so that the rules can be tested.

/** The Vite mode of the staging build (`npm run build:staging`). */
export const STAGING_MODE = 'staging';

/**
 * Values used when a variable is unset, in every mode except staging: `npm run dev`, Vitest and the local
 * `npm run build` of the E2E tests. They live here, not in `src/`, so that a staging bundle cannot contain them.
 */
export const LOCAL_DEFAULTS = {
  VITE_API_BASE_URL: 'http://localhost:8080/api/v1',
  VITE_OIDC_AUTHORITY: 'http://localhost:8081/realms/mbia',
  VITE_OIDC_CLIENT_ID: 'mbia-web',
};

/** Variables the staging build requires; the workflow gives them, no `.env` file holds them. */
export const STAGING_VARIABLES = [
  'VITE_API_BASE_URL',
  'VITE_OIDC_AUTHORITY',
  'VITE_OIDC_CLIENT_ID',
  'VITE_SUPPORT_EMAIL',
];

const HTTPS_VARIABLES = ['VITE_API_BASE_URL', 'VITE_OIDC_AUTHORITY'];

/** Hosts of a developer machine, never allowed in a deployed build. */
export const LOCAL_HOST = /localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0/i;

/** Returns the problems of the staging variables in `env`; an empty list means they can be built. */
export function checkStagingEnv(env) {
  const problems = [];
  for (const name of STAGING_VARIABLES) {
    const value = env[name]?.trim();
    if (!value) {
      problems.push(`${name} is missing`);
      continue;
    }
    if (LOCAL_HOST.test(value)) {
      problems.push(`${name} points to a developer machine (${value})`);
    }
  }
  for (const name of HTTPS_VARIABLES) {
    const value = env[name]?.trim();
    if (value && !isHttpsUrl(value)) problems.push(`${name} must be an https:// URL (${value})`);
  }
  const support = env.VITE_SUPPORT_EMAIL?.trim();
  if (support && !/^[^\s@]+@[^\s@]+$/.test(support)) {
    problems.push(`VITE_SUPPORT_EMAIL must be an email address (${support})`);
  }
  return problems;
}

function isHttpsUrl(value) {
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

/**
 * Sets the local default of every variable that `env` (the `.env` files and the environment, as loaded by Vite)
 * leaves unset. Only unset variables are written to `target`, so a value from `.env.local` is never replaced.
 */
export function applyLocalDefaults(env, target = process.env) {
  for (const [name, value] of Object.entries(LOCAL_DEFAULTS)) {
    if (env[name] === undefined) target[name] = value;
  }
}

/** The robots.txt of the staging frontend: nothing is indexed (OQ-070). */
export const STAGING_ROBOTS_TXT = 'User-agent: *\nDisallow: /\n';

/**
 * A Vite plugin that adds `robots.txt` to the staging build only; any other build, production included, has none.
 * `public/` is not used for it, because everything there is copied into every build.
 */
export function stagingRobotsTxt(mode) {
  return {
    name: 'mbia-staging-robots-txt',
    apply: 'build',
    generateBundle() {
      if (mode !== STAGING_MODE) return;
      this.emitFile({ type: 'asset', fileName: 'robots.txt', source: STAGING_ROBOTS_TXT });
    },
  };
}
