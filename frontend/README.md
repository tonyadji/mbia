# frontend

React + TypeScript single-page application (Vite), mobile-first, in French and English.

Rules: [`AGENTS.md`](../AGENTS.md) §8.

## Commands

Requires Node.js ≥ 22.12 (see `.nvmrc`).

```bash
npm ci
npm run dev           # http://localhost:5173
npm run typecheck
npm run lint
npm run i18n:check    # fr and en translations have the same keys, no empty value
npm test              # Vitest
npm run test:e2e      # Playwright end-to-end tests (see below)
npm run test:e2e:ui   # same, in the Playwright UI
npm run build         # production build in dist/
npm run preview       # serve dist/
npm run format        # Prettier (format:check in CI)
```

## Design tokens

Colors, font (Nunito Sans, self-hosted) and the type scale (display / section / body / caption) are defined only in
`src/styles/theme.css` and used through Tailwind classes (`bg-primary`, `text-text-muted`, `text-display`, …).
Tailwind's default palette is disabled, and a test fails if a color is written anywhere else.

## Translations

Every user-facing string lives in `src/i18n/{fr,en}/<namespace>.json` (namespaces `common`, `errors` and `auth` for now) and is
read with `useTranslation()` from `react-i18next`. Keys are typed from the French resources.

- Initial language: the browser language (`fr*` → `fr`, anything else → `en`); after sign-in, the User's stored
  `preferredLocale` (from `GET /me`) replaces it.
  `<html lang>` follows the active language.
- `npm run i18n:check` fails when a key or namespace exists in one language only, or when a value is empty.
- ESLint (`i18next/no-literal-string`) fails on literal text or user-facing attributes in JSX; only the brand name
  "Mbia" is allowed.
- Dates: `formatDate(date, language)` in `src/i18n/formatDate.ts` (`12 mars 1954` / `March 12, 1954`).

## API client

Types are generated from `../mbia-specs/technical/api/openapi.yaml` by `openapi-typescript` into `src/api/generated/`
(git-ignored, never edited). `npm run generate:api` runs automatically before `dev`, `build`, `typecheck`, `lint` and
`test`.

- `src/api/client.ts`: `apiClient`, typed with `openapi-fetch`. Base URL from `VITE_API_BASE_URL` (see `.env.example`;
  defaults to `http://localhost:8080/api/v1`). `setAccessTokenProvider()` supplies the bearer token. A failed response
  rejects with an `ApiError { status, code, traceId, fieldErrors, details }` read from the `ProblemDetails` body.
- `src/api/errorMessage.ts`: `errorMessage(i18n, error)` returns the translation `errors:<code>`, or the generic
  `errors:unexpected` when the code has no translation yet. Each feature adds the messages of the codes it returns.
- `package.json` `overrides` lets `openapi-typescript` (peer `typescript ^5`) use the project's TypeScript 6; remove it
  once `openapi-typescript` supports TypeScript 6.

## Authentication

Keycloak (ADR-005) through `oidc-client-ts`: Authorization Code + PKCE with the public client `mbia-web`
(`src/auth/oidcConfig.ts`). `VITE_OIDC_AUTHORITY` and `VITE_OIDC_CLIENT_ID` (see `.env.example`) default to the local
realm.

- `AuthProvider` / `useAuth()`: `signIn(returnTo)`, `signUp()` (`prompt=create`, the Keycloak registration page),
  `signOut()`. Keycloak pages follow the UI language (`ui_locales`). The session lives in `sessionStorage` and is
  renewed with the refresh token; the pending sign-in state is in `localStorage`, so the email verification link can
  finish the sign-up in another tab.
- Keycloak redirects back to `/auth/callback`, which continues to the in-app page asked for before sign-in (`/home`
  by default).
- `ProtectedRoute` wraps the pages for signed-in Users: it starts sign-in when there is no session, then loads
  `GET /me` (`useCurrentUser()`) and applies the User's language. `403 EMAIL_NOT_VERIFIED` shows the "check your
  inbox" page.
- The access token is added to every API call; a `401` answer drops the session and starts sign-in again.

## Account settings

`/settings` (SCREEN-011), reached from the avatar of the signed-in pages. `useUpdateCurrentUser()` calls `PATCH /me`
for the display name and the language; the language switches at once and the stored choice wins over the browser
language at the next sign-in, on any device. `Delete my account` links to the support address `VITE_SUPPORT_EMAIL`
(see `.env.example`); without it, only the explanation is shown.

## End-to-end tests

Playwright (`playwright.config.ts`, tests in `e2e/`) drives Chromium at phone width (375 px) through the real stack.
It builds the frontend and serves it on http://localhost:5173 (the only origin the Keycloak client accepts); locally,
a running `npm run dev` is reused instead. Docker compose and the backend must already run:

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npx playwright install chromium   # once
cd frontend && npm run test:e2e                  # or npm run test:e2e:ui
```

- Each test signs up a new account (`e2e-<uuid>@mbia.local`) and reads its verification email through the Mailpit API
  (`e2e/support/mailpit.ts`), so runs repeat without resetting local data. The isolation test signs in as the realm's
  test user `bob@mbia.local`.
- Expected texts come from `src/i18n/{fr,en}`: a wording change does not break the tests.
- `e2e/mvp-release-criteria.spec.ts` lists every step of `mvp.md` §28 as `test.fixme`: each phase turns its steps into
  real tests.
- Report: `npx playwright show-report` (traces and screenshots of failed tests). `MAILPIT_URL`, `E2E_API_URL` and
  `E2E_OIDC_AUTHORITY` override the local service URLs.
