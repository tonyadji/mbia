package com.lehnade.mbia.memory.api;

import com.lehnade.mbia.api.generated.MediaApi;
import com.lehnade.mbia.api.generated.model.CreateMediaUploadRequest;
import com.lehnade.mbia.api.generated.model.MediaAssetResponse;
import com.lehnade.mbia.api.generated.model.MediaStatus;
import com.lehnade.mbia.api.generated.model.MediaUploadResponse;
import com.lehnade.mbia.memory.application.MediaAssetView;
import com.lehnade.mbia.memory.application.completemediaupload.CompleteMediaUploadCommand;
import com.lehnade.mbia.memory.application.completemediaupload.CompleteMediaUploadUseCase;
import com.lehnade.mbia.memory.application.createmediaupload.CreateMediaUploadCommand;
import com.lehnade.mbia.memory.application.createmediaupload.CreateMediaUploadUseCase;
import com.lehnade.mbia.memory.application.createmediaupload.MediaUploadView;
import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.MediaPurpose;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Direct uploads of images to object storage (ADR-004), and their processing (ADR-007). */
@RestController
class MediaController implements MediaApi {

    private final CreateMediaUploadUseCase createMediaUpload;
    private final CompleteMediaUploadUseCase completeMediaUpload;

    MediaController(CreateMediaUploadUseCase createMediaUpload, CompleteMediaUploadUseCase completeMediaUpload) {
        this.createMediaUpload = createMediaUpload;
        this.completeMediaUpload = completeMediaUpload;
    }

    @Override
    public ResponseEntity<MediaUploadResponse> createMediaUpload(UUID familyId, CreateMediaUploadRequest request) {
        MediaUploadView slot = createMediaUpload.create(new CreateMediaUploadCommand(familyId,
                MediaPurpose.valueOf(request.getPurpose().name()), request.getFileName(),
                request.getMimeType().getValue(), request.getSizeBytes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new MediaUploadResponse(slot.mediaAssetId().value(),
                slot.upload().url(), MediaUploadResponse.MethodEnum.PUT,
                slot.upload().expiresAt().atOffset(ZoneOffset.UTC), slot.upload().requiredHeaders()));
    }

    @Override
    public ResponseEntity<MediaAssetResponse> completeMediaUpload(UUID familyId, UUID mediaAssetId) {
        MediaAssetView asset = completeMediaUpload.complete(
                new CompleteMediaUploadCommand(familyId, new MediaAssetId(mediaAssetId)));
        return ResponseEntity.ok(toResponse(asset));
    }

    private static MediaAssetResponse toResponse(MediaAssetView asset) {
        return new MediaAssetResponse(asset.id().value(),
                com.lehnade.mbia.api.generated.model.MediaPurpose.valueOf(asset.purpose().name()),
                MediaStatus.valueOf(asset.status().name()), asset.mimeType())
                .sizeBytes(asset.sizeBytes())
                .widthPx(asset.widthPx())
                .heightPx(asset.heightPx())
                .url(asset.url())
                .thumbnailUrl(asset.thumbnailUrl());
    }
}
