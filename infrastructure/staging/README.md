# staging

Staging and closed-beta environment on AWS (ADR-010, `mbia-specs/delivery/phase-7-staging.md`). This folder holds the backend image (PR-64), the container stack of the VM (PR-65), the frontend build (PR-66), the deployment and the [runbook](#runbook) (PR-68). The AWS resources are in [`../terraform/staging/`](../terraform/staging/README.md) (PR-67).

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

`backend.env` and `keycloak.env` are rendered from SSM Parameter Store at deployment ([Deployment](#deployment)), owned by root, mode `0600`, outside the repository. Their content: [`backend.env.example`](backend.env.example) (variables in [Configuration](#configuration)) and [`keycloak.env.example`](keycloak.env.example).

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

## Frontend build

The SPA is built for staging by the deployment workflow (PR-68), from `frontend/`:

```bash
npm ci
VITE_API_BASE_URL=... VITE_OIDC_AUTHORITY=... VITE_OIDC_CLIENT_ID=... VITE_SUPPORT_EMAIL=... npm run build:staging
npm run check:staging-build
```

| Variable | Value on staging |
|---|---|
| `VITE_API_BASE_URL` | `https://api.mbia-staging.lehnade.com/api/v1` |
| `VITE_OIDC_AUTHORITY` | `https://auth.mbia-staging.lehnade.com/realms/mbia` |
| `VITE_OIDC_CLIENT_ID` | `mbia-web` |
| `VITE_SUPPORT_EMAIL` | `<support address>`, the contact for account deletion requests (`mvp.md` §30) |

The values are read at build time and end up in the published JavaScript: none is secret. They come from the workflow, never from a file of the repository.

- `npm run build:staging` builds in the Vite mode `staging`. It stops before building when a variable is missing or empty, when the API or the issuer is not an `https://` URL, or when a value points to a developer machine (`localhost`, `127.0.0.1`, `[::1]`). The local defaults (`frontend/scripts/build-env.js`) apply to every other mode only: `npm run dev`, the tests and the local `npm run build` of the E2E tests.
- The staging build alone contains `robots.txt` (`User-agent: *`, `Disallow: /`, OQ-070); a production build will have none.
- `npm run check:staging-build` fails when `dist/` contains a local address or no such `robots.txt`. Two exact literals are tolerated: `http://localhost` (react-router) and `http://127.0.0.1` (oidc-client-ts), bases against which these libraries parse relative URLs; no request goes to them.
- Output: `index.html`, `robots.txt` and `assets/`, whose file names carry a content hash.

## Frontend on CloudFront

What the CloudFront distribution of the frontend does, defined in [`../terraform/staging/cdn.tf`](../terraform/staging/cdn.tf) (PR-67). The build itself cannot set headers nor routing.

1. **Origin:** the private frontend bucket through Origin Access Control; `default_root_object = index.html`.
2. **Deep links** (`/invitations/<token>`, `/families/<id>/tree`, …): a CloudFront Function on viewer request rewrites to `/index.html` every URI whose last segment has no extension (no `.`). No custom error response turning 403/404 into `index.html`: a missing asset must stay an error. The application's paths hold only UUIDs, years and base64url invitation tokens, never a dot.
3. **Behavior `/assets/*`:** cache policy `CachingOptimized`; response header `Cache-Control: public, max-age=31536000, immutable` (override).
4. **Default behavior** (`index.html`, `robots.txt`, deep links): cache policy `CachingDisabled`; response header `Cache-Control: no-cache` (override), so that browsers revalidate `index.html` on every visit and a deployment is seen at once.
5. **Headers on every response** (the response headers policies of both behaviors):
   - `X-Robots-Tag: noindex, nofollow` (OQ-070);
   - `Strict-Transport-Security: max-age=31536000; includeSubDomains`;
   - `X-Content-Type-Options: nosniff`;
   - `X-Frame-Options: DENY`;
   - `Referrer-Policy: strict-origin-when-cross-origin`: an invitation token in the address never leaves the frontend origin;
   - `Content-Security-Policy`, on one line:

     ```text
     default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self';
     img-src 'self' https://<media bucket>.s3.eu-west-3.amazonaws.com;
     connect-src 'self' https://api.mbia-staging.lehnade.com https://auth.mbia-staging.lehnade.com https://<media bucket>.s3.eu-west-3.amazonaws.com;
     frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'
     ```

6. **After an upload** (PR-68): invalidate `/index.html` and `/robots.txt` at least (assets have new names).

Why each source of the CSP:

- **Scripts, styles, fonts:** the build has no inline script nor stylesheet; React sets inline styles through the DOM, which the CSP allows; the font is bundled.
- **Media bucket:** photos are uploaded with a pre-signed `PUT` sent by `XMLHttpRequest` (`connect-src`) and shown with pre-signed `GET` URLs in `<img>` (`img-src`). On AWS S3 the URLs are virtual-hosted (`path-style-access` `false`): `https://<media bucket>.s3.eu-west-3.amazonaws.com`. Terraform builds it from the bucket (`terraform output media_bucket_origin`).
- **Keycloak:** oidc-client-ts fetches the discovery document and the keys, exchanges the code and renews the access token with the refresh token, all with `fetch` (`connect-src`); Keycloak gives `mbia-web` a refresh token and allows its origin (`webOrigins`). Sign-in, sign-out and the account page are full-page navigations, outside the CSP. No silent renewal in an iframe: it is used only without a refresh token, and the callback page does not handle it; the application then signs the User in again. Hence `frame-src 'none'`.
- **PostHog EU:** absent, the frontend sends no analytics today (ADR-008). Add `https://eu.i.posthog.com` to `connect-src` when a change does.

## Deployment

[`.github/workflows/deploy-staging.yml`](../../.github/workflows/deploy-staging.yml) deploys one commit of `main`, on demand only: never on a merge. It authenticates to AWS by OIDC with the role `mbia-staging-deploy` (no stored key) and runs, in this order:

| Step | What it does | If it fails |
|---|---|---|
| 1. Commit | the given commit, or the head of `main`; refused if it is not a commit of `main` or the run is not on `main` | nothing changed |
| 2. Images | backend and Keycloak, `linux/arm64` (buildx, QEMU), tagged with the full commit SHA, pushed to ECR; skipped when the tag already exists (tags are immutable) | nothing changed |
| 3. VM | SSM Run Command: [`deploy/vm-deploy.sh`](deploy/vm-deploy.sh) renders the env files, pulls, `up -d --wait` | before the start, nothing changed; a release that does not become healthy is replaced by the current one again, and the run fails |
| 4. Smoke checks | [`deploy/smoke-checks.sh backend`](deploy/smoke-checks.sh): `api.…/api/v1/me` answers the backend's `401 AUTHENTICATION_REQUIRED` through Caddy, `/actuator` and `/admin` answer 404, the OIDC discovery document of `auth.…` has the expected `issuer` | the previous release is brought back (`vm-deploy.sh revert`), the run fails, the frontend is not touched |
| 5. Frontend | `npm run build:staging` and `check:staging-build`; upload of `assets/` (`Cache-Control: public, max-age=31536000, immutable`), then `index.html` and `robots.txt` (`no-cache`), then removal of the older assets; CloudFront invalidation of `/`, `/index.html`, `/robots.txt`, awaited | the backend stays deployed; deploy again or roll back |
| 6. Smoke checks | `smoke-checks.sh frontend`: `/` and a deep link serve the `index.html` just built, `robots.txt` disallows everything | the run fails; roll back if needed |

The run's summary gives the deployed commit, the previous release and the rollback command.

**"The previous containers keep running":** one Compose stack runs on the VM, so `up -d` replaces the containers. A failure before the start (settings, ECR, pull) changes nothing; a failed start or a failed smoke check starts the previous release again, within about a minute (an outage of a few minutes is accepted, ADR-010).

### On the VM

| Path | Content |
|---|---|
| `/opt/mbia/staging/releases/<commit>/` | `compose.yml` and `Caddyfile` of the commit, `compose.env` (image references, not secret) |
| `/opt/mbia/staging/current`, `previous` | links to the running release and to the one before |
| `/opt/mbia/staging/bin/vm-deploy.sh` | the deployment script, copied by each run from `main` |
| `/opt/mbia/staging/releases.log` | one line per deployment or revert |
| `/etc/mbia/staging/backend.env`, `keycloak.env` | rendered at each deployment from `/mbia/staging/backend/*` and `/mbia/staging/keycloak/*`, root, `0600` |

The env files hold every parameter of the path, the name after the last `/` being the variable (`ssm.tf` creates the names). A deployment stops before touching anything when a value is still `change-me`, or holds a line break or a single quote. Values are written between single quotes: Compose reads them literally, so a `$` in a password is safe. No value is ever printed: the output of the SSM command is shown in the GitHub log.

### GitHub repository variables

Repository variables, not secrets (Settings › Secrets and variables › Actions › Variables), and no GitHub environment: an environment would change the OIDC subject, which the role's trust policy pins to `repo:tonyadji/mbia:ref:refs/heads/main`.

| Variable | Value |
|---|---|
| `AWS_ACCOUNT_ID` | the account id (the workflow refuses credentials of another account) |
| `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw deploy_role_arn` |
| `STAGING_VM_INSTANCE_ID` | `terraform output -raw vm_instance_id` |
| `STAGING_CLOUDFRONT_DISTRIBUTION_ID` | `terraform output -raw cloudfront_distribution_id` |
| `STAGING_SUPPORT_EMAIL` | the support address shown in the frontend (`VITE_SUPPORT_EMAIL`, `mvp.md` §30) |

The hosts and the frontend bucket are fixed in the workflow (`locals.tf`).

## Runbook

Everything here is done by the human, from a computer with the AWS CLI v2, the [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html), the GitHub CLI and `psql` (`brew install libpq`), with an administrator profile. The commands start from the repository root:

```bash
export AWS_PROFILE=<profile> AWS_REGION=eu-west-3
tf() { terraform -chdir=infrastructure/terraform/staging "$@"; }   # a function: zsh does not split "$TF"
VM=$(tf output -raw vm_instance_id)
RDS=$(tf output -raw rds_endpoint)
```

### Shell on the VM

No SSH: a Session Manager shell, as `ssm-user` (use `sudo -i` for root).

```bash
aws ssm start-session --target "$VM"
sudo /opt/mbia/staging/bin/vm-deploy.sh status     # on the VM: current and previous releases, containers
```

### Reaching the databases

RDS accepts only the VM: a port forward through it, to `localhost:15432`, in a terminal kept open.

```bash
aws ssm start-session --target "$VM" --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "host=$RDS,portNumber=5432,localPortNumber=15432"
```

```bash
PGPASSWORD=$(aws ssm get-parameter --name /mbia-admin/staging/rds-master-password --with-decryption --query Parameter.Value --output text) \
  psql "host=localhost port=15432 user=mbia_admin dbname=postgres sslmode=require"
```

### First installation

Once, after the first `terraform apply` ([`../terraform/staging/README.md`](../terraform/staging/README.md)), in this order.

**1. The Docker Compose plugin on the VM.** Amazon Linux 2023 ships Docker without it. Pinned version and checksum (from the release's `checksums.txt`); in a shell on the VM:

```bash
sudo -i
COMPOSE_VERSION=v5.6.1
COMPOSE_SHA256=42cfa75f51ad0ccf290bad37899e0038a8c10a0069e85233ecc1d53e3ff1347d
curl -fsSL -o /tmp/docker-compose "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/docker-compose-linux-aarch64"
echo "$COMPOSE_SHA256  /tmp/docker-compose" | sha256sum -c -
install -D -m 0755 /tmp/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose && rm /tmp/docker-compose
docker compose version
```

To upgrade it later: the same commands with the new version and its checksum.

**2. SSM parameters.** A helper, then the values that are not secret (`ssm.tf` created every name with `change-me` and the right type):

```bash
put() { aws ssm put-parameter --overwrite --name "/mbia/staging/$1" --value "$2" >/dev/null; }
MEDIA=$(tf output -raw media_bucket)
put backend/SPRING_DATASOURCE_URL "jdbc:postgresql://$RDS:5432/mbia?sslmode=require"
put backend/SPRING_DATASOURCE_USERNAME mbia
put backend/MBIA_SECURITY_ISSUER_URI https://auth.mbia-staging.lehnade.com/realms/mbia
put backend/MBIA_CORS_ALLOWED_ORIGINS https://mbia-staging.lehnade.com
put backend/MBIA_STORAGE_BUCKET "$MEDIA"
put backend/MBIA_STORAGE_REGION eu-west-3
put backend/MBIA_STORAGE_PATH_STYLE_ACCESS false
put backend/MBIA_APP_BASE_URL https://mbia-staging.lehnade.com
put backend/MBIA_MAIL_FROM "Mbia <no-reply@mbia-staging.lehnade.com>"
put backend/MBIA_SMTP_HOST email-smtp.eu-west-3.amazonaws.com
put backend/MBIA_SMTP_PORT 587
put backend/MBIA_PROBLEMS_BASE_URI https://mbia-staging.lehnade.com/problems
put keycloak/KC_HOSTNAME https://auth.mbia-staging.lehnade.com
put keycloak/KC_HOSTNAME_ADMIN http://localhost:8081
put keycloak/KC_DB_URL "jdbc:postgresql://$RDS:5432/keycloak?sslmode=require"
put keycloak/KC_DB_USERNAME keycloak
put keycloak/MBIA_WEB_ORIGIN https://mbia-staging.lehnade.com
put keycloak/MBIA_KC_SMTP_HOST email-smtp.eu-west-3.amazonaws.com
put keycloak/MBIA_KC_SMTP_PORT 587
put keycloak/MBIA_KC_SMTP_FROM no-reply@mbia-staging.lehnade.com
```

Generated secrets, never shown (characters safe in a JDBC setting and an env file):

```bash
gen() { openssl rand -base64 32 | tr -d '/+='; }
put backend/SPRING_DATASOURCE_PASSWORD "$(gen)"
put keycloak/KC_DB_PASSWORD "$(gen)"
put keycloak/KC_BOOTSTRAP_ADMIN_USERNAME "bootstrap-$(openssl rand -hex 4)"
put keycloak/KC_BOOTSTRAP_ADMIN_PASSWORD "$(gen)"
```

**3. SES SMTP credentials.** An access key of the user `mbia-staging-ses-smtp` (allowed `ses:SendRawEmail` only), turned into an SMTP password by [`support/ses-smtp-password.py`](support/ses-smtp-password.py) (algorithm published by AWS); the secret key is never printed:

```bash
read -r KEY_ID SECRET < <(aws iam create-access-key --user-name "$(tf output -raw ses_smtp_user)" --query 'AccessKey.[AccessKeyId,SecretAccessKey]' --output text)
SMTP_PASSWORD=$(printf %s "$SECRET" | python3 -I infrastructure/staging/support/ses-smtp-password.py eu-west-3)
put backend/MBIA_SMTP_USERNAME "$KEY_ID";  put backend/MBIA_SMTP_PASSWORD "$SMTP_PASSWORD"
put keycloak/MBIA_KC_SMTP_USERNAME "$KEY_ID"; put keycloak/MBIA_KC_SMTP_PASSWORD "$SMTP_PASSWORD"
unset SECRET SMTP_PASSWORD
```

To rotate: create a second key, set the four parameters, deploy, change the SMTP password of the realm in the admin console (Realm settings › Email: the realm keeps the value of its first import), then `aws iam delete-access-key` on the old one.

**4. Databases and users on RDS**, as `infrastructure/postgres/init` does locally. With the port forward open ([Reaching the databases](#reaching-the-databases)):

```bash
PGPASSWORD=$(aws ssm get-parameter --name /mbia-admin/staging/rds-master-password --with-decryption --query Parameter.Value --output text) \
psql "host=localhost port=15432 user=mbia_admin dbname=postgres sslmode=require" -v ON_ERROR_STOP=1 \
  -v mbia_password="$(aws ssm get-parameter --name /mbia/staging/backend/SPRING_DATASOURCE_PASSWORD --with-decryption --query Parameter.Value --output text)" \
  -v keycloak_password="$(aws ssm get-parameter --name /mbia/staging/keycloak/KC_DB_PASSWORD --with-decryption --query Parameter.Value --output text)" <<'SQL'
-- The RDS master user is not a superuser: it must be able to SET ROLE to the owners it creates (PostgreSQL 16+).
SET createrole_self_grant TO 'set, inherit';
CREATE ROLE mbia LOGIN PASSWORD :'mbia_password';
CREATE DATABASE mbia OWNER mbia;
REVOKE ALL ON DATABASE mbia FROM PUBLIC;
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
```

The schema of `mbia` is created by Flyway at the backend's first start; Keycloak creates its own.

**5. GitHub repository variables** ([table above](#github-repository-variables)):

```bash
gh variable set AWS_ACCOUNT_ID --body "$(aws sts get-caller-identity --query Account --output text)"
gh variable set AWS_DEPLOY_ROLE_ARN --body "$(tf output -raw deploy_role_arn)"
gh variable set STAGING_VM_INSTANCE_ID --body "$VM"
gh variable set STAGING_CLOUDFRONT_DISTRIBUTION_ID --body "$(tf output -raw cloudfront_distribution_id)"
gh variable set STAGING_SUPPORT_EMAIL --body "<support address>"
```

**6. First deployment**, then the Keycloak administrator:

- [Deploy](#deploy). Keycloak imports the realm on its first start, with the `MBIA_*` values of `keycloak.env`.
- Open the [admin console](#keycloak-admin-console) with the bootstrap administrator (`KC_BOOTSTRAP_ADMIN_*`), create a named administrator in the `master` realm (role `admin`, a strong password, OTP required), sign in with it, delete the bootstrap user.
- Realm `mbia` › Realm settings › Email › Test connection: the email arrives (SES).

### Deploy

```bash
gh workflow run deploy-staging.yml --ref main                    # the head of main
gh workflow run deploy-staging.yml --ref main -f commit=<sha>    # a given commit of main
gh run watch "$(gh run list --workflow deploy-staging.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
```

Settings changed in SSM take effect at the next deployment (the env files are rendered then), except the `MBIA_*` settings of Keycloak, read at the first import only.

### Rollback

One command, to the previous release given by the summary of the last run (or by `vm-deploy.sh status`):

```bash
gh workflow run deploy-staging.yml --ref main -f commit=<previous commit>
```

The images of that commit are still in ECR (the last 10 are kept; an older one is rebuilt), its frontend is rebuilt and uploaded, the same checks apply.

Flyway migrations are not undone: an older backend starts on a newer schema (Flyway ignores migrations it does not know), and works as long as the migrations in between only added things. When one did not, restore the database to a time before the deployment ([below](#restoring-rds-to-a-point-in-time)) instead.

### Logs

CloudWatch Logs, group `/mbia/staging`, one stream per service, 30 days:

```bash
aws logs tail /mbia/staging --log-stream-names backend --follow
aws logs tail /mbia/staging --log-stream-names keycloak caddy --since 1h
aws logs tail /mbia/staging --log-stream-names backend --since 1d --filter-pattern '"ERROR"'
```

The output of each deployment is in the GitHub run, and in Systems Manager › Run Command › Command history.

### Keycloak admin console

Not exposed on the Internet (`/admin` answers 404): a port forward to the VM's `127.0.0.1:8081`, in a terminal kept open, then http://localhost:8081/admin/.

```bash
aws ssm start-session --target "$VM" --document-name AWS-StartPortForwardingSession \
  --parameters "portNumber=8081,localPortNumber=8081"
```

### Restoring RDS to a point in time

Automated backups allow a restore to any second of the last 7 days. The restore creates a **new** instance; the running one is not touched. Test (Phase 7 exit criteria), on a temporary instance:

```bash
RDS_SG=$(aws rds describe-db-instances --db-instance-identifier mbia-staging --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' --output text)
aws rds restore-db-instance-to-point-in-time --source-db-instance-identifier mbia-staging \
  --target-db-instance-identifier mbia-staging-restore-test --restore-time <UTC time, 2026-11-20T09:30:00Z> \
  --db-instance-class db.t4g.micro --no-multi-az --no-publicly-accessible \
  --db-subnet-group-name mbia-staging --vpc-security-group-ids "$RDS_SG" --no-deletion-protection
aws rds wait db-instance-available --db-instance-identifier mbia-staging-restore-test
RESTORED=$(aws rds describe-db-instances --db-instance-identifier mbia-staging-restore-test --query 'DBInstances[0].Endpoint.Address' --output text)
```

Check it through the VM (port forward with `host=$RESTORED`, same users and passwords as the source), for example the counts against what the application showed at that time:

```sql
\c mbia
SELECT count(*) AS families FROM families;
SELECT count(*) AS persons FROM persons;
SELECT count(*) AS memories FROM memories;
SELECT max(occurred_at) AS last_change FROM audit_entries;
```

Then delete it (it costs as much as the real one):

```bash
aws rds delete-db-instance --db-instance-identifier mbia-staging-restore-test --skip-final-snapshot --delete-automated-backups
```

For a real incident: restore the same way under another name, check it, take a manual snapshot of the damaged instance, point `backend/SPRING_DATASOURCE_URL` and `keycloak/KC_DB_URL` to the restored endpoint and [deploy](#deploy) the running commit; then bring the restored instance under Terraform (`terraform import`, or rename) with the human's review. The media bucket is versioned: photos uploaded after the restore time stay in it, without rows pointing to them.

### Account deletion

The support procedure of `mvp.md` §30, in the transaction of `data-model.md` §5 (OQ-075). Within 30 days of the request.

1. **The request** comes from the account's email address, to the support address (`STAGING_SUPPORT_EMAIL`). Answer from it to confirm the request and say what stays: content the User added to a Family stays there, shown as "Former member", unless the User asks for specific content to be removed.
2. **Last ADMIN:** if the User is the only ADMIN of a Family, ask them to name another ADMIN first (Members screen). The script refuses otherwise, and prints the Families concerned.
3. **Specific content to remove**, if asked: an ADMIN archives the Memories or Persons in the application. The photos stay in the versioned media bucket until OQ-074 is decided: for a removal request, delete every version of their objects by hand (`aws s3api list-object-versions --bucket <media bucket> --prefix <key>`, then `delete-object --version-id` for each).
4. **The Mbia record**, with the port forward open ([Reaching the databases](#reaching-the-databases)), as the `mbia` user, with [`support/delete-account.sql`](support/delete-account.sql):

   ```bash
   PGPASSWORD=$(aws ssm get-parameter --name /mbia/staging/backend/SPRING_DATASOURCE_PASSWORD --with-decryption --query Parameter.Value --output text) \
     psql "host=localhost port=15432 user=mbia dbname=mbia sslmode=require" \
     -v email='<the account email>' -f infrastructure/staging/support/delete-account.sql
   ```

   One transaction: the memberships become `REMOVED` and the linked Persons are released, the pending invitations sent by the User or to the User's email are revoked, the User row is anonymised; each step is audited with the User as actor, no activity entry. It prints the counts and the Keycloak user ID.
5. **The Keycloak account:** [admin console](#keycloak-admin-console), realm `mbia` › Users › search the printed ID › Delete.
6. **Answer** the User that the deletion is done. Automated RDS backups still hold the old row for up to 7 days, then it is gone; manual snapshots taken before the deletion (below) keep it until they are deleted.

Test it on staging with a test account (sign up, create a Family, add a second ADMIN, delete) before the first real family (Phase 7 exit criteria).

### What to keep for production (OQ-071)

The beta data moves to production at launch (OQ-071, answer A): beta families keep everything. Until the production ADR's migration has been done:

- **RDS `mbia-staging`:** never destroyed (`deletion_protection`, `prevent_destroy`), never rebuilt. Take a manual snapshot before the migration and before any risky change; manual snapshots are kept until deleted, automated backups only 7 days:

  ```bash
  aws rds create-db-snapshot --db-instance-identifier mbia-staging --db-snapshot-identifier mbia-staging-$(date -u +%Y%m%d-%H%M)
  ```

- **Both databases together:** `mbia` and `keycloak` are migrated from the same point in time. `users.identity_provider_subject` holds the Keycloak user IDs; a Keycloak database from another time breaks the link between accounts and their data.
- **The Keycloak database is the realm,** not `realm-mbia-staging.json`: accounts, password hashes, settings changed in the console. The realm file only seeds an empty database. The production address (`auth.mbia.lehnade.com`) changes the token issuer: accounts are kept, everybody signs in again (ADR-010).
- **The media bucket `lehnade-mbia-staging-media`:** never emptied nor destroyed (`prevent_destroy`), versioning on. `media_assets` holds its object keys: production uses the same bucket or a copy with the same keys.
- **Not needed:** the frontend bucket and the images (rebuilt), the logs, the VM (it holds no data).

Staging for later tests is rebuilt empty after the migration (ADR-010).
