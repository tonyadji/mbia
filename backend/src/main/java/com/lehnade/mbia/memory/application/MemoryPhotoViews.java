package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import com.lehnade.mbia.memory.domain.MediaStatus;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The photos of Memories as the API shows them, signed in one batch per response (data-model.md
 * §14bis): the dimensions of every photo are read in one query, and the URLs are signed locally
 * from the storage keys, without a query per photo.
 */
@Component
public class MemoryPhotoViews {

    private final MediaAssetRepository assets;
    private final MediaViews mediaViews;

    public MemoryPhotoViews(MediaAssetRepository assets, MediaViews mediaViews) {
        this.assets = assets;
        this.mediaViews = mediaViews;
    }

    /** @return the photos of each Memory, in position order, by Memory id */
    public Map<MemoryId, List<MemoryPhotoView>> of(UUID familyId, Collection<Memory> memories) {
        List<UUID> ids = memories.stream()
                .flatMap(memory -> memory.photos().stream())
                .map(photo -> photo.mediaAssetId().value())
                .toList();
        Map<UUID, MediaAsset> byId = assets.findAllInFamily(familyId, ids).stream()
                .collect(Collectors.toMap(asset -> asset.id().value(), Function.identity()));
        return memories.stream().collect(Collectors.toMap(Memory::id, memory -> memory.photos().stream()
                .map(photo -> view(photo, byId.get(photo.mediaAssetId().value())))
                .flatMap(Optional::stream)
                .toList()));
    }

    public List<MemoryPhotoView> of(Memory memory) {
        return of(memory.familyId(), List.of(memory)).get(memory.id());
    }

    /**
     * An attached photo is always READY (data-model.md §13); should it not be, it is not shown
     * rather than shown without a URL.
     */
    private Optional<MemoryPhotoView> view(MemoryPhoto photo, MediaAsset asset) {
        if (asset == null || asset.status() != MediaStatus.READY) {
            return Optional.empty();
        }
        MediaAssetView media = mediaViews.of(asset);
        return Optional.of(new MemoryPhotoView(photo.mediaAssetId(), photo.caption(), photo.takenAt(), media.url(),
                media.thumbnailUrl(), media.widthPx(), media.heightPx()));
    }
}
