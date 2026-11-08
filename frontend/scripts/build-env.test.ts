// @vitest-environment node
import {
  applyLocalDefaults,
  checkStagingEnv,
  LOCAL_DEFAULTS,
  STAGING_ROBOTS_TXT,
  stagingRobotsTxt,
} from './build-env.js';

const STAGING = {
  VITE_API_BASE_URL: 'https://api.example.test/api/v1',
  VITE_OIDC_AUTHORITY: 'https://auth.example.test/realms/mbia',
  VITE_OIDC_CLIENT_ID: 'mbia-web',
  VITE_SUPPORT_EMAIL: 'support@example.test',
};

describe('checkStagingEnv', () => {
  it('accepts complete https values', () => {
    expect(checkStagingEnv(STAGING)).toEqual([]);
  });

  it.each(Object.keys(STAGING))('refuses a build without %s', (name) => {
    expect(checkStagingEnv({ ...STAGING, [name]: undefined })).toEqual([`${name} is missing`]);
  });

  it('refuses empty or blank values', () => {
    expect(checkStagingEnv({ ...STAGING, VITE_OIDC_CLIENT_ID: '  ' })).toEqual([
      'VITE_OIDC_CLIENT_ID is missing',
    ]);
  });

  it('refuses the addresses of a developer machine', () => {
    const problems = checkStagingEnv({
      ...STAGING,
      VITE_API_BASE_URL: 'https://localhost:8080/api/v1',
      VITE_OIDC_AUTHORITY: 'https://127.0.0.1/realms/mbia',
      VITE_SUPPORT_EMAIL: 'support@mbia.localhost',
    });
    expect(problems).toEqual([
      'VITE_API_BASE_URL points to a developer machine (https://localhost:8080/api/v1)',
      'VITE_OIDC_AUTHORITY points to a developer machine (https://127.0.0.1/realms/mbia)',
      'VITE_SUPPORT_EMAIL points to a developer machine (support@mbia.localhost)',
    ]);
  });

  it('refuses plain HTTP and invalid URLs for the API and the issuer', () => {
    expect(
      checkStagingEnv({
        ...STAGING,
        VITE_API_BASE_URL: 'http://api.example.test/api/v1',
        VITE_OIDC_AUTHORITY: 'auth.example.test/realms/mbia',
      }),
    ).toEqual([
      'VITE_API_BASE_URL must be an https:// URL (http://api.example.test/api/v1)',
      'VITE_OIDC_AUTHORITY must be an https:// URL (auth.example.test/realms/mbia)',
    ]);
  });

  it('refuses a support contact that is not an email address', () => {
    expect(checkStagingEnv({ ...STAGING, VITE_SUPPORT_EMAIL: 'support' })).toEqual([
      'VITE_SUPPORT_EMAIL must be an email address (support)',
    ]);
  });
});

describe('applyLocalDefaults', () => {
  it('sets the local defaults of unset variables only', () => {
    const target: Record<string, string | undefined> = {};
    applyLocalDefaults({ VITE_API_BASE_URL: 'http://api.localhost/api/v1' }, target);
    expect(target).toEqual({
      VITE_OIDC_AUTHORITY: LOCAL_DEFAULTS.VITE_OIDC_AUTHORITY,
      VITE_OIDC_CLIENT_ID: LOCAL_DEFAULTS.VITE_OIDC_CLIENT_ID,
    });
  });

  it('gives no default support contact', () => {
    const target: Record<string, string | undefined> = {};
    applyLocalDefaults({}, target);
    expect(target).not.toHaveProperty('VITE_SUPPORT_EMAIL');
  });
});

describe('stagingRobotsTxt', () => {
  function emittedBy(mode: string) {
    const emitFile = vi.fn();
    const plugin = stagingRobotsTxt(mode);
    plugin.generateBundle.call({ emitFile });
    return emitFile.mock.calls.map(([file]: unknown[]) => file);
  }

  it('adds a robots.txt that disallows everything to the staging build', () => {
    expect(emittedBy('staging')).toEqual([
      { type: 'asset', fileName: 'robots.txt', source: STAGING_ROBOTS_TXT },
    ]);
    expect(STAGING_ROBOTS_TXT).toBe('User-agent: *\nDisallow: /\n');
  });

  it.each(['production', 'development'])('adds no robots.txt to the %s build', (mode) => {
    expect(emittedBy(mode)).toEqual([]);
  });
});
