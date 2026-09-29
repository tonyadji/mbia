package com.lehnade.mbia.memory.domain;

import java.util.Objects;

/**
 * A photo of a Memory (data-model.md §14bis, OQ-042): an uploaded {@code MEMORY_PHOTO} asset at its
 * position, the order of addition, with an optional caption and taken date.
 *
 * @param caption {@code null} when there is none; never logged nor audited
 * @param takenAt {@link PartialDay#UNKNOWN} when not given
 */
public record MemoryPhoto(MediaAssetId mediaAssetId, int position, String caption, PartialDay takenAt) {

    public static final int CAPTION_MAX_LENGTH = 5000;

    public MemoryPhoto {
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        takenAt = takenAt == null ? PartialDay.UNKNOWN : takenAt;
    }

    /**
     * A photo to add, as the member describes it. The caption is kept as written, a blank one being
     * no caption.
     */
    public record New(MediaAssetId mediaAssetId, String caption, PartialDay takenAt) {

        public New {
            Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        }

        @Override
        public String toString() {
            return "MemoryPhoto.New[mediaAssetId=" + mediaAssetId.value() + "]";
        }
    }

    /** Never shows the caption, a family text. */
    @Override
    public String toString() {
        return "MemoryPhoto[mediaAssetId=" + mediaAssetId.value() + ", position=" + position + "]";
    }
}
