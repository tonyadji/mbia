package com.lehnade.mbia.memory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository {

    /** Writes a new asset (data-model.md §13). */
    void insert(MediaAsset asset);

    /**
     * Locks the asset of this Family until the end of the transaction ({@code SELECT … FOR UPDATE}),
     * so that two completions, or a completion and the cleanup, cannot interleave.
     *
     * @return the asset, whatever its status; empty when unknown or of another Family
     */
    Optional<MediaAsset> lockInFamily(UUID familyId, MediaAssetId id);

    /** Writes the status, the derivatives, the dimensions and the failure reason of the asset. */
    void update(MediaAsset asset);

    /**
     * Locks, skipping the rows locked by another transaction, the PENDING_UPLOAD assets created
     * before {@code cutoff}, of every Family (ADR-007 §4).
     */
    List<MediaAsset> lockPendingCreatedBefore(Instant cutoff, int limit);

    /**
     * Locks, skipping the rows locked by another transaction, the READY assets made ready before
     * {@code cutoff}, of every Family, in id order after {@code afterId} (null for the first page)
     * (OQ-036).
     */
    List<MediaAsset> lockReadyBefore(Instant cutoff, UUID afterId, int limit);
}
