package com.lehnade.mbia.memory.application.completemediaupload;

import com.lehnade.mbia.memory.domain.MediaAssetId;
import java.util.UUID;

public record CompleteMediaUploadCommand(UUID familyId, MediaAssetId mediaAssetId) {}
