package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;

/** The one answer for a Memory the caller cannot see: unknown, of another Family or ARCHIVED (OQ-037). */
public final class MemoryNotFound {

    private MemoryNotFound() {}

    public static DomainException exception() {
        return new DomainException(ErrorCode.MEMORY_NOT_FOUND, "Memory not found.");
    }
}
