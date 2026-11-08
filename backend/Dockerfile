# syntax=docker/dockerfile:1
# Container image of the Mbia backend (ADR-010, Phase 7 plan PR-64).
# Build from the repository root: the API is generated from mbia-specs/technical/api/openapi.yaml.
#   docker build -f backend/Dockerfile -t mbia-backend .
# Tests are not run here: CI runs ./mvnw verify on every pull request.

# The jar does not depend on the CPU: build it on the build machine's platform, even for another target.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
WORKDIR /workspace
COPY mbia-specs/technical/api/openapi.yaml mbia-specs/technical/api/openapi.yaml
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
WORKDIR /workspace/backend
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -Dmaven.test.skip=true package \
    && cp target/mbia-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble
RUN groupadd --system --gid 10001 mbia && useradd --system --uid 10001 --gid mbia --no-create-home mbia
WORKDIR /app
# Layers ordered from the least to the most often changed.
COPY --from=build /workspace/backend/extracted/dependencies/ ./
COPY --from=build /workspace/backend/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/backend/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/backend/extracted/application/ ./
USER mbia
EXPOSE 8080
# Configured by environment variables only: infrastructure/staging/README.md.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "application.jar"]
