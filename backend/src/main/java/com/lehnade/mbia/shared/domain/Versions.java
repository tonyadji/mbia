package com.lehnade.mbia.shared.domain;

/**
 * Optimistic concurrency of mutable aggregates (data-model.md §2.3, architecture.md §12): a
 * mutation starts only from the version its author has seen.
 */
public final class Versions {

    public static final String STALE_DETAIL = "The resource was modified by someone else. Reload it and try again.";

    private Versions() {}

    /**
     * @throws DomainException {@code CONCURRENT_MODIFICATION} when the expected version is not the
     *     persisted one
     */
    public static void requireCurrent(long expected, long persisted) {
        if (expected != persisted) {
            throw new DomainException(ErrorCode.CONCURRENT_MODIFICATION, STALE_DETAIL);
        }
    }
}
