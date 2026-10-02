package com.lehnade.mbia.family.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A private collaborative family space (data-model.md §6). */
public final class Family {

    public static final int NAME_MAX_LENGTH = 200;

    private final FamilyId id;
    private final String name;
    private final UUID createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private Family(FamilyId id, String name, UUID createdBy, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /**
     * A new Family named by its creator. The name is trimmed and must then hold 1 to
     * {@value #NAME_MAX_LENGTH} characters.
     */
    public static Family create(FamilyId id, String name, UUID createdBy, Instant now) {
        return new Family(id, validName(name), createdBy, now, now, 0);
    }

    public static Family restore(FamilyId id, String name, UUID createdBy, Instant createdAt, Instant updatedAt,
            long version) {
        return new Family(id, name, createdBy, createdAt, updatedAt, version);
    }

    /**
     * The same Family under a new name, following the creation rules. The version is unchanged:
     * persisting the rename increments it.
     */
    public Family rename(String newName, Instant now) {
        return new Family(id, validName(newName), createdBy, createdAt, now, version);
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "The family name must not be blank.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > NAME_MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "The family name must not exceed " + NAME_MAX_LENGTH + " characters.");
        }
        return trimmed;
    }

    public FamilyId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
