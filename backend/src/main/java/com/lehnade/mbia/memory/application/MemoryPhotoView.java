package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.PartialDay;
import java.net.URI;

/**
 * A photo of a Memory as the API shows it (openapi {@code MemoryPhotoResponse}). It holds no
 * storage key.
 *
 * @param url the pre-signed {@code display} derivative
 * @param thumbnailUrl the pre-signed {@code thumbnail} derivative
 * @param widthPx of the {@code display} derivative
 */
public record MemoryPhotoView(MediaAssetId mediaAssetId, String caption, PartialDay takenAt, URI url,
        URI thumbnailUrl, Integer widthPx, Integer heightPx) {

    /** Never shows the caption nor the pre-signed URLs, which hold storage keys and signatures. */
    @Override
    public String toString() {
        return "MemoryPhotoView[mediaAssetId=" + mediaAssetId.value() + "]";
    }
}
