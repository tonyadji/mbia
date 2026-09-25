# infrastructure

Configuration for the local services started by the root [`docker-compose.yml`](../docker-compose.yml), and later deployment files.

- `postgres/init/`: creates the `mbia` and `keycloak` databases, each with its own user, on first start.
- `keycloak/`: the `mbia` realm imported by Keycloak on first start, and its local test users (ADR-005).
- The `rustfs-init` service in `docker-compose.yml` creates the private `mbia-media` bucket through the S3 API (ADR-009).
