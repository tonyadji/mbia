-- Explicit genealogical facts (data-model.md §11). Enumerations are checked VARCHAR columns (§3).
-- Parental cycles, ARCHIVED/MERGED Persons and duplicates are also checked by the application (§11.4).
CREATE TABLE family_relationships (
    id                 UUID PRIMARY KEY,
    family_id          UUID NOT NULL REFERENCES families(id),
    type               VARCHAR(20) NOT NULL,
    source_person_id   UUID NOT NULL,
    target_person_id   UUID NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by         UUID NOT NULL REFERENCES users(id),
    updated_by         UUID NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    archived_at        TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,

    UNIQUE (id, family_id),

    CHECK (type IN ('PARENT_OF', 'PARTNER_OF')),
    CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CHECK (source_person_id <> target_person_id),
    -- PARTNER_OF is symmetric and stored once, endpoints in UUID order (§11.2).
    CHECK (type <> 'PARTNER_OF' OR source_person_id < target_person_id),

    -- Both Persons belong to the relationship's Family (§2.5).
    CONSTRAINT fk_relationship_source_person
        FOREIGN KEY (source_person_id, family_id) REFERENCES persons(id, family_id),
    CONSTRAINT fk_relationship_target_person
        FOREIGN KEY (target_person_id, family_id) REFERENCES persons(id, family_id)
);

-- One ACTIVE relation per type and ordered pair (§11.3): the final guard against duplicate races.
CREATE UNIQUE INDEX uq_active_parent_relationship
ON family_relationships (family_id, type, source_person_id, target_person_id)
WHERE status = 'ACTIVE' AND type = 'PARENT_OF';

CREATE UNIQUE INDEX uq_active_partner_relationship
ON family_relationships (family_id, type, source_person_id, target_person_id)
WHERE status = 'ACTIVE' AND type = 'PARTNER_OF';

-- Local graph traversal: parents, children, partners, cycle check (§23.2).
CREATE INDEX idx_rel_source_active
ON family_relationships (family_id, source_person_id, type)
WHERE status = 'ACTIVE';

CREATE INDEX idx_rel_target_active
ON family_relationships (family_id, target_person_id, type)
WHERE status = 'ACTIVE';

-- Removed relationships of a Person (§23.2bis).
CREATE INDEX idx_rel_status_source
ON family_relationships (family_id, status, source_person_id);

CREATE INDEX idx_rel_status_target
ON family_relationships (family_id, status, target_person_id);
