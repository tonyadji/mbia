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

## Frontend

- React 19.3
- TypeScript
- Vite 8.1
- React Router 8.x
- TanStack Query v5
- React Hook Form
- Tailwind CSS 4.3
- OpenAPI-generated TypeScript types/client layer

## Infrastructure

- Monorepo
- Docker / Docker Compose for local dependencies
- S3-compatible object storage (MinIO locally, S3-compatible managed storage in production)
- External OIDC identity provider
- Transactional email provider
- Containerized Spring Boot backend
- Static frontend hosting + CDN
- Managed PostgreSQL in production

## Version policy

The versions above are the project baseline, not a rule to upgrade automatically.

- Major upgrades require an ADR and explicit review.
- Patch/security upgrades may be applied through normal dependency maintenance.
- Maven and npm lock/build metadata must pin reproducible builds.
- An agent must not silently replace the selected framework or major version.
