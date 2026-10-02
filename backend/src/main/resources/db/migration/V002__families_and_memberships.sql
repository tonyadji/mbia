-- Families and their memberships (data-model.md §6–7). Enumerations are checked VARCHAR columns (§3).
CREATE TABLE families (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,

    CHECK (btrim(name) <> ''),
    CHECK (status IN ('ACTIVE'))
);

CREATE TABLE family_memberships (
    id          UUID PRIMARY KEY,
    family_id   UUID NOT NULL REFERENCES families(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    role        VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at   TIMESTAMPTZ NOT NULL,
    removed_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,

    UNIQUE (family_id, user_id),
    CHECK (role IN ('ADMIN', 'CONTRIBUTOR', 'VIEWER')),
    CHECK (status IN ('ACTIVE', 'REMOVED')),
    CHECK ((status = 'REMOVED') = (removed_at IS NOT NULL))
);

CREATE INDEX idx_memberships_user_active
ON family_memberships (user_id, status);

CREATE INDEX idx_memberships_family_active
ON family_memberships (family_id, status);
