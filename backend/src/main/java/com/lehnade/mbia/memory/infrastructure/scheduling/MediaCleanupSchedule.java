package com.lehnade.mbia.memory.infrastructure.scheduling;

import com.lehnade.mbia.memory.application.cleanupmedia.CleanUpMediaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the media cleanup in-process (ADR-007 §4): no queue nor worker. */
@Component
@ConditionalOnBooleanProperty(name = "mbia.scheduling.enabled", matchIfMissing = true)
class MediaCleanupSchedule {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupSchedule.class);

    private final CleanUpMediaUseCase cleanUpMedia;

    MediaCleanupSchedule(CleanUpMediaUseCase cleanUpMedia) {
        this.cleanUpMedia = cleanUpMedia;
    }

    @Scheduled(fixedDelayString = "${mbia.media.cleanup-interval}", initialDelayString = "PT1M")
    void cleanUp() {
        try {
            cleanUpMedia.cleanUp();
        } catch (RuntimeException e) {
            // The next run retries; the exception message could name a storage key.
            log.warn("Media cleanup failed: {}", e.getClass().getSimpleName());
        }
    }
}
