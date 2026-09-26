package com.lehnade.mbia.memory.application.createmediaupload;

import com.lehnade.mbia.memory.domain.MediaPurpose;
import java.util.UUID;

public record CreateMediaUploadCommand(UUID familyId, MediaPurpose purpose, String fileName, String mimeType,
        long sizeBytes) {}
