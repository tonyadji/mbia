-- Family Memories (data-model.md §14, §15). Phase 3 creates stories only (Phase 3 plan §3.1, §3.3):
-- no media_asset_id, caption nor taken-date columns until OQ-042 designs media on Memories, and
-- the type is checked to STORY. Enumerations are checked VARCHAR columns (§3).
CREATE TABLE memories (
    id           UUID PRIMARY KEY,
    family_id    UUID NOT NULL REFERENCES families(id),
    type         VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    title        VARCHAR(250),
    content      TEXT,

    created_by   UUID NOT NULL REFERENCES users(id),
    updated_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    archived_at  TIMESTAMPTZ,
    version      BIGINT NOT NULL DEFAULT 0,

    UNIQUE (id, family_id),

    CHECK (type IN ('STORY')),
    CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    -- A STORY has a title and a text (§14 type invariants).
    CHECK (type <> 'STORY' OR (title IS NOT NULL AND content IS NOT NULL))
);

-- The Persons of each Memory (§15): no duplicate association, and the Memory and its Persons
-- belong to the same Family (§2.5).
CREATE TABLE memory_persons (
    family_id   UUID NOT NULL,
    memory_id   UUID NOT NULL,
    person_id   UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (memory_id, person_id),

    CONSTRAINT fk_memory_person_memory
        FOREIGN KEY (memory_id, family_id) REFERENCES memories(id, family_id),
    CONSTRAINT fk_memory_person_person
        FOREIGN KEY (person_id, family_id) REFERENCES persons(id, family_id)
);

-- Person memories (§23.3).
CREATE INDEX idx_memory_person_person
ON memory_persons (family_id, person_id, memory_id);

-- Family memories, most recent first (§23.4).
CREATE INDEX idx_memories_family_recent
ON memories (family_id, created_at DESC)
WHERE status = 'ACTIVE';
