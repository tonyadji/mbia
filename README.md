# Mbia

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

`docker compose ps -a` shows the long-running services as `healthy` and the one-shot `rustfs-init` (bucket creation) as `Exited (0)`. Avoid `up --wait`: it reports a failure as soon as that one-shot container exits.

RustFS replaces MinIO locally (ADR-009). Keycloak is added in PR-09.

To stop everything and delete all local data:

```bash
docker compose down -v
```

## Working on Mbia

- [`AGENTS.md`](AGENTS.md): rules and commands for developers and coding agents.
- [`mbia-specs/`](mbia-specs/): product and technical specifications, ADRs, OpenAPI contract, delivery plans.
