# Phase 7 — Staging and closed beta

**Status:** Draft, for the human's review  
**Spec baseline:** `mbia-specs/` 0.7.3 (ADR-010 Accepted; answers to OQ-070 to OQ-072)  
**Branch base:** `develop`, after PR-63 (tag `phase-6-complete`)

This is a delivery plan. It does not define product behavior; the specs do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

Mbia runs on AWS for a closed beta, as decided in ADR-010: one VM for the backend and Keycloak, RDS, S3, CloudFront, SES, Route 53.

At the end of Phase 7, the following works without a developer machine, in FR and EN, on a phone:

```text
open https://app.staging.<domain>
→ sign up, receive the verification email (SES), verify
→ create a Family, tell a first memory with a photo
→ invite a relative by email: the invitation arrives, its link opens app.staging
→ the relative joins and sees the family story
→ a new version is deployed from GitHub Actions: the data is still there
→ the database is restored from a backup on a test instance (runbook)
```

## 2. How to run this phase

### 2.1 Rules

Phase 3 rules apply (`phase-3-family-memories.md` §2.1), with these differences:

- Terraform, Dockerfiles, Compose, Caddy and workflow files count as hand-written code (**≤ ~700 lines** per PR). PR-67 is split into two PRs if it goes beyond.
- **No product change.** No new endpoint, screen, migration or setting that a spec does not request. A code change is allowed only when the deployment needs it (for example a configuration key read from the environment), and it keeps `local` and `test` unchanged.
- **No secret in the repository**: no password, key, token or real email address. Examples use placeholders (`<domain>`, `change-me`).
- Terraform is never applied by the agent. The agent writes it and runs `terraform fmt -check` and `terraform validate`; the human runs `plan` and `apply`.

### 2.2 Before PR-64 (human)

- [x] ADR-010 Accepted (2026-10-03).
- [ ] Domain registered in Route 53 (hosted zone created).
- [ ] AWS account: MFA on the root user, an administrator user or SSO for the human, a budget alert.
- [ ] SES production access requested for eu-west-3 (it can take a few days).
- [x] OQ-070, OQ-071 and OQ-072 answered (2026-10-03).
- [ ] Beta privacy notice and terms requested from the lawyer (OQ-072), with the production texts.

### 2.3 Human review checklist (every PR)

- [ ] No secret, real email or account identifier in the diff.
- [ ] `./mvnw verify` and every frontend check still pass; the local stack (`docker compose up -d`) is unchanged.
- [ ] Every new environment variable is documented (`infrastructure/staging/README.md`).
- [ ] Security groups, IAM policies and bucket policies grant the minimum listed in §3.

## 3. Phase-wide constraints

### 3.1 Network and access

| From | To | Allowed |
|---|---|---|
| Internet | VM | 80, 443 (Caddy) |
| Internet | CloudFront | 443 |
| VM | RDS | 5432 |
| VM | S3, SES, ECR, SSM, CloudWatch | over HTTPS |
| Human | VM | SSM Session Manager and port forwarding only |

The Keycloak admin console and the Actuator are not exposed on the Internet; `/actuator/health` is answered only to Caddy (health check).

### 3.2 IAM

- VM instance role: S3 `GetObject`, `PutObject`, `DeleteObject`, `ListBucket` on the media bucket only; ECR pull; SSM parameters under `/mbia/staging/` (read); CloudWatch Logs write; SSM managed instance.
- GitHub deployment role (OIDC, `main` branch and manual runs only): ECR push, S3 write on the frontend bucket, CloudFront invalidation, SSM `SendCommand` on the VM.
- SES SMTP user: `ses:SendRawEmail` from the verified domain only.

### 3.3 Configuration

The backend is configured by environment variables only (Spring relaxed binding): no staging secret in `application.yml`. Required outside `local` and `test`: the datasource, `MBIA_SECURITY_ISSUER_URI`, the CORS allowed origin, the storage bucket and region, `MBIA_APP_BASE_URL`, `MBIA_MAIL_FROM`, `MBIA_SMTP_*`. The exact list is written in PR-64.

## 4. PR list

| PR | Title | Main value |
|---|---|---|
| PR-64 | Backend container image | the backend runs anywhere Docker runs |
| PR-65 | Keycloak and the staging stack | the VM stack (Caddy, Keycloak, backend) runs in production mode |
| PR-66 | Frontend build for staging | the SPA is built for an environment and served by a CDN |
| PR-67 | Staging infrastructure (Terraform) | the AWS resources are reviewed as code |
| PR-68 | Deployment workflow and runbook | a version is deployed, checked and rolled back without the agent |

---

## PR-64 — Backend container image

**Goal:** the backend runs as a container, configured by the environment.

**Scope**

- `backend/Dockerfile`: multi-stage build with the Maven Wrapper, runtime on a Java 25 JRE image (pinned), non-root user, Spring Boot layered jar, `-XX:MaxRAMPercentage`, `linux/arm64` and `linux/amd64`.
- `.dockerignore`.
- Environment variables for the settings that have none today (issuer URI, CORS origins, storage), documented in `infrastructure/staging/README.md`.
- `mbia.problems.base-uri` from the environment (the staging domain).

**Out of scope:** Keycloak, Caddy, AWS.

**Specs:** ADR-010; `stack.md` §Infrastructure; `technical-specification.md` §19 (logs); AGENTS.md §5 (secrets).

**Acceptance criteria**

- The image builds from a clean checkout and starts against the local `docker compose` services with environment variables only.
- Without a required setting, the container stops at startup with a readable message (as today).
- Logs are structured JSON; no secret appears in them at startup.
- The process does not run as root.

**Human check:** `docker build` then `docker run` against the local stack; create a Family from the local frontend.

---

## PR-65 — Keycloak and the staging stack

**Goal:** the stack of the VM runs in production mode.

**Scope**

- `infrastructure/keycloak/Dockerfile`: Keycloak image (same pinned version as `docker-compose.yml`) built for PostgreSQL (`kc.sh build`), started with `start --optimized`, `KC_PROXY_HEADERS=xforwarded`, `KC_HOSTNAME` from the environment.
- `infrastructure/keycloak/realm-mbia-staging.json`: the `mbia` realm without test users; `mbia-web` redirect URIs and web origins from the environment; SMTP from the environment (Keycloak placeholders); registration open (OQ-070); password policy; brute-force protection on.
- `infrastructure/staging/compose.yml`: Caddy, Keycloak, backend; `awslogs` log driver; restart policy; health checks.
- `infrastructure/staging/Caddyfile`: `api.staging.<domain>` → backend `/api/v1` only, `auth.staging.<domain>` → Keycloak without `/admin`; security headers.

**Out of scope:** AWS resources (PR-67), deployment (PR-68).

**Specs:** ADR-005, ADR-010.

**Acceptance criteria**

- The stack starts locally with the staging Compose file against local PostgreSQL, with `localhost` hosts.
- Sign up, email verification (Mailpit) and login work through Caddy.
- `/admin` and `/actuator` are not reachable through Caddy.
- The staging realm contains no user and no local URL.

**Human check:** start the staging stack locally and sign in from the frontend.

---

## PR-66 — Frontend build for staging

**Goal:** the SPA is built for an environment and served correctly by a CDN.

**Scope**

- `npm run build` with the staging `VITE_*` values, given by the workflow (no `.env.staging` with real values in the repository).
- SPA routing on CloudFront: a deep link (`/invitations/<token>`, `/families/...`) serves `index.html`.
- Cache: hashed assets cached for a long time, `index.html` never cached.
- `noindex`: a `robots.txt` that disallows everything and an `X-Robots-Tag: noindex` header on every response of the staging frontend (OQ-070).
- Security headers (CSP allowing the API, Keycloak, the media bucket and PostHog EU if enabled, `frame-ancestors 'none'`), defined in PR-67 as a CloudFront response headers policy.

**Out of scope:** new UI.

**Specs:** ADR-010; `stack.md`; ADR-008.

**Acceptance criteria**

- A build with the staging values contains no `localhost` URL.
- The staging frontend answers `robots.txt` with `Disallow: /`.
- `npm run preview` with the same build opens a deep link directly.

**Human check:** build with fake staging values and `grep` the output for `localhost`.

---

## PR-67 — Staging infrastructure (Terraform)

**Goal:** every AWS resource of ADR-010 is described as code.

**Scope** (`infrastructure/terraform/staging/`)

- State backend (S3 bucket with locking), created once by a bootstrap step documented in the runbook.
- Network: VPC with public subnets in two zones (required by RDS), no NAT gateway; security groups of §3.1.
- EC2 `t4g.medium` (Amazon Linux, Docker, SSM agent), Elastic IP, instance role of §3.2, EBS encrypted.
- RDS PostgreSQL `db.t4g.micro`, single-AZ, encrypted, backups 7 days, deletion protection.
- S3 media bucket (private, CORS, versioning, encryption), S3 frontend bucket + CloudFront (OAC, ACM in us-east-1, response headers policy).
- Route 53 records; SES domain identity with DKIM, SPF, DMARC and a custom MAIL FROM.
- ECR repositories (backend, keycloak) with a lifecycle policy.
- SSM parameter names (values set by the human, never in Terraform).
- GitHub OIDC provider and deployment role of §3.2; AWS Budgets alert; CloudWatch log groups.

**Out of scope:** applying it (human), production resources.

**Specs:** ADR-010; `infrastructure/README.md` §Object storage in production.

**Acceptance criteria**

- `terraform fmt -check` and `terraform validate` pass; they are added to CI.
- No secret value, no account identifier in the code; variables have no production default.
- The rules of §3.1 and §3.2 are the only ones granted.

**Human check:** `terraform plan` on the real account, then `apply`.

---

## PR-68 — Deployment workflow and runbook

**Goal:** a version is deployed, checked and rolled back by the human alone.

**Scope**

- `.github/workflows/deploy-staging.yml`, manual trigger on a commit of `main`: build and push the images (tagged with the commit), build and upload the frontend, invalidate CloudFront, deploy on the VM with SSM Run Command (`compose pull` + `up -d`), then smoke checks (health through Caddy, frontend `200`, OIDC discovery document).
- `infrastructure/staging/README.md` (runbook): first installation (databases and users on RDS, SSM parameters, SES SMTP credentials), deployment, rollback to a previous tag, reading logs, reaching the Keycloak admin console, restoring RDS to a point in time, the account deletion procedure of `mvp.md` §30 on staging; what must be kept (RDS snapshots, media bucket, Keycloak database) for the migration of the beta data to production (OQ-071).
- AGENTS.md §4: the new commands.

**Out of scope:** automatic deployment on every merge, production.

**Specs:** ADR-010; `mvp.md` §30.

**Acceptance criteria**

- A run deploys a given commit; a failing smoke check fails the run and leaves the previous containers running.
- A rollback to the previous tag is one documented command.
- The §1 journey passes on staging (human).

**Human check:** the §1 journey on a phone; a restore of the database on a temporary RDS instance.

---

## 5. Not in Phase 7

- production (ECS Fargate, multi-AZ, later ADR) and the migration of the beta data to it (OQ-071);
- the legal texts themselves (written by a lawyer, OQ-072) and their links in the UI;
- automatic deployment on every merge;
- monitoring beyond CloudWatch logs and the budget alert (APM, uptime service);
- any product feature, even small, requested by beta users: they go through the specs first (OQ-069 for example).

## 6. Phase 7 exit criteria

- [ ] PR-64 to PR-68 merged, CI green.
- [ ] The §1 journey passes on `app.staging.<domain>` in FR and EN, on a phone.
- [ ] Emails (Keycloak and invitations) arrive in a Gmail and an Outlook inbox, not in spam.
- [ ] A restore of the database has been done once, following the runbook.
- [ ] The support procedure of `mvp.md` §30 has been written and tested on staging (required before the first real family).
- [ ] The beta privacy notice and terms written by the lawyer (OQ-072) are available to beta families before the first one is invited.
