-- Persons of a family graph (data-model.md §9–10). Enumerations are checked VARCHAR columns (§3).
-- profile_media_asset_id is added by the migration that creates media_assets (Phase 2 plan §3.3).
CREATE TABLE persons (
    id                     UUID PRIMARY KEY,
    family_id              UUID NOT NULL REFERENCES families(id),

    first_name             VARCHAR(150) NOT NULL,
    middle_names           VARCHAR(250),
    last_name              VARCHAR(150),
    preferred_name         VARCHAR(150),

    gender                 VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',

    birth_date             DATE,
    birth_year             SMALLINT,
    birth_date_precision   VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',

    is_deceased            BOOLEAN NOT NULL DEFAULT FALSE,
    death_date             DATE,
    death_year             SMALLINT,
    death_date_precision   VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',

    biography              TEXT,

    linked_user_id         UUID REFERENCES users(id),

    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    merged_into_person_id  UUID,

    created_by             UUID NOT NULL REFERENCES users(id),
    updated_by             UUID NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    archived_at            TIMESTAMPTZ,
    version                BIGINT NOT NULL DEFAULT 0,

    UNIQUE (id, family_id),

    CHECK (btrim(first_name) <> ''),
    CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CHECK (status IN ('ACTIVE', 'ARCHIVED', 'MERGED')),

    -- Partial dates keep one source of truth (§9): EXACT => date only, YEAR_ONLY => year only, UNKNOWN => none.
    CHECK (birth_date_precision IN ('EXACT', 'YEAR_ONLY', 'UNKNOWN')),
    CHECK ((birth_date_precision = 'EXACT' AND birth_date IS NOT NULL AND birth_year IS NULL)
        OR (birth_date_precision = 'YEAR_ONLY' AND birth_date IS NULL AND birth_year IS NOT NULL)
        OR (birth_date_precision = 'UNKNOWN' AND birth_date IS NULL AND birth_year IS NULL)),
    CHECK (death_date_precision IN ('EXACT', 'YEAR_ONLY', 'UNKNOWN')),
    CHECK ((death_date_precision = 'EXACT' AND death_date IS NOT NULL AND death_year IS NULL)
        OR (death_date_precision = 'YEAR_ONLY' AND death_date IS NULL AND death_year IS NOT NULL)
        OR (death_date_precision = 'UNKNOWN' AND death_date IS NULL AND death_year IS NULL)),
    CHECK (is_deceased OR death_date_precision = 'UNKNOWN'),

    CHECK ((status = 'MERGED') = (merged_into_person_id IS NOT NULL)),
    CHECK (id <> merged_into_person_id),

    CONSTRAINT fk_person_merged_target
        FOREIGN KEY (merged_into_person_id, family_id) REFERENCES persons(id, family_id)
);

CREATE INDEX idx_persons_family_status
ON persons (family_id, status);

CREATE INDEX idx_persons_family_name
ON persons (family_id, lower(last_name), lower(first_name));

CREATE UNIQUE INDEX uq_person_linked_user_per_family
ON persons (family_id, linked_user_id)
WHERE linked_user_id IS NOT NULL AND status <> 'MERGED';
