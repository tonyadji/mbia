# Mbia

[![CI](https://github.com/tonyadji/mbia/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/tonyadji/mbia/actions/workflows/ci.yml)

Mbia is a private, collaborative family-heritage web app.
Families build their tree, attach photos and stories to relatives, and invite each other to contribute.

## Prerequisites

- Docker with Docker Compose v2
- JDK 25 (backend; the build refuses any other major version)
- Node.js 22.12 or later (frontend; version in [`frontend/.nvmrc`](frontend/.nvmrc))

## Local services

```bash
docker compose up -d
```

This starts every local dependency with health checks. No `.env` file is needed: `docker-compose.yml` uses the values from [`.env.example`](.env.example) as defaults. Copy it to `.env` only to override a value, for example `POSTGRES_PORT` when port 5432 is already used on your machine.

| Service | URL / address | Credentials (local only) |
|---|---|---|
| PostgreSQL | `localhost:5432`, database `mbia` | `mbia` / `mbia-local` |
| PostgreSQL | `localhost:5432`, database `keycloak` | `keycloak` / `keycloak-local` |
| RustFS S3 API | http://localhost:9000, private bucket `mbia-media` | `mbia-local` / `mbia-local-secret` |
| RustFS console | http://localhost:9001/rustfs/console/ | same as S3 API |
| Mailpit SMTP | `localhost:1025` | none |
| Mailpit UI | http://localhost:8025 | none |
| Keycloak admin console | http://localhost:8081/admin/ | `admin` / `admin-local` |
| Keycloak realm `mbia` | http://localhost:8081/realms/mbia/account | test users in [`infrastructure/keycloak/README.md`](infrastructure/keycloak/README.md) |

`docker compose ps -a` shows the long-running services as `healthy` and the one-shot `rustfs-init` (bucket creation) as `Exited (0)`. Avoid `up --wait` on the whole file: it can report a failure as soon as that one-shot container exits. Name the long-running services instead, as CI does: `docker compose up -d --wait postgres mailpit keycloak`.

RustFS replaces MinIO locally (ADR-009). Keycloak imports the realm `mbia` on first start (ADR-005); see [`infrastructure/keycloak/README.md`](infrastructure/keycloak/README.md).

To stop everything and delete all local data:

```bash
docker compose down -v
```

## Running the application

With the local services up:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API on http://localhost:8080/api/v1
cd frontend && npm ci && npm run dev                                     # http://localhost:5173
```

Sign in with a test user of [`infrastructure/keycloak/README.md`](infrastructure/keycloak/README.md), or create an account: the verification email arrives in Mailpit.

End-to-end tests (Playwright) need the same services and the backend running:

```bash
cd frontend && npx playwright install chromium   # once
cd frontend && npm run test:e2e                  # or npm run test:e2e:ui
```

Every command (build, tests, lint, API generation) is listed in [`AGENTS.md`](AGENTS.md) §4; details in [`frontend/README.md`](frontend/README.md).

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every pull request to `develop` or `main` and on every push to those branches. A new push cancels the run still in progress on the same branch.

| Job | Steps |
|---|---|
| `backend` | Temurin JDK 25, `./mvnw -B verify` (compile, unit and Testcontainers tests) |
| `frontend` | Node from `frontend/.nvmrc`, `npm ci`, `typecheck`, `lint`, `i18n:check`, `test`, `build` |
| `api-lint` | Redocly lint of `mbia-specs/technical/api/openapi.yaml` (rules: [`redocly.yaml`](redocly.yaml)) |
| `api-breaking` (pull requests only) | `oasdiff` against the base branch's contract ([`oasdiff-levels.txt`](oasdiff-levels.txt)); fails on a breaking change unless the pull request carries the label `api-breaking-approved` |
| `e2e` (pull requests only) | PostgreSQL, Mailpit and Keycloak from `docker-compose.yml`, the backend jar, then `npm run test:e2e` (Playwright); the HTML report is uploaded when it fails |

### Recommended branch protection

Apply to `develop` and `main` in GitHub (*Settings → Rules → Rulesets*, or *Settings → Branches* for classic rules):

- require a pull request before merging;
- require status checks `backend`, `frontend`, `api-lint`, `api-breaking` and `e2e` to pass, with the branch up to date before merging;
- block force pushes and branch deletion;
- do not allow bypassing these rules, administrators included.

## Working on Mbia

- [`AGENTS.md`](AGENTS.md): rules and commands for developers and coding agents.
- [`mbia-specs/`](mbia-specs/): product and technical specifications, ADRs, OpenAPI contract, delivery plans.
