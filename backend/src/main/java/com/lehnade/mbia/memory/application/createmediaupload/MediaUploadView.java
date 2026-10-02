package com.lehnade.mbia.memory.application.createmediaupload;

import com.lehnade.mbia.memory.application.PresignedUpload;
import com.lehnade.mbia.memory.domain.MediaAssetId;

/** An upload slot: the asset and where the browser uploads it. It holds no storage key. */
public record MediaUploadView(MediaAssetId mediaAssetId, PresignedUpload upload) {}
