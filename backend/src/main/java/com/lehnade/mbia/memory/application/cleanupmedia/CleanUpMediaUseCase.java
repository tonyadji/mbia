package com.lehnade.mbia.memory.application.cleanupmedia;

import com.lehnade.mbia.genealogy.application.ProfilePictures;
import com.lehnade.mbia.memory.application.ObjectStorage;
import com.lehnade.mbia.memory.domain.MediaAsset;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import com.lehnade.mbia.memory.domain.MediaFailureReason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes the uploads that will never be shown, in every Family, so that no photo stays stored
 * without being visible (ADR-007 §4, data-model.md §13, OQ-036):
 *
 * <ul>
 *   <li>PENDING_UPLOAD assets created more than 24 hours ago;
 *   <li>READY assets that are still nobody's photo 24 hours after {@code ready_at}.
 * </ul>
 *
 * <p>Their objects are deleted and they become FAILED; an attached asset is never touched. Each
 * batch is one transaction holding its assets locked, which a completion or an attachment also
 * takes: an asset attached meanwhile is seen as attached. Objects are deleted before the commit, so
 * that a failed commit leaves the asset to the next run rather than serving deleted files.
 */
@Service
public class CleanUpMediaUseCase {

    static final Duration GRACE_PERIOD = Duration.ofHours(24);
    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(CleanUpMediaUseCase.class);

    private final MediaAssetRepository mediaAssets;
    private final ObjectStorage objectStorage;
    private final ProfilePictures profilePictures;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CleanUpMediaUseCase(MediaAssetRepository mediaAssets, ObjectStorage objectStorage,
            ProfilePictures profilePictures, TransactionTemplate transaction, Clock clock) {
        this.mediaAssets = mediaAssets;
        this.objectStorage = objectStorage;
        this.profilePictures = profilePictures;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** @return how many assets became FAILED */
    public int cleanUp() {
        Instant cutoff = clock.instant().minus(GRACE_PERIOD);
        int failed = 0;
        int batch;
        do {
            batch = transaction.execute(status -> failAll(mediaAssets.lockPendingCreatedBefore(cutoff, BATCH_SIZE),
                    MediaFailureReason.UPLOAD_EXPIRED));
            failed += batch;
        } while (batch == BATCH_SIZE);

        UUID after = null;
        while (true) {
            UUID from = after;
            ReadyBatch ready = transaction.execute(status -> failUnattached(cutoff, from));
            failed += ready.failed();
            if (ready.lastId() == null) {
                break;
            }
            after = ready.lastId();
        }
        if (failed > 0) {
            log.info("Media cleanup: {} assets removed", failed);
        }
        return failed;
    }

    /** One page of old READY assets, in id order: attached ones stay READY and are skipped next time. */
    private ReadyBatch failUnattached(Instant cutoff, UUID after) {
        List<MediaAsset> ready = mediaAssets.lockReadyBefore(cutoff, after, BATCH_SIZE);
        if (ready.isEmpty()) {
            return new ReadyBatch(0, null);
        }
        Set<UUID> attached = profilePictures.inUse(ready.stream().map(asset -> asset.id().value()).toList());
        int failed = failAll(ready.stream().filter(asset -> !attached.contains(asset.id().value())).toList(),
                MediaFailureReason.NEVER_ATTACHED);
        return new ReadyBatch(failed, ready.getLast().id().value());
    }

    private int failAll(List<MediaAsset> assets, MediaFailureReason reason) {
        for (MediaAsset asset : assets) {
            asset.allStorageKeys().forEach(objectStorage::delete);
            mediaAssets.update(asset.markFailed(reason));
        }
        return assets.size();
    }

    private record ReadyBatch(int failed, UUID lastId) {}
}
