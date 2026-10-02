# Keycloak (local)

Local OIDC identity provider for Mbia (ADR-005). `docker compose up -d` starts Keycloak in dev mode and imports [`realm-mbia.json`](realm-mbia.json) with no manual step.

| What | URL |
|---|---|
| Admin console (realm `master`) | http://localhost:8081/admin/ (`admin` / `admin-local`) |
| Account console, realm `mbia` (sign in, sign up) | http://localhost:8081/realms/mbia/account |
| Issuer | `http://localhost:8081/realms/mbia` |
| OIDC discovery | http://localhost:8081/realms/mbia/.well-known/openid-configuration |
| Emails (verification, password reset) | Mailpit, http://localhost:8025 |

## Realm `mbia`

- Sign-up allowed; the email is the username; one account per email; email verification required; forgot password allowed; brute-force protection on; passwords of at least 8 characters.
- Keycloak pages and emails in French (default) and English. The language chosen on the sign-up page is stored on the account and used for its emails and the `locale` claim.
- With email verification on, Keycloak 26.7 asks for the password **after** the email is verified, not on the sign-up form.
- SMTP: Mailpit (`mailpit:1025`), sender `no-reply@mbia.local`.
- Default Keycloak theme (the Mbia theme comes later).

## Client `mbia-web`

Public client for the SPA: Authorization Code flow only, PKCE `S256` required, redirect URIs, web origin and post-logout redirect on `http://localhost:5173`.
Access tokens carry `aud` = `mbia-api` (audience mapper) and the claims `email`, `email_verified`, `name` and `locale`.
Keycloak realm roles are not included: Mbia keeps its own family roles.

## Test users

**Local test data only.** These accounts and passwords exist only in this local realm.

| Email | Password | Email verified | Language |
|---|---|---|---|
| `alice@mbia.local` | `alice-local-1` | yes | fr |
| `bob@mbia.local` | `bob-local-1` | yes | en |
| `carol@mbia.local` | `carol-local-1` | no | fr |

## Changing the realm

The import runs only when the realm does not exist yet: an existing realm is never overwritten. After editing `realm-mbia.json`, reset the local data (this also empties the Mbia database and the media bucket):

```bash
docker compose down -v && docker compose up -d
```

## Inspecting an access token

Admin console › realm `mbia` › Clients › `mbia-web` › Client scopes › Evaluate › pick a user › **Generated access token**.
