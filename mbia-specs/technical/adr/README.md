# Architecture Decision Records

Architecturally significant decisions for the Mbia MVP. Format: Context / Decision / Consequences.

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-modular-monolith.md) | Modular monolith | Accepted |
| [ADR-002](ADR-002-postgresql-for-family-graph.md) | PostgreSQL instead of a graph database | Accepted |
| [ADR-003](ADR-003-rest-openapi-contract-first.md) | Contract-first REST/OpenAPI | Accepted |
| [ADR-004](ADR-004-s3-object-storage.md) | S3-compatible object storage | Accepted |
| [ADR-005](ADR-005-keycloak-identity-provider.md) | Keycloak as OIDC identity provider | Accepted |
| [ADR-006](ADR-006-bilingual-fr-en.md) | French + English from the MVP | Accepted |
| [ADR-007](ADR-007-image-processing.md) | Synchronous server-side image processing | Accepted |
| [ADR-008](ADR-008-product-analytics.md) | Product analytics with PostHog (EU) | Accepted |

A new ADR is required to change framework, architectural style, persistence semantics, identity provider or public API style. Agents may draft ADRs with status `Proposed`; only a human sets `Accepted`.
