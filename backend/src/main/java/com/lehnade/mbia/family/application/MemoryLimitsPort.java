package com.lehnade.mbia.family.application;

/**
 * The limits of Memories, provided by the memory module which enforces them (Phase 4 plan §3.4),
 * so that family does not depend on memory.
 */
public interface MemoryLimitsPort {

    /** @return the maximum number of photos of a Memory ({@code mbia.memory.max-photos}, OQ-042) */
    int maxPhotosPerMemory();
}
