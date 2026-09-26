package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import org.springframework.stereotype.Repository;

@Repository
class JpaMediaAssetRepository implements MediaAssetRepository {

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
}
