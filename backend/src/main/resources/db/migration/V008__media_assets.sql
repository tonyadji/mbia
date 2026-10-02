-- Media assets held in S3-compatible object storage (data-model.md §13, ADR-004, ADR-007).
-- Phase 3 uploads Person photos only (Phase 3 plan §3.3, OQ-040): the purpose is checked to
-- PROFILE_PICTURE; MEMORY_PHOTO is added by the migration that brings media to Memories (OQ-042).
-- Enumerations are checked VARCHAR columns (§3).
CREATE TABLE media_assets (
    id                      UUID PRIMARY KEY,
    family_id               UUID NOT NULL REFERENCES families(id),
    purpose                 VARCHAR(30) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING_UPLOAD',
    upload_storage_key      VARCHAR(1024) NOT NULL UNIQUE,
    display_storage_key     VARCHAR(1024) UNIQUE,
    thumbnail_storage_key   VARCHAR(1024) UNIQUE,
    original_filename       VARCHAR(500),
    upload_mime_type        VARCHAR(100) NOT NULL,
    upload_size_bytes       BIGINT NOT NULL,
    width_px                INTEGER,
    height_px               INTEGER,
    failure_reason          VARCHAR(100),
    uploaded_by             UUID NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL,
    ready_at                TIMESTAMPTZ,
    archived_at             TIMESTAMPTZ,

    UNIQUE (id, family_id),

    CHECK (purpose IN ('PROFILE_PICTURE')),
    CHECK (status IN ('PENDING_UPLOAD', 'READY', 'FAILED', 'ARCHIVED')),
    -- At most 15 MB per upload (technical-specification.md §16).
    CHECK (upload_size_bytes > 0 AND upload_size_bytes <= 15728640),
    -- A READY asset has both derivatives (ADR-007).
    CHECK (status <> 'READY'
           OR (display_storage_key IS NOT NULL AND thumbnail_storage_key IS NOT NULL))
);

-- The photo of a Person (§10), created with media_assets as §10 requires. The photo belongs to the
-- Person's Family (§2.5).
ALTER TABLE persons
ADD COLUMN profile_media_asset_id UUID;

ALTER TABLE persons
ADD CONSTRAINT fk_person_profile_media
FOREIGN KEY (profile_media_asset_id, family_id)
REFERENCES media_assets(id, family_id);
