# Phase 1 — Walking Skeleton

**Status:** Ready  
**Spec baseline:** tag `spec-v1.0`  
**Branch base:** `develop`

This is a delivery plan. It does not define product behavior; the specifications do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

Build the thinnest possible end-to-end slice of Mbia on the final architecture, with every guardrail in place, so that all later features copy good patterns.

At the end of Phase 1, a person can:

```text
open Mbia (FR or EN)
→ create an account (Keycloak) and verify their email
→ sign in
→ create a Family
→ see the Family home (empty state)
→ change language / display name
→ sign out
```

and another User cannot see that Family.

Nothing else. Persons, tree, memories and invitations belong to Phase 2+.

## 2. How to run this phase

### 2.1 One PR = one agent session = one small step

- PRs are done **strictly in order**. Do not start PR *n+1* before PR *n* is merged into `develop`.
- One branch per PR: `feature/pr-XX-short-name` from an up-to-date `develop`.
- Target size: **≤ ~400 lines of hand-written code** per PR (lockfiles, generated code and JSON realm export excluded). If a PR grows beyond that, stop and split it.
- The agent implements **only** the scope of the PR. Everything under "Out of scope" is forbidden, even if it looks easy.
- The agent follows `AGENTS.md`. Spec gaps go to `mbia-specs/open-questions.md`; the agent stops rather than guesses.
- A PR is mergeable only when: CI is green (from PR-05 on), every acceptance criterion is met, and the human check has been done.

### 2.2 Prompt template for the agent

```text
Implement PR-XX from mbia-specs/delivery/phase-1-walking-skeleton.md.

1. Read AGENTS.md, the PR-XX section, and every spec section it references.
2. Before coding, write a short plan: files to create/change, tests to write,
   and any question or ambiguity. Wait for my approval.
3. Implement only PR-XX scope. Write the tests from the acceptance criteria.
4. Run all checks listed in AGENTS.md §4 that exist at this point.
5. Open a PR to develop titled "PR-XX: <title>" whose description lists:
   spec sections covered, acceptance criteria with how each is verified,
   deviations or open questions, and how to run the human check.
```

### 2.3 Human review checklist (every PR)

- [ ] Scope matches the PR section; nothing from "Out of scope".
- [ ] No product behavior invented; open questions recorded if any.
- [ ] Tests exist for every acceptance criterion and were not weakened.
- [ ] No secret, token or password committed (except documented local-only test users).
- [ ] Naming, packages and layering follow `AGENTS.md` §5.
- [ ] `AGENTS.md` / `README.md` updated if commands or setup changed.
- [ ] Human check done and passed.

---

## 3. PR list

| PR | Title | Area |
|---|---|---|
| PR-01 | Repository bootstrap & local services | infra |
| PR-02 | Backend skeleton | backend |
| PR-03 | Frontend skeleton | frontend |
| PR-04 | Frontend i18n foundation | frontend |
| PR-05 | CI pipeline | ci |
| PR-06 | Backend architecture guardrails | backend |
| PR-07 | Error model & request tracing | backend |
| PR-08 | OpenAPI code generation & contract checks | backend + frontend + ci |
| PR-09 | Keycloak local realm | infra |
| PR-10 | Backend authentication & current user | backend |
| PR-11 | Sign up / sign in / sign out & welcome screen | frontend |
| PR-12 | Account settings | frontend |
| PR-13 | Create & list Families (backend) | backend |
| PR-14 | Family access guard, get & rename (backend) | backend |
| PR-15 | Family creation & home screens | frontend |
| PR-16 | End-to-end test harness & first journey | e2e + ci |

---

## PR-01 — Repository bootstrap & local services

**Goal:** a developer clones the repo and starts all local dependencies with one command.

**Scope**

- Create empty top-level folders `backend/`, `frontend/`, `infrastructure/` (with a short `README.md` each stating their purpose).
- `docker-compose.yml` with:
  - PostgreSQL 18 (port 5432) with an init script creating two databases: `mbia` and `keycloak`, each with its own user;
  - RustFS (ports 9000/9001, ADR-009) plus a one-shot init container creating the private bucket `mbia-media`;
  - Mailpit (SMTP 1025, UI 8025);
  - named volumes, health checks on every service.
- `.env.example` with every variable used by compose (no real secrets).
- `.editorconfig` (UTF-8, LF, 2 spaces; 4 spaces for Java).
- Root `README.md`: what Mbia is (2 lines), prerequisites (Docker, JDK 25, Node LTS), `docker compose up -d`, service URLs, link to `AGENTS.md` and `mbia-specs/`.

**Out of scope:** Keycloak (PR-09), any backend or frontend code, CI.

**Specs:** `technical/technical-specification.md` §3, §18; `technical/stack.md`.

**Acceptance criteria**

- `docker compose up -d` on a clean machine starts all services healthy.
- Both databases exist and are reachable with the credentials from `.env.example`.
- Bucket `mbia-media` exists and is not publicly readable.
- `docker compose down -v` removes everything cleanly.

**Human check:** run compose, open RustFS console and Mailpit UI, connect to both databases.

---

## PR-02 — Backend skeleton

**Goal:** an empty Spring Boot application that starts, connects to PostgreSQL and is tested with Testcontainers.

**Scope**

- **First, verify** that the baseline versions in `technical/stack.md` exist (Java 25, Spring Boot 4.1.x, PostgreSQL 18 image). If one does not, stop and record an open question.
- Maven project in `backend/` with Maven Wrapper; group `com.lehnade`, artifact `mbia`, root package `com.lehnade.mbia`.
- Dependencies: Spring Web MVC, Validation, Data JPA, Flyway (+ PostgreSQL module), PostgreSQL driver, Actuator, Testcontainers (PostgreSQL, JUnit 5), AssertJ, Mockito.
- `application.yml` with profiles `local` (compose services from `.env`) and `test`.
- `spring.jpa.hibernate.ddl-auto=validate`; `open-in-view=false`.
- Flyway enabled with the empty folder `db/migration/` (no migration yet).
- Actuator: only `health` exposed, at `/actuator/health`.
- One test: application context starts against a PostgreSQL Testcontainer (`@ServiceConnection`).

**Out of scope:** security, business modules, API code generation, error handling, logging configuration.

**Specs:** `technical/stack.md`; `technical/architecture.md` §1–3; `technical/data-model.md` §26.

**Acceptance criteria**

- `./mvnw verify` passes with Docker running.
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts; `GET /actuator/health` returns `UP` including the database.
- Java 25 toolchain is enforced by the build.

**Human check:** run the app, call the health endpoint.

---

## PR-03 — Frontend skeleton

**Goal:** an empty, mobile-first React app with the chosen tooling.

**Scope**

- Vite + React 19 + TypeScript in `frontend/`, `strict: true` and `noUncheckedIndexedAccess: true`.
- Tailwind CSS 4 with **design tokens** as CSS variables derived from `product/ux/design-guidelines.md` and the mockup: one primary (deep green), one warm accent, neutral warm background, text colors; one readable sans-serif font, self-hosted (for example via `@fontsource`); type scale limited to display / section / body / caption.
- React Router with a root layout and a single placeholder route.
- TanStack Query provider.
- ESLint (flat config, TypeScript + React hooks rules) and Prettier.
- Vitest + Testing Library with one smoke test.
- Scripts: `dev`, `build`, `preview`, `typecheck`, `lint`, `test`.
- The placeholder page shows only the brand name "Mbia" (no other user-facing text until PR-04).

**Out of scope:** i18n, API client, authentication, real screens, component library.

**Specs:** `product/ux/design-guidelines.md`; `product/ux/family-tree-ux.md` §2–3; `technical/stack.md`.

**Acceptance criteria**

- `npm ci && npm run typecheck && npm run lint && npm test && npm run build` pass.
- The page renders correctly at 375 px and 1280 px width.
- Tokens are defined in one place and used through Tailwind classes, not hard-coded colors.

**Human check:** `npm run dev`, look at the page on a phone-sized viewport.

---

## PR-04 — Frontend i18n foundation

**Goal:** no user-facing string can exist outside translation files, in French and English.

**Scope**

- `i18next` + `react-i18next`; resources in `frontend/src/i18n/{fr,en}/<namespace>.json`, starting with namespaces `common` and `errors`.
- Initial language: stored preference (added in PR-12) → browser language (`fr*` → `fr`, otherwise `en`). Keep the `<html lang>` attribute in sync.
- `npm run i18n:check`: fails when a key exists in one language and not the other, or when a value is empty.
- ESLint rule forbidding literal strings in JSX (for example `react/jsx-no-literals` or `eslint-plugin-i18next`), with the brand name "Mbia" allowed.
- A small `formatDate` helper using `Intl.DateTimeFormat` for the active language, with tests.
- Move the placeholder page text to translations.

**Out of scope:** language switch UI (PR-12), kinship labels (Phase 2), `PartialDate` formatting (Phase 2).

**Specs:** `product/ux/localization-and-kinship-labels.md` §1; ADR-006.

**Acceptance criteria**

- Switching browser language between French and English changes the placeholder page text.
- `npm run i18n:check` fails on a deliberately missing key (proved by a test of the script).
- Lint fails on a literal string in JSX.

**Human check:** open the app with browser language FR, then EN.

---

## PR-05 — CI pipeline

**Goal:** every PR to `develop` or `main` is checked automatically.

**Scope**

- `.github/workflows/ci.yml`, triggered on pull requests and pushes to `develop` and `main`:
  - **backend** job: Temurin JDK 25, Maven cache, `./mvnw -B verify` (Testcontainers runs on the Ubuntu runner);
  - **frontend** job: Node LTS, npm cache, `npm ci`, `typecheck`, `lint`, `i18n:check`, `test`, `build`.
- Cancel in-progress runs of the same branch.
- Add the CI badge to the root `README.md`.
- Document in `README.md` the recommended branch protection for `develop` and `main` (required checks, PR required). The human applies it in GitHub settings.

**Out of scope:** OpenAPI checks (PR-08), E2E (PR-16), deployment.

**Specs:** `technical/technical-specification.md` §20.

**Acceptance criteria**

- The PR for PR-05 itself shows both jobs green.
- A deliberately failing test makes the corresponding job red (verified on a throwaway commit, then reverted).

**Human check:** look at the Actions run; enable branch protection on GitHub.

---

## PR-06 — Backend architecture guardrails

**Goal:** architecture rules are enforced by tests, not by good intentions.

**Scope**

- Create module packages with `package-info.java` only: `identity`, `family`, `genealogy`, `memory`, `invitation`, `activity`, `shared`.
- ArchUnit tests (or Spring Modulith verification, if it can express all rules) that fail when:
  1. a class in `..domain..` depends on `org.springframework..`, `jakarta.persistence..`, Jackson, `software.amazon..` or any `..api..` / `..infrastructure..` package;
  2. `..application..` depends on `..api..` or `..infrastructure..`;
  3. `..api..` depends on `..infrastructure..`;
  4. a module depends on another module's `domain`, `api` or `infrastructure` packages (allowed: the other module's `application` package, and `shared`);
  5. a top-level package named `controller`, `service`, `repository` or `entity` exists under `com.lehnade.mbia`;
  6. a JPA `@Entity` lives outside `..infrastructure..`.
- Rules must pass on the current (almost empty) code base; use "allow empty should" where needed.
- Each rule is proved by a test fixture that violates it (in test sources), so rules cannot silently become no-ops.

**Out of scope:** any business code.

**Specs:** `technical/architecture.md` §3–8; ADR-001.

**Acceptance criteria**

- `./mvnw verify` runs the architecture tests.
- Each of the six rules has a failing fixture proving it detects violations.

**Human check:** read the rule list in the test class; it must match `architecture.md`.

---

## PR-07 — Error model & request tracing

**Goal:** every error leaves the API in one stable shape, and every request can be traced in logs.

**Scope**

- In `shared`: an `ErrorCode` catalogue and a base domain exception carrying a code, HTTP status and optional structured `details`.
- Global exception handling producing `application/problem+json` exactly as the `ProblemDetails` schema: `type`, `title`, `status`, `code`, `detail`, `traceId`, optional `fieldErrors`, optional `details`.
- Bean Validation errors → 400 `VALIDATION_FAILED` with `fieldErrors`.
- Unknown exceptions → 500 `INTERNAL_ERROR` with a generic message; the real error is only logged.
- Request tracing: a filter reads `X-Request-Id` or generates one, stores it in the logging context, returns it in the response header, and uses it as `traceId`.
- Structured JSON logs (Spring Boot structured logging) in non-local profiles; readable logs locally.
- **Contract update first:** add `VALIDATION_FAILED`, `INTERNAL_ERROR`, `AUTHENTICATION_REQUIRED` and `FAMILY_NOT_FOUND` to the `ProblemDetails.code` examples in `openapi.yaml` (non-breaking).
- A test-only controller (in test sources) to exercise each error path.

**Out of scope:** business error codes' usage, security errors (PR-10), frontend error display.

**Specs:** `technical/architecture.md` §13; `technical/technical-specification.md` §12, §19; `openapi.yaml` `ProblemDetails`.

**Acceptance criteria**

- Tests prove the exact JSON shape for: domain exception, validation error, unknown exception.
- No stack trace, SQL or exception class name ever appears in a response body.
- `traceId` in the body equals the `X-Request-Id` response header and appears in the log line.

**Human check:** call a test endpoint or a non-existing path; read the response and the log line.

---

## PR-08 — OpenAPI code generation & contract checks

**Goal:** backend and frontend cannot drift from `openapi.yaml`.

**Scope**

- Backend: OpenAPI Generator Maven plugin reading `../mbia-specs/technical/api/openapi.yaml`, Spring generator, **interfaces only** (+ models), output in `target/generated-sources/openapi/`, package `com.lehnade.mbia.api.generated`. Verify compatibility with Spring Boot 4 / Jackson 3; if generated code does not compile, stop and record an open question.
- The API is served under `/api/v1`; Actuator stays at `/actuator`. Choose the mechanism and document it in `AGENTS.md`.
- Frontend: `openapi-typescript` generating types into `src/api/generated/` (git-ignored), and `openapi-fetch` for the typed client.
  - `npm run generate:api`, run automatically before `dev`, `build`, `typecheck` and `test`.
  - `src/api/client.ts`: base URL from `VITE_API_BASE_URL`; a hook point for the access token (filled in PR-11); parsing of problem responses into a typed `ApiError { status, code, traceId, fieldErrors, details }`.
  - Error `code` → translated message lookup in the `errors` namespace, with a generic fallback.
- CI:
  - Redocly lint of `openapi.yaml` with a committed `redocly.yaml` (disable only: `info-license`, `no-server-example.com`, `tag-description`);
  - breaking-change detection against the base branch with `oasdiff`; the job fails on breaking changes unless the PR carries the label `api-breaking-approved`.

**Out of scope:** implementing any endpoint.

**Specs:** ADR-003; `technical/architecture.md` §7; `technical/technical-specification.md` §10–11.

**Acceptance criteria**

- `./mvnw verify` generates and compiles the interfaces.
- `npm run typecheck` uses the generated types.
- Redocly and oasdiff jobs run in CI; a deliberately breaking change (removing a response field) is detected on a throwaway branch.

**Human check:** open one generated interface and `src/api/client.ts`.

---

## PR-09 — Keycloak local realm

**Goal:** a local identity provider configured exactly as ADR-005, reproducible from the repository.

**Scope**

- Add Keycloak to `docker-compose.yml` (pinned image tag, dev mode with realm import, port 8081, database `keycloak` from PR-01).
- `infrastructure/keycloak/realm-mbia.json`:
  - realm `mbia`; registration allowed; email as username; login with email; no duplicate emails; **verify email required**; reset password allowed; brute-force protection on; password policy minimum length 8;
  - internationalisation enabled, supported `fr` and `en`, default `fr`;
  - SMTP → Mailpit (`mailpit:1025`), sender `no-reply@mbia.local`;
  - public client `mbia-web`: standard flow only, PKCE `S256` required, redirect URIs and web origins for `http://localhost:5173`, post-logout redirect to `http://localhost:5173/*`;
  - audience mapper adding `mbia-api` to access tokens; `email`, `email_verified`, `name`, `locale` claims available;
  - local-only test users: two verified users and one unverified user, with passwords documented in `infrastructure/keycloak/README.md` as local test data.
- Default Keycloak theme (the Mbia theme is out of scope).

**Out of scope:** backend or frontend integration, custom theme, production configuration.

**Specs:** ADR-005; `product/mvp.md` §21.

**Acceptance criteria**

- `docker compose up -d` imports the realm with no manual step.
- Registering a new user on the Keycloak account page sends a verification email visible in Mailpit, in French by default and in English when chosen.
- Forgot-password email arrives in Mailpit.
- An access token for `mbia-web` contains `aud` = `mbia-api`, `email_verified` and `locale`.

**Human check:** register through Keycloak, verify via Mailpit, inspect a token (for example on jwt.io with local data only).

---

## PR-10 — Backend authentication & current user

**Goal:** the API trusts Keycloak tokens and knows the Mbia User behind each request.

**Scope**

- OAuth2 Resource Server: issuer from configuration, audience `mbia-api` required.
- Security rules: `/api/v1/**` authenticated; `/actuator/health` public; stateless; CSRF disabled (bearer tokens only); CORS allowed origins from configuration (local: `http://localhost:5173`).
- 401 responses use the problem format with `AUTHENTICATION_REQUIRED`.
- `identity` module:
  - migration `V001__users.sql` exactly as `data-model.md` §5;
  - just-in-time provisioning in `identity.application`: first authenticated call creates the User from `sub`, `email`, `name`, `locale` (`fr`/`en`, otherwise `fr`); later calls update `email` when it changed; `display_name` is never overwritten after creation;
  - concurrent first calls for the same `sub` must not fail (unique constraint + retry/read);
  - tokens with `email_verified` ≠ `true` → 403 `EMAIL_NOT_VERIFIED`;
  - a `CurrentUser` accessor in `identity.application` for other modules.
- Implement `getCurrentUser` and `updateCurrentUser` from the generated interfaces.

**Out of scope:** families, analytics events (`user_registered` comes with the analytics PR in a later phase), account deletion.

**Specs:** ADR-005; `data-model.md` §5; `openapi.yaml` `/me`; `mvp.md` §21.

**Acceptance criteria (tests)**

- No token → 401 problem; token with wrong audience or issuer → 401.
- First `GET /me` creates exactly one row; a second call creates none.
- Two concurrent first calls → one row, both succeed.
- Email change in token → email updated; display name kept.
- Unverified email → 403 `EMAIL_NOT_VERIFIED`.
- `PATCH /me` updates `displayName` and `preferredLocale`; invalid locale → 400.
- Most tests use mocked JWTs; **one** integration test uses a real Keycloak Testcontainer with a test-only realm file (a test client with direct access grants exists only there).

**Human check:** get a token from local Keycloak and call `GET /me` with curl.

---

## PR-11 — Sign up / sign in / sign out & welcome screen

**Goal:** a visitor can create an account and sign in from the Mbia UI.

**Scope**

- `oidc-client-ts` with Authorization Code + PKCE against `mbia-web`; redirect callback route; silent renewal with refresh tokens; configuration from `VITE_OIDC_*` variables.
- `ui_locales` passed to Keycloak with the current language.
- Sign up: redirect to the Keycloak registration page (verify whether `prompt=create` is supported by the pinned Keycloak version; otherwise use the registration endpoint).
- Access token attached to every API call through the client hook from PR-08; on 401, restart sign-in.
- Protected-route wrapper.
- After sign-in: call `GET /me`; apply `preferredLocale` to the UI; on 403 `EMAIL_NOT_VERIFIED`, show a "check your inbox" screen with a sign-out action.
- **SCREEN-001 Welcome** (public): logo, short value proposition, a neutral visual placeholder, `Create my family` (→ sign-up, or → Family creation if already signed in) and `Sign in`.
- Temporary signed-in landing page showing "Hello {displayName}" and `Sign out` (replaced in PR-15).

**Out of scope:** Family screens, account settings, custom Keycloak theme.

**Specs:** `product/ux/screens.md` SCREEN-001; `product/mvp.md` §21; ADR-005, ADR-006.

**Acceptance criteria**

- Full manual flow works: welcome → sign up → verify email in Mailpit → sign in → landing page → sign out → welcome.
- Keycloak pages appear in the UI's current language.
- Unit tests cover: protected route redirect, 401 handling, `EMAIL_NOT_VERIFIED` screen.
- Welcome screen correct at 375 px.

**Human check:** do the full flow on a phone-sized viewport, in FR then EN.

---

## PR-12 — Account settings

**Goal:** a signed-in User can manage their language and display name.

**Scope** — **SCREEN-011** without legal links:

- display name edit (`PATCH /me`), with validation and success feedback;
- email shown read-only;
- language switch Français / English: updates the UI immediately and persists through `PATCH /me`; the stored preference wins over the browser language on next sign-in;
- `Change password` → Keycloak account page;
- `Sign out`;
- `Delete my account` → explanation text and a support contact link built from `VITE_SUPPORT_EMAIL`.
- A way to reach settings from the temporary landing page (for example an avatar button).

**Out of scope:** terms and privacy pages (later phase), self-service deletion.

**Specs:** `product/ux/screens.md` SCREEN-011; `product/mvp.md` §30; `localization-and-kinship-labels.md` §1.

**Acceptance criteria**

- Language change persists across sign-out/sign-in and across browsers.
- Display name validation errors come from translated messages.
- Component tests cover language switch and display name save.

**Human check:** change language, sign out, sign in on another browser.

---

## PR-13 — Create & list Families (backend)

**Goal:** a User can create a Family and becomes its ADMIN.

**Scope**

- Migration `V002__families_and_memberships.sql`: `families` and `family_memberships` as `data-model.md` §6–7, with enum types/checks and indexes.
- `family` module:
  - domain: `Family`, `FamilyMembership`, `MembershipRole`, `MembershipStatus`, `FamilyId`; family name trimmed, 1–200 characters, not blank;
  - `CreateFamilyUseCase`: Family + ADMIN membership for the creator **in one transaction**;
  - `ListMyFamiliesUseCase`: only Families where the caller's membership is ACTIVE;
  - persistence adapters with separate JPA entities and mappers.
- Implement `createFamily` (201 + `ETag`) and `listMyFamilies`.
- `FamilyStats`: `activeMemberCount` real; `personCount` and `memoryCount` come from a `FamilyStatsPort` whose Phase 1 implementation returns 0, documented as temporary.

**Out of scope:** get/update a single Family (PR-14), audit and activity entries (Phase 2), analytics.

**Specs:** `product/mvp.md` §14; `data-model.md` §6–7; `openapi.yaml` `/families`; `architecture.md` §6–9.

**Acceptance criteria (tests)**

- Domain tests for name rules.
- Use-case test: creation produces one Family and one ACTIVE ADMIN membership; failure of the second insert rolls back both.
- API tests: 201 with `myRole = ADMIN` and `ETag: "0"`; blank name → 400 `VALIDATION_FAILED`.
- List: returns own Families only; a Family where the caller is `REMOVED` is excluded; another User's Family is never listed.

**Human check:** create two Families with curl as user A, list as A and as B.

---

## PR-14 — Family access guard, get & rename (backend)

**Goal:** establish the pattern every family-scoped feature will reuse: membership check, role check, optimistic concurrency.

**Scope**

- `FamilyAccess` in `family.application`, usable by other modules:
  - `requireActiveMember(familyId)` → returns the caller's role;
  - `requireRole(familyId, roles…)`.
- Access rules:
  - unknown Family, or caller not an ACTIVE member → **404 `FAMILY_NOT_FOUND`** (never reveal that a Family exists);
  - ACTIVE member without the required role → 403 `PERMISSION_DENIED`.
- Shared optimistic-concurrency support: `ETag` = `"<version>"`; `If-Match` parsing; stale version → 409 `CONCURRENT_MODIFICATION`; missing header → 400 `VALIDATION_FAILED`.
- Implement `getFamily` (any ACTIVE member) and `updateFamily` (ADMIN only, rename, `If-Match` required).
- Update `technical/technical-specification.md` §9 with the 404-not-403 rule for non-members.

**Out of scope:** members endpoints, invitations.

**Specs:** `product/mvp.md` §22; `technical/architecture.md` §11–12; `data-model.md` §2.3, §2.5; `openapi.yaml` `/families/{familyId}`.

**Acceptance criteria (tests)**

- User B → 404 on GET and PATCH of user A's Family.
- A CONTRIBUTOR or VIEWER (membership inserted directly in the test) → 200 on GET, 403 on PATCH.
- PATCH with a stale `If-Match` → 409; with the current one → 200 and version + 1.
- A reusable test helper ("given a family with members of each role") exists for later modules.

**Human check:** read `FamilyAccess` and one test; this is the template for all later modules.

---

## PR-15 — Family creation & home screens

**Goal:** after sign-in, a User reaches their Family or creates one.

**Scope**

- Post-sign-in routing: `GET /families` → 0 Families: Family creation; 1: its home; more than 1: a simple Family chooser.
- Family creation screen: name field, translated validation, submit, success feedback, redirect to home.
- **SCREEN-002 Family Home** at `/families/{familyId}`:
  - header with Family name, Person count and Memory count;
  - empty state text "Welcome to the {familyName} family / Let's add the first person" **without** its action buttons (they arrive with the Person feature);
  - bottom navigation bar with only `Home` for now (other tabs arrive with their features);
  - settings access (from PR-12).
- 404 `FAMILY_NOT_FOUND` → a friendly "family not found" page with a way back.
- Replace the temporary landing page from PR-11.
- `Create my family` on the welcome screen leads here for signed-in Users.

**Out of scope:** search bar, tree card, recent activity, add Person / Memory actions, rename UI.

**Specs:** `product/ux/screens.md` SCREEN-002; `product/mvp.md` §14, §20; `family-tree-ux.md` §4.

**Acceptance criteria**

- Component tests for the three routing cases and the empty state.
- Screens correct at 375 px and desktop width, in FR and EN.

**Human check:** new account → create Family → home; open another User's Family URL → not found page.

---

## PR-16 — End-to-end test harness & first journey

**Goal:** the Phase 1 journey is proved automatically, in CI.

**Scope**

- Playwright in `frontend/` (`npm run test:e2e`) running against docker compose + backend + built frontend.
- Helper reading the Mailpit API to fetch the verification link.
- Tests:
  1. register a new User (FR) → verify email → sign in → create Family → see home with the Family name;
  2. same journey in EN (language chosen on the welcome screen or browser locale);
  3. User B opening User A's Family URL sees "family not found".
- `mvp-release-criteria.spec.ts`: the full `mvp.md` §28 journey as `test.fixme` steps, the north star for later phases.
- CI job `e2e` on pull requests to `develop` and `main`, uploading the Playwright report on failure.

**Out of scope:** visual regression, performance tests.

**Specs:** `product/mvp.md` §28; `technical/technical-specification.md` §17; `technical/architecture.md` §14.

**Acceptance criteria**

- The three E2E tests pass locally and in CI.
- The release-criteria spec is present and skipped, listing every step of §28.

**Human check:** run `npm run test:e2e` locally once with the Playwright UI.

---

## 4. Phase 1 exit criteria

- [ ] PR-01 to PR-16 merged into `develop`, CI green.
- [ ] The journey in §1 works manually on a phone-sized viewport, in FR and EN.
- [ ] Cross-family access returns "not found" (API test + E2E).
- [ ] Architecture tests, OpenAPI lint/breaking checks, i18n check and E2E run in CI.
- [ ] `AGENTS.md` commands are all real and correct.
- [ ] `mbia-specs/open-questions.md` has no open blocking question.

## 5. Not in Phase 1 (planned later)

Persons and relationships, kinship engine, tree, memories and media, invitations and members, activity and audit, search and duplicates, merge/archive, analytics, Mbia Keycloak theme, landing/legal pages, deployment and production environments.
