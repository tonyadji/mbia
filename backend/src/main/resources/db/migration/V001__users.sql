-- Mbia accounts (data-model.md §5). Credentials are owned by Keycloak (ADR-005).
CREATE TABLE users (
    id                         UUID PRIMARY KEY,
    identity_provider_subject  VARCHAR(255) NOT NULL UNIQUE,
    email                      VARCHAR(320),
    display_name               VARCHAR(200),
    preferred_locale           VARCHAR(5) NOT NULL DEFAULT 'fr',
    status                     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at                 TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,

    CHECK (preferred_locale IN ('fr', 'en')),
    CHECK (status IN ('ACTIVE', 'DELETED')),
    CHECK (status = 'DELETED' OR email IS NOT NULL)
);

CREATE UNIQUE INDEX uq_users_email_lower
ON users (lower(email))
WHERE status = 'ACTIVE';
