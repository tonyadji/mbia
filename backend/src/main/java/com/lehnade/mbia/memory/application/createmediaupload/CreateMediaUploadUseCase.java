package com.lehnade.mbia.memory.application.createmediaupload;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.memory.application.ObjectStorage;
import com.lehnade.mbia.memory.application.PresignedUpload;
import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a direct upload of an image to object storage (openapi {@code createMediaUpload};
 * technical-specification.md §16; data-model.md §13; ADR-004). ADMIN or CONTRIBUTOR only. Only
 * Person photos are uploaded in this iteration (Phase 3 plan §3.3). The asset is stored
 * {@code PENDING_UPLOAD} and a pre-signed {@code PUT} is returned; completion and processing are
 * {@code completeMediaUpload} (ADR-007). Media operations are not audited (data-model.md §17).
 */
@Service
public class CreateMediaUploadUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final MediaAssetRepository mediaAssets;
    private final ObjectStorage objectStorage;
    private final Clock clock;
    private final Duration uploadUrlValidity;

    public CreateMediaUploadUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            MediaAssetRepository mediaAssets, ObjectStorage objectStorage, Clock clock,
            @Value("${mbia.storage.upload-url-validity}") Duration uploadUrlValidity) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.mediaAssets = mediaAssets;
        this.objectStorage = objectStorage;
        this.clock = clock;
        this.uploadUrlValidity = uploadUrlValidity;
    }

    @Transactional
    public MediaUploadView create(CreateMediaUploadCommand command) {
        CurrentUser caller = currentUserAccessor.currentUser();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);

        MediaAsset asset = MediaAsset.requestUpload(MediaAssetId.newId(), command.familyId(), command.purpose(),
                command.fileName(), command.mimeType(), command.sizeBytes(), caller.id(), clock.instant());
        mediaAssets.insert(asset);
        PresignedUpload upload = objectStorage.presignUpload(asset.uploadStorageKey(), asset.uploadMimeType(),
                uploadUrlValidity);
        return new MediaUploadView(asset.id(), upload);
    }
}
