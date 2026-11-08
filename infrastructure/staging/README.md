# staging

Staging and closed-beta environment on AWS (ADR-010, `mbia-specs/delivery/phase-7-staging.md`). This folder grows with Phase 7: the backend image (PR-64), the container stack of the VM (PR-65), the deployment and the runbook (PR-68).

## Backend container image

Built from the repository root, because the API is generated from `mbia-specs/technical/api/openapi.yaml`:

```bash
docker build -f backend/Dockerfile -t mbia-backend .
docker buildx build -f backend/Dockerfile --platform linux/arm64 -t mbia-backend .   # for the t4g VM, from another CPU
```

The image runs the jar as a non-root user (`mbia`, uid 10001) on port 8080, without a Spring profile: logs are structured JSON (ECS format). Tests are not run by the image build; CI runs `./mvnw verify`.

### Configuration

The backend is configured by environment variables only. Without a required one, the container stops at startup and its log names the missing property.

| Variable | Required | Value on staging |
|---|---|---|
| `SPRING_DATASOURCE_URL` | yes | `jdbc:postgresql://<rds endpoint>:5432/mbia` |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | yes | the `mbia` database user |
| `MBIA_SECURITY_ISSUER_URI` | yes | `https://auth.mbia-staging.lehnade.com/realms/mbia` |
| `MBIA_CORS_ALLOWED_ORIGINS` | yes | `https://mbia-staging.lehnade.com` (comma-separated if several) |
| `MBIA_STORAGE_BUCKET`, `MBIA_STORAGE_REGION` | yes | the media bucket, `eu-west-3` |
| `MBIA_STORAGE_PATH_STYLE_ACCESS` | no (`true`) | `false` on AWS S3 |
| `MBIA_STORAGE_ENDPOINT`, `MBIA_STORAGE_PUBLICENDPOINT` | no | unset on AWS; the endpoints of another S3-compatible provider |
| `MBIA_STORAGE_ACCESSKEY`, `MBIA_STORAGE_SECRETKEY` | no | unset on AWS: the VM's instance role is used (default credentials chain) |
| `MBIA_APP_BASE_URL` | yes | `https://mbia-staging.lehnade.com` (invitation links) |
| `MBIA_MAIL_FROM` | yes | `Mbia <no-reply@mbia-staging.lehnade.com>`, an address of the identity verified in SES |
| `MBIA_SMTP_HOST`, `MBIA_SMTP_PORT` | yes | `email-smtp.eu-west-3.amazonaws.com`, `587` |
| `MBIA_SMTP_USERNAME`, `MBIA_SMTP_PASSWORD` | yes | the SES SMTP credentials |
| `MBIA_SMTP_AUTH`, `MBIA_SMTP_STARTTLS` | no (`true`) | `true` |
| `MBIA_MEMORY_MAX_PHOTOS` | no (`3`) | between 1 and 10 |
| `MBIA_PROBLEMS_BASE_URI` | no | `https://mbia-staging.lehnade.com/problems` (base of the `type` of error responses) |
| `JDK_JAVA_OPTIONS` | no | extra JVM options; the heap already takes 75 % of the container memory |

Secret values live in SSM Parameter Store, never in this repository (ADR-010).

### Trying the image locally

Against the `docker compose` services, on the host network so that `localhost` is the same for the browser and the backend (the token issuer must match `MBIA_SECURITY_ISSUER_URI`):

```bash
docker run --rm --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mbia \
  -e SPRING_DATASOURCE_USERNAME=mbia -e SPRING_DATASOURCE_PASSWORD=mbia-local \
  -e MBIA_SECURITY_ISSUER_URI=http://localhost:8081/realms/mbia \
  -e MBIA_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  -e MBIA_STORAGE_BUCKET=mbia-media -e MBIA_STORAGE_REGION=us-east-1 \
  -e MBIA_STORAGE_ENDPOINT=http://localhost:9000 \
  -e MBIA_STORAGE_ACCESSKEY=mbia-local -e MBIA_STORAGE_SECRETKEY=mbia-local-secret \
  -e MBIA_APP_BASE_URL=http://localhost:5173 -e "MBIA_MAIL_FROM=Mbia <no-reply@mbia.localhost>" \
  -e MBIA_SMTP_HOST=localhost -e MBIA_SMTP_PORT=1025 -e MBIA_SMTP_AUTH=false -e MBIA_SMTP_STARTTLS=false \
  mbia-backend
```

On Docker Desktop (macOS, Windows), the host network is the Docker VM's: the API is not published on the computer's `localhost:8080`, and the container's host name `docker-desktop` must be made resolvable (`--add-host docker-desktop:127.0.0.1`), otherwise each email waits for a failed host name lookup before being sent. On Linux, the command works as is.

## Stack of the VM

[`compose.yml`](compose.yml) runs the three containers of the VM; PostgreSQL (RDS), S3, SES and CloudWatch are AWS services (ADR-010).

| Service | Image | Published | Memory |
|---|---|---|---|
| `caddy` | `caddy:2.11.9-alpine` | 80, 443 (the only open ports, §3.1) | 128 MB |
| `keycloak` | `infrastructure/keycloak/Dockerfile` | `127.0.0.1:8081` on the VM only (admin console, SSM port forward) | 1 GB |
| `backend` | `backend/Dockerfile` | nothing | 1.5 GB |

- Restart policy `unless-stopped`; logs to CloudWatch Logs with the `awslogs` driver (group `/mbia/staging`, one stream per service, the group is created by Terraform).
- Caddy starts without waiting for the others: it checks the backend itself (`/actuator/health`, on the internal network only), and Keycloak stays reachable when the backend does not start.
- Health checks: the backend and Keycloak images have no curl nor wget; a plain HTTP request is sent with bash (`/dev/tcp`), to `/actuator/health` (backend) and to Keycloak's management port 9000 (`/health/ready`, never published nor proxied).

### What Caddy exposes ([`Caddyfile`](Caddyfile))

| Host | Forwarded | Refused (404) |
|---|---|---|
| `api.mbia-staging.lehnade.com` | `/api/v1/*` → backend | everything else, `/actuator` included |
| `auth.mbia-staging.lehnade.com` | everything → Keycloak | `/admin*` (admin console and admin REST API) |

Certificates: Let's Encrypt, automatic, kept in the `caddy-data` volume. Headers: HSTS, `nosniff`, `Referrer-Policy`, no `Server`; on the API, `X-Frame-Options: DENY` and a CSP that allows nothing. Keycloak keeps its own CSP and frame headers.

### Token issuer

The backend validates tokens against the public issuer `https://auth.mbia-staging.lehnade.com/realms/mbia`, as the browser sees it. Caddy carries that host name as a network alias in the Compose network: the backend fetches the discovery document and the keys through Caddy without leaving the VM, and the `iss` claim matches.

### Interpolated variables

| Variable | Default | |
|---|---|---|
| `MBIA_BACKEND_IMAGE`, `MBIA_KEYCLOAK_IMAGE` | required | image references (ECR, tagged with the commit) |
| `MBIA_API_HOST`, `MBIA_AUTH_HOST` | `api.mbia-staging.lehnade.com`, `auth.mbia-staging.lehnade.com` | Caddy sites; `MBIA_AUTH_HOST` is also the network alias |
| `MBIA_ENV_DIR` | `/etc/mbia/staging` | folder of `backend.env` and `keycloak.env` |
| `MBIA_AWS_REGION`, `MBIA_LOG_GROUP` | `eu-west-3`, `/mbia/staging` | CloudWatch Logs |
| `KEYCLOAK_ADMIN_PORT` | `8081` | admin console port on the VM's loopback |

`backend.env` and `keycloak.env` are rendered from SSM Parameter Store at deployment (PR-68), owned by root, mode `0600`, outside the repository. Their content: [`backend.env.example`](backend.env.example) (variables in [Configuration](#configuration)) and [`keycloak.env.example`](keycloak.env.example).

## Keycloak image and staging realm

```bash
docker build -f infrastructure/keycloak/Dockerfile -t mbia-keycloak .
```

Keycloak 26.7.4 (the version of `docker-compose.yml`), built for PostgreSQL with health endpoints (`kc.sh build`), started with `start --optimized --import-realm`, HTTP behind Caddy (`KC_PROXY_HEADERS=xforwarded`). The image contains [`realm-mbia-staging.json`](../keycloak/realm-mbia-staging.json).

| Variable (`keycloak.env`) | Value on staging |
|---|---|
| `KC_HOSTNAME` | `https://auth.mbia-staging.lehnade.com` |
| `KC_HOSTNAME_ADMIN` | `http://localhost:8081`, the end of the SSM port forward |
| `KC_DB_URL`, `KC_DB_USERNAME`, `KC_DB_PASSWORD` | the `keycloak` database on RDS and its user |
| `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` | temporary admin of the `master` realm, used on the first start only |
| `MBIA_WEB_ORIGIN` | `https://mbia-staging.lehnade.com` (redirect URIs, web origin, post-logout redirect of `mbia-web`) |
| `MBIA_KC_SMTP_HOST`, `MBIA_KC_SMTP_PORT` | `email-smtp.eu-west-3.amazonaws.com`, `587` |
| `MBIA_KC_SMTP_FROM` | `no-reply@mbia-staging.lehnade.com` (shown as "Mbia") |
| `MBIA_KC_SMTP_USERNAME`, `MBIA_KC_SMTP_PASSWORD` | the SES SMTP credentials |
| `MBIA_KC_SMTP_AUTH`, `MBIA_KC_SMTP_STARTTLS` | default `true` |

The `MBIA_*` variables are read **when the realm is imported, on the first start only**: an existing realm is never overwritten. Afterwards, change them in the admin console (Realm settings › Email, Clients › `mbia-web`).

Differences from the local realm (`realm-mbia.json`):

- no user, no URL nor address of a developer machine; `mbia-web` URLs and SMTP come from the environment;
- SMTP with authentication and STARTTLS (SES);
- brute-force protection with explicit values: 5 failures, then a wait growing from 1 to 15 minutes, no permanent lockout;
- unchanged: open sign-up (OQ-070), email as username, email verification, forgot password, FR/EN, PKCE, `mbia-api` audience, passwords of at least 8 characters (beta only, OQ-073).

`python3 infrastructure/staging/check-staging-realm.py` fails if the realm contains a user, a local address or a secret value (CI job `staging-config`).

## Trying the stack locally

The staging stack runs on the developer machine against the PostgreSQL, RustFS and Mailpit of the root `docker-compose.yml`, with the hosts `api.localhost` and `auth.localhost` on port 80 (plain HTTP). [`compose.local.yml`](compose.local.yml) keeps logs in Docker; [`local/`](local) holds the settings, local values only.

```bash
docker compose up -d --wait postgres rustfs mailpit && docker compose up rustfs-init
docker compose exec -T postgres psql -U postgres -c 'CREATE DATABASE mbia_staging OWNER mbia' -c 'CREATE DATABASE keycloak_staging OWNER keycloak'
docker build -f infrastructure/keycloak/Dockerfile -t mbia-keycloak .
docker build -f backend/Dockerfile -t mbia-backend .
docker compose --env-file infrastructure/staging/local/compose.env -f infrastructure/staging/compose.yml -f infrastructure/staging/compose.local.yml up -d --wait
cd frontend && VITE_API_BASE_URL=http://api.localhost/api/v1 VITE_OIDC_AUTHORITY=http://auth.localhost/realms/mbia npm run dev
```

Separate databases: the local Keycloak already holds a realm `mbia`, which would keep the staging realm from being imported. Then open http://localhost:5173, sign up, verify the email from Mailpit (http://localhost:8025), sign in. The admin console is at http://localhost:8082/admin/ (`admin` / `admin-local`), `http://auth.localhost/admin/` answers 404. Stop with `docker compose -p mbia-staging down -v` (the `-v` also removes the Caddy volumes).

- Browsers resolve `*.localhost` to the computer; containers reach the host through `host.docker.internal`.
- Port 80 must be free: inside the Compose network, Caddy also answers `auth.localhost` on port 80, so the issuer is the same for the browser and the backend.
- If `5432` is already taken on the computer, start PostgreSQL with `POSTGRES_PORT=5433` and point `MBIA_ENV_DIR` to a copy of `local/` using that port.
- To start again from an empty realm: `docker compose exec -T postgres psql -U postgres -c 'DROP DATABASE keycloak_staging WITH (FORCE)'` then create it again.
