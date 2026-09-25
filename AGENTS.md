# AGENTS.md — Mbia

Instructions for coding agents (Claude Code, Codex, Nambé, …) and human developers working on this repository.

Mbia is a private, collaborative family-heritage web app: families build their tree, attach photos and stories to relatives, and invite each other to contribute. This repository contains the specifications (`mbia-specs/`) and the implementation (`backend/`, `frontend/`, `infrastructure/`).

## 1. Specifications come first

Read the relevant specs **before** writing code. They are the source of truth, in this order of authority:

```text
mbia-specs/product/vision.md, mvp.md          product rules
mbia-specs/product/domain/                    business rules (Person, relationships, collaboration)
mbia-specs/product/ux/                        screens, tree layout, labels, languages
mbia-specs/technical/                         stack, architecture, data model, ADRs
mbia-specs/technical/api/openapi.yaml         HTTP contract
automated tests
implementation
```

When two sources disagree, the higher one wins. Code never silently changes a product rule.

### Spec gaps: stop, do not invent

You may choose implementation details. You must **not** invent product meaning, permission rules, conflict behavior, user-facing wording for kinship, or data semantics.

When the specs do not answer a question that changes behavior:

1. do not guess;
2. add an entry to `mbia-specs/open-questions.md` (context, question, options, your recommendation);
3. tell the human and continue only with work that does not depend on the answer.

When a human answers, update the relevant spec in the same change set as the code.

## 2. Out of scope

`mbia-specs/product/mvp.md` §27 lists what must not be added (video, AI, GEDCOM, chat, public trees, …). Do not add features, settings or "nice to haves" that no spec requests.

## 3. Repository layout

```text
mbia/
├── AGENTS.md
├── mbia-specs/          specifications (product + technical + ADRs + OpenAPI)
│   └── delivery/        delivery plans: ordered PR breakdown per phase (not product rules)
├── backend/             Spring Boot modular monolith (Java 25, Maven Wrapper)
├── frontend/            React + TypeScript SPA (Vite)
├── infrastructure/      docker, keycloak realm, deployment files
├── docker-compose.yml   local PostgreSQL, RustFS (S3), Keycloak, Mailpit
└── .env.example
```

Stack and versions: `mbia-specs/technical/stack.md`. Do not change a framework or a major version without an ADR.

## 4. Commands

> Commands are added as the delivery plan progresses (backend since PR-02). A change that adds or modifies a command updates this section in the same change.

```bash
docker compose up -d                      # PostgreSQL, RustFS, Keycloak (realm imported), Mailpit
cd backend && ./mvnw verify               # compile, generate API, unit + Testcontainers + architecture tests
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API on http://localhost:8080/api/v1
cd frontend && npm ci
cd frontend && npm run generate:api       # regenerate the TypeScript types (also run before dev, build, typecheck, lint, test)
npx @redocly/cli@2.54.3 lint mbia-specs/technical/api/openapi.yaml   # lint the contract (rules: redocly.yaml)
cd frontend && npm run typecheck
cd frontend && npm run lint
cd frontend && npm run i18n:check        # French and English translations have the same keys, no empty value
cd frontend && npm test                   # Vitest
cd frontend && npx playwright install chromium   # once, the browser used by the E2E tests
cd frontend && npm run test:e2e           # Playwright on the built frontend (needs docker compose + backend running)
cd frontend && npm run test:e2e:ui        # same, in the Playwright UI
cd frontend && npm run dev                # http://localhost:5173
```

Local services: Keycloak admin http://localhost:8081, Mailpit http://localhost:8025, RustFS console http://localhost:9001/rustfs/console/. Test users are defined in `infrastructure/keycloak/realm-mbia.json`.

A change is done only when `./mvnw verify` and all frontend checks pass. CI (`.github/workflows/ci.yml`) runs the same checks on every pull request to `develop` and `main`.

## 5. Backend architecture

Details: `mbia-specs/technical/architecture.md`.

- Root package `com.lehnade.mbia`, one package per business module: `identity`, `family`, `genealogy`, `memory`, `invitation`, `activity`, `shared`.
- Inside a module: `domain/` → `application/` → `api/`, with `infrastructure/` implementing domain/application ports.
- `domain/` has no dependency on Spring, JPA, Jackson, HTTP or AWS.
- **One use case per business operation**, in `application/<operation>/` (for example `genealogy/application/createrelationship/CreateRelationshipUseCase.java`). No catch-all `PersonService`.
- HTTP DTOs (generated from OpenAPI) ≠ commands ≠ domain objects ≠ JPA entities. Map explicitly.
- Generated server interfaces and models live in `com.lehnade.mbia.api.generated`. A module's `api/` controller implements a generated interface; `shared/api/web/ApiPathPrefixConfiguration` serves every such controller under `/api/v1` (the contract's paths stay those of `openapi.yaml`). Actuator stays at `/actuator`.
- No global `controller/`, `service/`, `repository/` or `entity/` packages.
- Architecture tests (ArchUnit / Spring Modulith) enforce these rules. Never weaken or skip them to make a build pass.

### Non-negotiable rules

- **Family isolation:** every family-scoped use case checks the caller's ACTIVE membership in that family before anything else. A resource from another family returns 404, never its data.
- **Authorization lives in the backend.** Hiding a button in the UI is not a security control.
- **Optimistic concurrency:** mutations of versioned resources require `If-Match`; a stale version returns 409 `CONCURRENT_MODIFICATION`.
- **Atomicity:** operations listed in `technical-specification.md` §14 run in one transaction.
- **Soft lifecycle:** Persons, relationships, Memories and memberships are archived or removed logically, never deleted in normal flows.
- **Errors:** `application/problem+json` with a stable `code` from the OpenAPI `ProblemDetails` list. Never expose stack traces, SQL or framework messages.
- **Secrets:** never log or audit tokens, raw invitation tokens, passwords or storage keys.
- **Derived kinship is computed, never stored.**

## 6. OpenAPI contract

- Contract-first (ADR-003): change `mbia-specs/technical/api/openapi.yaml` **first**, then the code.
- Backend server interfaces and the frontend client are generated from it.
- Generated code is **never edited by hand**:
  - backend: `backend/target/generated-sources/openapi/`
  - frontend: `frontend/src/api/generated/`
- A breaking change to the contract needs explicit human approval; CI detects it.
  - `api-lint`: Redocly with `redocly.yaml` (recommended rules; only `info-license`, `no-server-example.com` and `tag-description` are off).
  - `api-breaking`: `oasdiff` against the pull request's base branch, with `oasdiff-levels.txt`; it fails on breaking changes unless the pull request carries the label `api-breaking-approved`.

## 7. Database migrations

- Flyway, in `backend/src/main/resources/db/migration/`, named `V###__description.sql` (for example `V009__add_invitation_channel.sql`).
- **Committed migrations are immutable.** Any change needs a new migration.
- Hibernate `ddl-auto` is `validate`, never `update` or `create`.
- The schema follows `mbia-specs/technical/data-model.md`; update that document when the model changes.

## 8. Frontend rules

- React + TypeScript strict, TanStack Query for server state, React Hook Form for forms, Tailwind CSS.
- Mobile-first. Test every screen at phone width (375 px).
- **Every user-facing string exists in French and English** (`frontend/src/i18n/{fr,en}/`). No hard-coded text. CI fails on missing keys.
- Kinship labels come only from `mbia-specs/product/ux/localization-and-kinship-labels.md`. Never invent a label.
- Human language only: never show "node", "edge", "PARENT_OF", HTTP codes or raw server messages. Translate error `code`s.
- Use the component set in `mbia-specs/product/ux/design-guidelines.md` §7 before creating new ones.
- The tree uses the fixed three-row layout of `family-tree-ux.md` §6.1, not a generic graph-layout library.

## 9. Tests

- Write the tests **from the spec's rules and acceptance criteria**, before or together with the implementation, never adapted afterwards to match the code.
- Levels: domain unit tests → use-case tests → PostgreSQL Testcontainers tests → API integration tests → a few Playwright E2E tests.
- Every business rule that blocks, permits or isolates something needs a test (cycle, self-relation, cross-family access, last ADMIN, single-use invitation, linked-Person protection, merge atomicity, …). See `technical-specification.md` §17 and `data-model.md` §28.
- The north-star E2E test replays `mvp.md` §28 (release criteria) end to end.
- Never delete, skip or loosen a failing test to get green. Fix the code, or report the conflict.

## 10. Decisions that require an ADR

Write a new ADR in `mbia-specs/technical/adr/` (status `Proposed`) and wait for human acceptance before:

- changing a framework, library family or major version;
- changing the architectural style or module boundaries;
- adding infrastructure (message broker, cache, search engine, worker, new external service);
- changing persistence semantics (lifecycle, isolation, versioning);
- changing the API style, authentication or identity provider.

## 11. Working method

- Follow the current delivery plan in `mbia-specs/delivery/`: one PR at a time, in order, within its scope.
- Work in **small vertical slices**: one use case or one screen per change, with its tests.
- Before coding, state which spec sections you implement.
- Keep the change focused; do not refactor unrelated code.
- Branches: `feature/<short-name>` from `develop`. Commit messages in English, imperative mood.
- A pull request describes: the spec sections covered, what was tested, and any open question raised. Its rationale must be understandable without the agent conversation.
- Never commit secrets or `.env` files.
