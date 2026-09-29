-- When a Memory happened (mvp.md §17, OQ-063; data-model.md §9, §14, §23.5; Phase 6 plan §3.2).
-- A partial date with one source of truth per precision; "never in the future" depends on the
-- current day and is the application's rule. Every existing Memory becomes UNKNOWN: undated.
ALTER TABLE memories
    ADD COLUMN happened_date           DATE,
    ADD COLUMN happened_year           SMALLINT,
    ADD COLUMN happened_date_precision VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE memories
    ADD CONSTRAINT ck_memory_happened_date_precision
        CHECK (happened_date_precision IN ('EXACT', 'YEAR_ONLY', 'UNKNOWN')),
    ADD CONSTRAINT ck_memory_happened_date CHECK (
        (happened_date_precision = 'EXACT' AND happened_date IS NOT NULL AND happened_year IS NULL)
     OR (happened_date_precision = 'YEAR_ONLY' AND happened_date IS NULL AND happened_year IS NOT NULL
         AND happened_year BETWEEN 1 AND 9999)
     OR (happened_date_precision = 'UNKNOWN' AND happened_date IS NULL AND happened_year IS NULL));

-- The story year of the ACTIVE Memories of a Family (§23.5), read by the family story (PR-59).
-- The queries must use this exact expression for the index to apply.
CREATE INDEX idx_memories_family_story_year
    ON memories (family_id, (COALESCE(EXTRACT(YEAR FROM happened_date)::int, happened_year::int)))
    WHERE status = 'ACTIVE';
