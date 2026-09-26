package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import com.lehnade.mbia.memory.domain.MediaFailureReason;
import com.lehnade.mbia.memory.domain.MediaPurpose;
import com.lehnade.mbia.memory.domain.MediaStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaMediaAssetRepository implements MediaAssetRepository {

    /** The smallest UUID: every id comes after it. */
    private static final UUID FIRST = new UUID(0, 0);

    private final MediaAssetJpaRepository jpa;

    JpaMediaAssetRepository(MediaAssetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(MediaAsset asset) {
        jpa.saveAndFlush(new MediaAssetJpaEntity(asset.id().value(), asset.familyId(), asset.purpose().name(),
                asset.status().name(), asset.uploadStorageKey(), asset.originalFilename(), asset.uploadMimeType(),
                asset.uploadSizeBytes(), asset.uploadedBy(), asset.createdAt()));
    }

    @Override
    public Optional<MediaAsset> lockInFamily(UUID familyId, MediaAssetId id) {
        return jpa.lockByIdAndFamilyId(id.value(), familyId).map(JpaMediaAssetRepository::toDomain);
    }

    @Override
    public void update(MediaAsset asset) {
        MediaAssetJpaEntity entity = jpa.findById(asset.id().value())
                .filter(found -> found.familyId().equals(asset.familyId()))
                .orElseThrow(() -> new IllegalStateException("The media asset to update does not exist."));
        entity.change(asset.status().name(), asset.displayStorageKey(), asset.thumbnailStorageKey(),
                asset.widthPx(), asset.heightPx(),
                asset.failureReason() == null ? null : asset.failureReason().name(), asset.readyAt());
        jpa.saveAndFlush(entity);
    }

    @Override
    public List<MediaAsset> lockPendingCreatedBefore(Instant cutoff, int limit) {
        return jpa.lockPendingCreatedBefore(cutoff, limit).stream().map(JpaMediaAssetRepository::toDomain).toList();
    }

    @Override
    public List<MediaAsset> lockReadyBefore(Instant cutoff, UUID afterId, int limit) {
        return jpa.lockReadyBefore(cutoff, afterId == null ? FIRST : afterId, limit).stream()
                .map(JpaMediaAssetRepository::toDomain)
                .toList();
    }

    private static MediaAsset toDomain(MediaAssetJpaEntity entity) {
        return MediaAsset.restore(new MediaAssetId(entity.getId()), entity.familyId(),
                MediaPurpose.valueOf(entity.purpose()), MediaStatus.valueOf(entity.status()),
                entity.originalFilename(), entity.uploadMimeType(), entity.uploadSizeBytes(), entity.uploadedBy(),
                entity.createdAt(), entity.widthPx(), entity.heightPx(),
                entity.failureReason() == null ? null : MediaFailureReason.valueOf(entity.failureReason()),
                entity.readyAt());
    }
}
