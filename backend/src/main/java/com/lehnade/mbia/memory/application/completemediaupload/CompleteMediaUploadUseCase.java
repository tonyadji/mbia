package com.lehnade.mbia.memory.application.completemediaupload;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.memory.application.ImageProcessor;
import com.lehnade.mbia.memory.application.InvalidImageException;
import com.lehnade.mbia.memory.application.MediaAssetView;
import com.lehnade.mbia.memory.application.MediaViews;
import com.lehnade.mbia.memory.application.ObjectStorage;
import com.lehnade.mbia.memory.application.ProcessedImage;
import com.lehnade.mbia.memory.domain.ImageFormat;
import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import com.lehnade.mbia.memory.domain.MediaFailureReason;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a direct upload into a safe, viewable image, synchronously (openapi
 * {@code completeMediaUpload}; ADR-007 §3; data-model.md §13). Only the member who uploaded it
 * (OQ-036). The upload is checked (it exists, at most 15 MB, its first bytes match the declared
 * type, at most 40 megapixels), its metadata-free JPEG derivatives are written, and the original
 * is deleted, whether the upload is valid or not: nothing uploaded is ever kept or served as is.
 *
 * <p>A READY asset is returned unchanged; a FAILED one answers {@code MEDIA_INVALID} again and an
 * ARCHIVED one {@code MEDIA_NOT_READY} (OQ-044). The asset stays locked while it is processed, so
 * that two completions, or a completion and the cleanup, cannot interleave. A failure of the
 * storage itself leaves the asset PENDING_UPLOAD: the client may retry, and the cleanup removes it
 * after 24 hours. Media operations are not audited (data-model.md §17); logs name the asset,
 * never its storage keys.
 */
@Service
public class CompleteMediaUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteMediaUploadUseCase.class);
    private static final String DERIVATIVE_TYPE = "image/jpeg";

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final MediaAssetRepository mediaAssets;
    private final ObjectStorage objectStorage;
    private final ImageProcessor imageProcessor;
    private final MediaViews views;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CompleteMediaUploadUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            MediaAssetRepository mediaAssets, ObjectStorage objectStorage, ImageProcessor imageProcessor,
            MediaViews views, TransactionTemplate transaction, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.mediaAssets = mediaAssets;
        this.objectStorage = objectStorage;
        this.imageProcessor = imageProcessor;
        this.views = views;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** The FAILED status is committed before {@code MEDIA_INVALID} reaches the client. */
    public MediaAssetView complete(CompleteMediaUploadCommand command) {
        MediaAssetView completed = transaction.execute(status -> completeLocked(command));
        if (completed == null) {
            throw MediaAsset.invalid();
        }
        return completed;
    }

    /** @return the READY asset; null when the upload was refused and the asset is now FAILED */
    private MediaAssetView completeLocked(CompleteMediaUploadCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireActiveMember(command.familyId());
        MediaAsset asset = mediaAssets.lockInFamily(command.familyId(), command.mediaAssetId())
                .orElseThrow(MediaAsset::notFound);
        if (!asset.uploadedBy().equals(callerId)) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only the member who uploaded this file can complete it.");
        }
        switch (asset.status()) {
            case READY -> {
                return views.of(asset);
            }
            case FAILED -> throw MediaAsset.invalid();
            case ARCHIVED -> throw new DomainException(ErrorCode.MEDIA_NOT_READY, "This file is no longer usable.");
            case PENDING_UPLOAD -> {
                // Processed below.
            }
        }

        MediaAsset ready;
        try {
            ready = process(asset);
        } catch (InvalidImageException invalid) {
            // Derivatives are removed too, in case a previous attempt stopped after writing them.
            asset.allStorageKeys().forEach(objectStorage::delete);
            mediaAssets.update(asset.markFailed(invalid.reason()));
            log.info("Media asset {} refused: {}", asset.id().value(), invalid.reason());
            return null;
        }
        mediaAssets.update(ready);
        return views.of(ready);
    }

    private MediaAsset process(MediaAsset asset) {
        String uploadKey = asset.uploadStorageKey();
        long size = objectStorage.sizeOf(uploadKey)
                .orElseThrow(() -> new InvalidImageException(MediaFailureReason.UPLOAD_MISSING));
        if (size == 0) {
            throw new InvalidImageException(MediaFailureReason.TYPE_MISMATCH);
        }
        if (size > MediaAsset.MAX_SIZE_BYTES) {
            throw new InvalidImageException(MediaFailureReason.TOO_LARGE);
        }
        // One byte more than allowed: an object replaced by a larger one since is refused, not read whole.
        byte[] content = objectStorage.readFirst(uploadKey, MediaAsset.MAX_SIZE_BYTES + 1);
        if (content.length > MediaAsset.MAX_SIZE_BYTES) {
            throw new InvalidImageException(MediaFailureReason.TOO_LARGE);
        }
        boolean declaredType = ImageFormat.detect(content)
                .map(format -> format.mimeType().equals(asset.uploadMimeType()))
                .orElse(false);
        if (!declaredType) {
            throw new InvalidImageException(MediaFailureReason.TYPE_MISMATCH);
        }

        ProcessedImage image = imageProcessor.process(content);
        MediaAsset ready = asset.markReady(image.displayWidth(), image.displayHeight(), clock.instant());
        objectStorage.write(ready.displayStorageKey(), image.display(), DERIVATIVE_TYPE);
        objectStorage.write(ready.thumbnailStorageKey(), image.thumbnail(), DERIVATIVE_TYPE);
        objectStorage.delete(uploadKey);
        return ready;
    }
}
