package com.lehnade.mbia.memory.domain;

/**
 * Why an asset became FAILED ({@code media_assets.failure_reason}, data-model.md §13). Internal:
 * the API only answers {@code MEDIA_INVALID}.
 */
public enum MediaFailureReason {
    /** Nothing was uploaded where the browser was told to. */
    UPLOAD_MISSING,
    /** The stored object is above 15 MB. */
    TOO_LARGE,
    /** The content is not a JPEG, PNG or WEBP image, or not the declared one. */
    TYPE_MISMATCH,
    /** Above 40 megapixels (ADR-007 §3). */
    TOO_MANY_PIXELS,
    /** The image cannot be decoded. */
    UNREADABLE,
    /** Still PENDING_UPLOAD 24 hours after its creation (ADR-007 §4). */
    UPLOAD_EXPIRED,
    /** READY but still unattached 24 hours after {@code ready_at} (OQ-036). */
    NEVER_ATTACHED
}
