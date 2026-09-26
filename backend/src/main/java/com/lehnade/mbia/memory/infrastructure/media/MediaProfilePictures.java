package com.lehnade.mbia.memory.infrastructure.media;

import com.lehnade.mbia.genealogy.application.ProfilePictureMediaPort;
import com.lehnade.mbia.memory.application.MediaViews;
import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Person photos from the media assets (data-model.md §13, OQ-036, OQ-040). Media operations are
 * not audited (data-model.md §17); the Person change is.
 */
@Component
class MediaProfilePictures implements ProfilePictureMediaPort {

    private final MediaAssetRepository assets;
    private final MediaViews views;

    MediaProfilePictures(MediaAssetRepository assets, MediaViews views) {
        this.assets = assets;
        this.views = views;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAttachable(UUID familyId, UUID mediaAssetId, UUID callerId) {
        assets.lockInFamily(familyId, new MediaAssetId(mediaAssetId))
                .orElseThrow(MediaAsset::notFound)
                .requireAttachableAsProfilePictureBy(callerId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(UUID familyId, UUID mediaAssetId, Instant now) {
        MediaAsset asset = assets.lockInFamily(familyId, new MediaAssetId(mediaAssetId))
                .orElseThrow(() -> new IllegalStateException("The photo of a Person is a media asset of its Family."));
        assets.update(asset.archive(now));
    }

    @Override
    public URI thumbnailUrl(UUID familyId, UUID mediaAssetId) {
        return views.thumbnailUrl(familyId, new MediaAssetId(mediaAssetId));
    }
}
