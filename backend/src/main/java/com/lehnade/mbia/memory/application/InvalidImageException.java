package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.memory.domain.MediaFailureReason;
import java.util.Objects;

/** An upload that cannot become a READY image; the client gets {@code MEDIA_INVALID}. */
public class InvalidImageException extends RuntimeException {

    private final MediaFailureReason reason;

    public InvalidImageException(MediaFailureReason reason) {
        super("Invalid image: " + reason, null, false, false);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public MediaFailureReason reason() {
        return reason;
    }
}
