# Mbia MVP — Technology Stack

**Baseline verified:** 2026-09-25

## Backend

- Java 25 LTS
- Spring Boot 4.1.1
- Spring MVC
- Spring Security (OAuth2 Resource Server / OIDC JWT)
- Spring Data JPA + Hibernate
- PostgreSQL 18.x
- Flyway
- Maven Wrapper
- Bean Validation
- JUnit 5 + AssertJ + Mockito
- Testcontainers for PostgreSQL integration tests
- Spring Boot Actuator
- ArchUnit (or Spring Modulith verification) for module-boundary tests
- OpenAPI Generator (Spring server interfaces generated from `api/openapi.yaml`)
- TwelveMonkeys ImageIO + `metadata-extractor` for image processing (ADR-007)
- AWS SDK for Java v2 (S3 client, compatible with MinIO)

## Frontend

- React 19.3
- TypeScript
- Vite 8.1
- React Router 8.x
- TanStack Query v5
- React Hook Form
- Tailwind CSS 4.3
- OpenAPI-generated TypeScript types/client layer
- `oidc-client-ts` (Authorization Code + PKCE against Keycloak)
- `i18next` + `react-i18next` (French + English, ADR-006)
- Vitest + Testing Library for unit/component tests
- Playwright for critical E2E tests

## Infrastructure

- Monorepo
- Docker / Docker Compose for local dependencies
- S3-compatible object storage (MinIO locally, S3-compatible managed storage in production)
- Keycloak as OIDC identity provider (ADR-005), image tag pinned in `docker-compose.yml`
- Transactional email provider (SMTP); Mailpit locally
- PostHog Cloud EU for product analytics (ADR-008, proposed)
- Containerized Spring Boot backend
- Static frontend hosting + CDN
- Managed PostgreSQL in production

## Version policy

The versions above are the project baseline, not a rule to upgrade automatically.

- Major upgrades require an ADR and explicit review.
- Patch/security upgrades may be applied through normal dependency maintenance.
- Maven and npm lock/build metadata must pin reproducible builds.
- An agent must not silently replace the selected framework or major version.
