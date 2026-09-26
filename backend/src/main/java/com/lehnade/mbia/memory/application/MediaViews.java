package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaStatus;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Media assets as the API shows them. Only a READY asset has URLs: pre-signed {@code GET}s of its
 * derivatives, valid 60 minutes and never stored (data-model.md §13, ADR-007 §5). Signing is
 * local: it costs no request to the storage.
 */
@Service
public class MediaViews {

    private final ObjectStorage objectStorage;
    private final Duration downloadUrlValidity;

    public MediaViews(ObjectStorage objectStorage,
            @Value("${mbia.storage.download-url-validity}") Duration downloadUrlValidity) {
        this.objectStorage = objectStorage;
        this.downloadUrlValidity = downloadUrlValidity;
    }

    public MediaAssetView of(MediaAsset asset) {
        boolean ready = asset.status() == MediaStatus.READY;
        return new MediaAssetView(asset.id(), asset.purpose(), asset.status(), asset.uploadMimeType(),
                asset.uploadSizeBytes(), asset.widthPx(), asset.heightPx(),
                ready ? objectStorage.presignDownload(asset.displayStorageKey(), downloadUrlValidity) : null,
                ready ? objectStorage.presignDownload(asset.thumbnailStorageKey(), downloadUrlValidity) : null);
    }
}
