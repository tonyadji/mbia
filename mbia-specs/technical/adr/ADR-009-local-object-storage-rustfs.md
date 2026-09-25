# ADR-009 — RustFS instead of MinIO for local object storage

**Status:** Accepted (2026-09-25)  
**Amends:** ADR-004 (local implementation only)

## Context

ADR-004 and `stack.md` name MinIO as the local S3-compatible storage. While implementing PR-01 (2026-09-25), the MinIO container images turned out to be unavailable to anonymous users:

- `docker pull minio/minio` and `minio/mc` fail: the repositories no longer exist on Docker Hub;
- `quay.io/minio/minio` and `quay.io/minio/mc` return `401 UNAUTHORIZED`.

MinIO stopped distributing community binaries and images in late 2025. The remaining options are community-rebuilt forks (for example `pgsty/minio`), a vendor image available only as `:latest` (for example `chainguard/minio`), or another S3-compatible server.

Needs of the local environment (production uses managed S3-compatible storage and is not affected):

- S3 API with pre-signed PUT and GET URLs (ADR-004, ADR-007);
- private buckets by default, so that no object is publicly readable;
- a pinnable, publicly pullable image, which also works in CI and Testcontainers;
- a web console for the human check;
- same code path as production: the AWS SDK v2 pointed at an endpoint.

## Decision

Use **RustFS** (`rustfs/rustfs`, Apache-2.0, S3-compatible, version 1.0.0 released 2026-09-16) as local S3-compatible storage, pinned by image tag in `docker-compose.yml`.

- API on port 9000, console on port 9001 (same ports as the previous MinIO plan).
- The bucket `mbia-media` is created by a one-shot init container using the official **`amazon/aws-cli`** image (pinned). This way the tooling uses only the S3 API and is not tied to the vendor.
- Backend code talks only S3 through AWS SDK v2. It never uses any RustFS-specific API, so the local server remains replaceable.

Spike results (RustFS 1.0.0, 2026-09-25): bucket creation through `aws s3api` works; an anonymous `GET` on the bucket or on an object returns `403`; an `aws s3 presign` GET URL returns the object (`200`); the health endpoint `/health` returns `200`; the console is served on port 9001. Pre-signed **PUT** has not been exercised yet. It will be tested when media upload is implemented, and a failure there would reopen this ADR.

## Alternatives considered

- **`pgsty/minio` fork:** pinnable tags and it keeps MinIO, but the image is a community rebuild of a project whose upstream no longer publishes. Its long-term maintenance is uncertain.
- **`chainguard/minio`:** a reputable publisher, but the free tier only offers `:latest`. Pinning is possible only by digest.
- **Garage, SeaweedFS:** mature, but their bucket and key setup is more involved and they have no built-in console comparable to MinIO's.

## Consequences

- Replace "MinIO" with "RustFS" where it refers to the local environment: `stack.md`, `technical-specification.md` §18, ADR-004 ("MinIO locally"), `AGENTS.md` (local services), PR-01 in `delivery/phase-1-walking-skeleton.md`.
- RustFS 1.0 is a young project. If a local S3 behaviour we depend on diverges from production storage, prefer fixing the local setup or reconsidering this ADR over adding code specific to one storage server.
