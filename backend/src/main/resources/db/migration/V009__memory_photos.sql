-- Photos in Memories (OQ-042; data-model.md §13, §14, §14bis; Phase 4 plan §3.3).
-- V007 and V008 are immutable: their unnamed checks are dropped here and replaced by named ones.

-- A STORY needs a title only: the text is required when the Memory has no photo, a rule that
-- spans memory_photos and is enforced by the application (§14).
ALTER TABLE memories
    DROP CONSTRAINT memories_type_check,
    DROP CONSTRAINT memories_status_check,
    DROP CONSTRAINT memories_check;

ALTER TABLE memories
    ADD CONSTRAINT ck_memory_type CHECK (type IN ('STORY')),
    ADD CONSTRAINT ck_memory_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    ADD CONSTRAINT ck_memory_story_title CHECK (type <> 'STORY' OR title IS NOT NULL);

-- Memory photos are uploaded as MEMORY_PHOTO (§13).
ALTER TABLE media_assets
    DROP CONSTRAINT media_assets_purpose_check,
    DROP CONSTRAINT media_assets_status_check,
    DROP CONSTRAINT media_assets_upload_size_bytes_check,
    DROP CONSTRAINT media_assets_check;

ALTER TABLE media_assets
    ADD CONSTRAINT ck_media_asset_purpose CHECK (purpose IN ('PROFILE_PICTURE', 'MEMORY_PHOTO')),
    ADD CONSTRAINT ck_media_asset_status CHECK (status IN ('PENDING_UPLOAD', 'READY', 'FAILED', 'ARCHIVED')),
    -- At most 15 MB per upload (technical-specification.md §16).
    ADD CONSTRAINT ck_media_asset_size CHECK (upload_size_bytes > 0 AND upload_size_bytes <= 15728640),
    -- A READY asset has both derivatives (ADR-007).
    ADD CONSTRAINT ck_media_asset_ready_derivatives CHECK (status <> 'READY'
        OR (display_storage_key IS NOT NULL AND thumbnail_storage_key IS NOT NULL));

-- The photos of a Memory, in the order they were added (§14bis). An asset is the photo of one
-- Memory at most, a position is taken once per Memory, and the Memory and its photos belong to
-- the same Family (§2.5). The taken date is a partial date (§9, OQ-033).
CREATE TABLE memory_photos (
    family_id             UUID NOT NULL,
    memory_id             UUID NOT NULL,
    media_asset_id        UUID NOT NULL,
    position              SMALLINT NOT NULL,
    caption               TEXT,
    taken_date            DATE,
    taken_year            SMALLINT,
    taken_date_precision  VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_memory_photos PRIMARY KEY (memory_id, media_asset_id),
    CONSTRAINT uq_memory_photo_asset UNIQUE (media_asset_id),
    CONSTRAINT uq_memory_photo_position UNIQUE (memory_id, position),

    CONSTRAINT fk_memory_photo_memory
        FOREIGN KEY (memory_id, family_id) REFERENCES memories(id, family_id),
    CONSTRAINT fk_memory_photo_media
        FOREIGN KEY (media_asset_id, family_id) REFERENCES media_assets(id, family_id),

    CONSTRAINT ck_memory_photo_caption CHECK (caption IS NULL OR char_length(caption) <= 5000),
    CONSTRAINT ck_memory_photo_taken_precision
        CHECK (taken_date_precision IN ('EXACT', 'YEAR_ONLY', 'UNKNOWN')),
    CONSTRAINT ck_memory_photo_taken_date
        CHECK ((taken_date_precision = 'EXACT' AND taken_date IS NOT NULL AND taken_year IS NULL)
            OR (taken_date_precision = 'YEAR_ONLY' AND taken_date IS NULL AND taken_year IS NOT NULL)
            OR (taken_date_precision = 'UNKNOWN' AND taken_date IS NULL AND taken_year IS NULL))
);
