package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.shared.domain.FieldValidationException;

/** The photos of a Memory (data-model.md §14bis, OQ-042). */
public final class MemoryPhotos {

    private MemoryPhotos() {}

    /**
     * {@code VALIDATION_FAILED} on {@code photos}: the contract describes them, but attaching and
     * changing them arrive with PR-41 and PR-42 (Phase 4 plan).
     */
    public static FieldValidationException notAvailableYet() {
        return new FieldValidationException("photos", "NOT_SUPPORTED", "Photos cannot be added to a Memory yet.");
    }
}
