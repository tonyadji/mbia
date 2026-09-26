package com.lehnade.mbia.memory.api;

import com.lehnade.mbia.api.generated.MediaApi;
import com.lehnade.mbia.api.generated.model.CreateMediaUploadRequest;
import com.lehnade.mbia.api.generated.model.MediaAssetResponse;
import com.lehnade.mbia.api.generated.model.MediaUploadResponse;
import com.lehnade.mbia.memory.application.createmediaupload.CreateMediaUploadCommand;
import com.lehnade.mbia.memory.application.createmediaupload.CreateMediaUploadUseCase;
import com.lehnade.mbia.memory.application.createmediaupload.MediaUploadView;
import com.lehnade.mbia.memory.domain.MediaPurpose;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direct uploads of images to object storage (ADR-004). Completion and processing arrive with
 * PR-36: until then {@code completeMediaUpload} answers like a route that does not exist yet
 * ({@code RESOURCE_NOT_FOUND}).
 */
@RestController
class MediaController implements MediaApi {

    private final CreateMediaUploadUseCase createMediaUpload;

    MediaController(CreateMediaUploadUseCase createMediaUpload) {
        this.createMediaUpload = createMediaUpload;
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
        throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
    }
}
