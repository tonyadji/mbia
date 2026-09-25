# backend

Spring Boot modular monolith (Java 25, Maven Wrapper) serving the Mbia REST API.

Architecture rules: [`AGENTS.md`](../AGENTS.md) §5 and [`architecture.md`](../mbia-specs/technical/architecture.md).

## Commands

Requires JDK 25 (the build refuses any other version) and Docker.

```bash
./mvnw verify                                        # compile + tests (PostgreSQL Testcontainer)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8080/actuator/health           # {"status":"UP","components":{"db":{"status":"UP"},...}}
```

The `local` profile connects to the `docker compose` PostgreSQL. Defaults match `../.env.example`; a repository-root `.env` overrides them.

API interfaces and models are generated from `../mbia-specs/technical/api/openapi.yaml` during `generate-sources` into
`target/generated-sources/openapi/` (package `com.lehnade.mbia.api.generated`), never edited by hand. Controllers that
implement them are served under `/api/v1`.

Flyway migrations live in `src/main/resources/db/migration/` (`V###__description.sql`, immutable once committed).
