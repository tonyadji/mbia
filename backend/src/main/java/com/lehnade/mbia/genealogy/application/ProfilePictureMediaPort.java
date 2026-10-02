package com.lehnade.mbia.genealogy.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * The uploaded images that serve as Person photos, provided by the memory module (Phase 3 plan
 * §3.4): genealogy never depends on memory. Takes plain ids so that memory does not depend on
 * {@code genealogy.domain}.
 */
public interface ProfilePictureMediaPort {

    /**
     * Checks that the caller may make this asset a Person's photo (data-model.md §13, OQ-036) and
     * locks it until the end of the caller's transaction, so that another attachment or the media
     * cleanup cannot interleave. Whether the asset is already used is the caller's check.
     *
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code MEDIA_NOT_FOUND} for an asset
     *     unknown or of another Family; {@code PERMISSION_DENIED} for another member's upload;
     *     {@code MEDIA_NOT_READY} unless READY; {@code VALIDATION_FAILED} for another purpose
     */
    void lockAttachable(UUID familyId, UUID mediaAssetId, UUID callerId);

    /**
     * The photo was replaced or removed, or lost in a merge (OQ-040, OQ-047): the asset becomes
     * ARCHIVED and serves no URL any more, in the caller's transaction.
     */
    void archive(UUID familyId, UUID mediaAssetId, Instant now);

    /**
     * The pre-signed URL of the photo's thumbnail, signed locally: no database or storage request.
     * An attached asset is always READY.
     */
    URI thumbnailUrl(UUID familyId, UUID mediaAssetId);
}
