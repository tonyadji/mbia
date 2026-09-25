# Mbia

[![CI](https://github.com/tonyadji/mbia/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/tonyadji/mbia/actions/workflows/ci.yml)

Mbia is a private, collaborative family-heritage web app.
Families build their tree, attach photos and stories to relatives, and invite each other to contribute.

## Prerequisites

- Docker with Docker Compose v2
- JDK 25 (backend, from PR-02)
- Node.js LTS (frontend, from PR-03)

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

`docker compose ps -a` shows the long-running services as `healthy` and the one-shot `rustfs-init` (bucket creation) as `Exited (0)`. Avoid `up --wait`: it reports a failure as soon as that one-shot container exits.

RustFS replaces MinIO locally (ADR-009). Keycloak imports the realm `mbia` on first start (ADR-005); see [`infrastructure/keycloak/README.md`](infrastructure/keycloak/README.md).

To stop everything and delete all local data:

```bash
docker compose down -v
```

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every pull request to `develop` or `main` and on every push to those branches. A new push cancels the run still in progress on the same branch.

| Job | Steps |
|---|---|
| `backend` | Temurin JDK 25, `./mvnw -B verify` (compile, unit and Testcontainers tests) |
| `frontend` | Node from `frontend/.nvmrc`, `npm ci`, `typecheck`, `lint`, `i18n:check`, `test`, `build` |

### Recommended branch protection

Apply to `develop` and `main` in GitHub (*Settings → Rules → Rulesets*, or *Settings → Branches* for classic rules):

- require a pull request before merging;
- require status checks `backend` and `frontend` to pass, with the branch up to date before merging;
- block force pushes and branch deletion;
- do not allow bypassing these rules, administrators included.

## Working on Mbia

- [`AGENTS.md`](AGENTS.md): rules and commands for developers and coding agents.
- [`mbia-specs/`](mbia-specs/): product and technical specifications, ADRs, OpenAPI contract, delivery plans.
