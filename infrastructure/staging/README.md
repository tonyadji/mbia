# staging

Staging and closed-beta environment on AWS (ADR-010, `mbia-specs/delivery/phase-7-staging.md`). This folder grows with Phase 7: the container stack of the VM (PR-65), the deployment and the runbook (PR-68).

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
