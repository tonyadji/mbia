package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.MediaPurpose;
import com.lehnade.mbia.memory.domain.MediaStatus;
import java.net.URI;

/**
 * A media asset as the API shows it (openapi {@code MediaAssetResponse}). It holds no storage key.
 *
 * @param mimeType the type declared at upload (OQ-045)
 * @param sizeBytes the size declared at upload (OQ-045)
 * @param widthPx of the {@code display} derivative; null until READY
 * @param url the pre-signed {@code display} derivative; null unless READY
 * @param thumbnailUrl the pre-signed {@code thumbnail} derivative; null unless READY
 */
public record MediaAssetView(MediaAssetId id, MediaPurpose purpose, MediaStatus status, String mimeType,
        long sizeBytes, Integer widthPx, Integer heightPx, URI url, URI thumbnailUrl) {

    /** Never shows the pre-signed URLs, which hold storage keys and signatures. */
    @Override
    public String toString() {
        return "MediaAssetView[id=" + id.value() + ", status=" + status + "]";
    }
}
