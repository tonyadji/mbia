package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.genealogy.application.ProfilePictures;
import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import com.lehnade.mbia.memory.domain.MediaPurpose;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The media assets of Memory photos (data-model.md §13, §14bis, OQ-036, OQ-042): which may be
 * added to a Memory, and what becomes of a removed one. Shared by the create and edit use cases.
 */
@Service
public class MemoryPhotos {

    private final MediaAssetRepository mediaAssets;
    private final MemoryRepository memories;
    private final ProfilePictures profilePictures;

    public MemoryPhotos(MediaAssetRepository mediaAssets, MemoryRepository memories,
            ProfilePictures profilePictures) {
        this.mediaAssets = mediaAssets;
        this.memories = memories;
        this.profilePictures = profilePictures;
    }

    /**
     * Checks that the caller may add these assets to a Memory, and locks them, in id order so that
     * two requests cannot deadlock, until the end of the transaction: the media cleanup and another
     * attachment wait, then see them attached (OQ-036).
     *
     * @throws DomainException {@code MEDIA_NOT_FOUND} for an asset unknown or of another Family,
     *     then as {@link MediaAsset#requireAttachableBy}, then {@code MEDIA_ALREADY_USED} for an
     *     asset that is the photo of a Memory or of a Person
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAttachable(UUID familyId, Collection<MediaAssetId> ids, UUID callerId) {
        List<MediaAssetId> sorted = ids.stream().sorted(Comparator.comparing(id -> id.value().toString())).toList();
        for (MediaAssetId id : sorted) {
            mediaAssets.lockInFamily(familyId, id)
                    .orElseThrow(MediaAsset::notFound)
                    .requireAttachableBy(callerId, MediaPurpose.MEMORY_PHOTO, "photos");
        }
        if (sorted.isEmpty()) {
            return;
        }
        List<UUID> values = sorted.stream().map(MediaAssetId::value).toList();
        Set<UUID> used = new HashSet<>(memories.findPhotosAmong(values));
        used.addAll(profilePictures.inUse(values));
        if (!used.isEmpty()) {
            throw new DomainException(ErrorCode.MEDIA_ALREADY_USED, "This photo is already used.");
        }
    }

    /**
     * Photos removed from a Memory become ARCHIVED: they serve no URL and can be attached nowhere
     * any more; their objects are kept, as the rest of the soft lifecycle (data-model.md §13).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(UUID familyId, Collection<MediaAssetId> ids, Instant now) {
        ids.stream().sorted(Comparator.comparing(id -> id.value().toString())).forEach(id -> {
            MediaAsset asset = mediaAssets.lockInFamily(familyId, id)
                    .orElseThrow(() -> new IllegalStateException(
                            "The photo of a Memory is a media asset of its Family."));
            mediaAssets.update(asset.archive(now));
        });
    }

    /** {@code MEMORY_PHOTO_LIMIT_REACHED}: an addition leaves more photos than the setting (§14bis). */
    public static DomainException limitReached(int maxPhotos) {
        return new DomainException(ErrorCode.MEMORY_PHOTO_LIMIT_REACHED,
                "A memory can have at most " + maxPhotos + " photos.");
    }
}
