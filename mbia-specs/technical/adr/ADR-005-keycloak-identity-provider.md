# ADR-005 — Keycloak as OIDC identity provider

**Status:** Accepted

## Context

The MVP needs sign-up, sign-in, sign-out, forgot password and email verification in French and English. Mbia must not store passwords. The same identity setup must work locally, in CI and in production, and must be well known by coding agents.

## Decision

Use **Keycloak** (current major version at project start, pinned by image tag).

- Realm `mbia`, versioned as `infrastructure/keycloak/realm-mbia.json` and imported automatically in local and CI environments.
- Public client `mbia-web`: Authorization Code + PKCE, used by the SPA (`oidc-client-ts`).
- The backend is an OAuth2 Resource Server validating JWT issuer, signature and audience.
- Realm settings: user registration enabled, email as username, email verification required, forgot password enabled, internationalisation enabled with `fr` (default) and `en`, Mbia login theme.
- Keycloak sends its own emails (verification, password reset) through the transactional email provider's SMTP; locally through Mailpit.
- Production: Keycloak container with its own database (`keycloak`) on the managed PostgreSQL instance, separate from the Mbia business database.

### User provisioning

Mbia creates its `users` row just-in-time on the first authenticated API call (any endpoint, typically `GET /me`):

- key: `sub` claim → `users.identity_provider_subject`;
- `email`, `name` and `locale` claims populate the row;
- a token whose `email_verified` claim is not `true` is rejected with `403 EMAIL_NOT_VERIFIED`;
- when the email claim changes, Mbia updates its copy on the next call.

Mbia never calls the Keycloak admin API during normal product flows.

## Consequences

- One more service to host and upgrade in production.
- No vendor lock-in and no per-user cost.
- Login and registration pages are hosted by Keycloak and must be themed to match Mbia in both languages.
- Phone-number login is not available in the MVP; it can be added later through Keycloak extensions or a new ADR.
