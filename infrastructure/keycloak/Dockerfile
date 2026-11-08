# syntax=docker/dockerfile:1
# Keycloak for staging (ADR-005, ADR-010, Phase 7 plan PR-65): production mode, PostgreSQL, the staging realm.
# Same pinned version as docker-compose.yml (local, dev mode). Build from the repository root:
#   docker build -f infrastructure/keycloak/Dockerfile -t mbia-keycloak .
# Configured by environment variables only: infrastructure/staging/README.md.

FROM quay.io/keycloak/keycloak:26.7.4 AS build
# Build-time options, fixed in the optimized server.
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=false
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.4
COPY --from=build /opt/keycloak/ /opt/keycloak/
# Imported on first start only: an existing realm is never overwritten. Its ${...} placeholders are
# read from the environment at import time.
COPY infrastructure/keycloak/realm-mbia-staging.json /opt/keycloak/data/import/realm-mbia-staging.json
# TLS ends at Caddy: plain HTTP inside the Compose network, public URL from KC_HOSTNAME.
ENV KC_HTTP_ENABLED=true \
    KC_PROXY_HEADERS=xforwarded
EXPOSE 8080 9000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
