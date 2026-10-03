# ADR-010 — Staging on AWS: one virtual machine, managed data services

**Status:** Accepted (2026-10-03)  
**Relates to:** `stack.md` §Infrastructure, ADR-004, ADR-005, ADR-009

## Context

The MVP is developed (Phase 6). It must now run outside a developer machine for a **closed beta**: a few invited families use it and give feedback. The production target is AWS (human, 2026-09-29), but its final shape (ECS Fargate, load balancer, multi-AZ database) costs around 90–140 € per month, which is too much for a beta.

`stack.md` already fixes the production building blocks: a containerized Spring Boot backend, Keycloak (ADR-005), managed PostgreSQL, S3-compatible object storage (ADR-004), a transactional email provider (SMTP), static frontend hosting with a CDN. The repository has no container image, no deployment file and no non-local Keycloak realm yet.

The beta environment holds **real family data** (living people, children, photos; `mvp.md` §30). It is a staging environment in name, but backups, secrets and access must be handled as in production.

Three options were compared (human, 2026-09-29):

| Option | Monthly cost (estimate) | Effort |
|---|---|---|
| A — AWS managed: ECS Fargate + ALB + RDS + CloudFront + SES | 90–140 € | high |
| **B — AWS minimal: one EC2 VM with Docker Compose, RDS, S3, CloudFront, SES** | **40–55 €** | **low** |
| C — Scaleway (Paris): Serverless Containers, managed PostgreSQL, Object Storage, TEM | 40–70 € | medium |

## Decision

Use **option B** for the staging / closed-beta environment, in AWS region **eu-west-3 (Paris)**, with the same data services as the production target, so that moving to production later only replaces the VM.

| Need | Staging choice | Production target (later ADR) |
|---|---|---|
| Domain, DNS | Route 53 (registered domain + hosted zone), subdomains `app.staging.`, `api.staging.`, `auth.staging.` | same, without `staging.` |
| Frontend | Static build in a private S3 bucket behind CloudFront (Origin Access Control), ACM certificate | same |
| Backend | Container on one EC2 `t4g.medium` (ARM, 4 GB), started by Docker Compose | ECS Fargate |
| Keycloak | Container on the same VM, production mode (`start --optimized`), realm imported without test users | ECS Fargate |
| Reverse proxy, TLS | Caddy on the VM (Let's Encrypt), the only process with open ports (80, 443) | ALB + ACM |
| PostgreSQL | RDS PostgreSQL (same major version as local), `db.t4g.micro`, single-AZ, not publicly accessible, automated backups kept 7 days; two databases `mbia` and `keycloak` with separate users, as locally | RDS multi-AZ |
| Media | Private S3 bucket, CORS from the frontend origin only (`infrastructure/README.md`), versioning on | same |
| Email | Amazon SES (eu-west-3), domain verified with DKIM, SPF and DMARC; SMTP credentials used by both the backend (`MBIA_SMTP_*`) and the Keycloak realm | same |
| Images | Amazon ECR, `linux/arm64` | same |
| Secrets | SSM Parameter Store (SecureString), rendered to a `0600` env file on the VM at deploy time | Secrets Manager or Parameter Store |
| Logs | Docker `awslogs` driver → CloudWatch Logs, retention 30 days | same |
| Access to the VM | SSM Session Manager only: no SSH port open | — |
| Deployment | GitHub Actions, authenticated to AWS by OIDC (no long-lived key), manual trigger; runs the deployment on the VM with SSM Run Command | ECS deployment |
| Infrastructure as code | Terraform, state in an S3 bucket with native locking | same code, extended |

Rules:

- The backend and Keycloak reach RDS through a security group that accepts only the VM. The VM accepts only 80 and 443.
- The backend uses the VM's **instance role** for S3 (default credentials chain, `infrastructure/README.md`): no S3 access key is stored. `path-style-access` is `false` on AWS S3.
- The Keycloak admin console (`/admin`) is not exposed publicly by Caddy; it is reached through an SSM port forward.
- Flyway migrations run when the backend starts (as today); a deployment that fails to start keeps the previous containers.
- An AWS Budgets alert warns the human above the expected monthly cost.

## Alternatives considered

- **Option A now:** the production target, but it doubles or triples the cost and the setup effort for a handful of beta families. It stays the production plan.
- **PostgreSQL in a container on the VM:** saves about 15 € per month, but backups, restore and upgrades become manual, for data that is real. Rejected.
- **Frontend served by Caddy on the VM:** one fewer service, but diverges from `stack.md` (static hosting + CDN) and from production; CloudFront + S3 costs about 1 € per month.
- **Brevo or Postmark for email:** simpler to start (no sandbox), but a second provider to leave later. SES is kept because production is on AWS; its sandbox exit is requested early.
- **Option C (Scaleway):** a good sovereignty argument, but production is on AWS; running the beta elsewhere would test another platform than the target.
- **AWS CDK or console-only setup:** CDK would add a TypeScript infrastructure project; a console-only setup cannot be reviewed nor rebuilt. Terraform is reviewed like code and reused for production.

## Consequences

- New files: backend and Keycloak container images, a staging Compose file and Caddy configuration, a staging realm, Terraform code under `infrastructure/`, a deployment workflow and a runbook (`delivery/phase-7-staging.md`).
- The VM is a single point of failure: a beta outage of a few minutes during a deployment or a VM restart is accepted. The data is not on the VM (RDS, S3), so losing the VM loses no data.
- The frontend build for staging sets `VITE_API_BASE_URL`, `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID` and `VITE_SUPPORT_EMAIL` at build time.
- The human must, before the first deployment: register the domain, request the SES production access, and create the AWS account guardrails (billing alert, MFA on the root user).
- A later ADR moves the backend and Keycloak to ECS Fargate for production; RDS, S3, CloudFront, SES, Route 53 and ECR stay.
- `stack.md` §Infrastructure links this ADR for the staging environment.
- Sign-up stays open during the closed beta; the address is shared only with beta families and the frontend is served with `noindex` (OQ-070).
- The beta data (RDS databases `mbia` and `keycloak`, media bucket) is migrated to production at launch (OQ-071): the production ADR must describe this migration, and staging is then rebuilt empty for later tests.
