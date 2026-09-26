package com.lehnade.mbia.memory.domain;

/** Media asset lifecycle (data-model.md §3, §13). */
public enum MediaStatus {
    PENDING_UPLOAD,
    READY,
    FAILED,
    ARCHIVED
}
