package com.lehnade.mbia.memory.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A Family Memory linked to one or more Persons (mvp.md §17, data-model.md §14, §15). In this
 * iteration every Memory is a STORY: a title and a written text, both required (Phase 3 plan
 * §3.1). The status of the related Persons is checked by the use cases, since it belongs to the
 * genealogy module.
 */
public final class Memory {

    public static final int TITLE_MAX_LENGTH = 250;
    public static final int CONTENT_MAX_LENGTH = 50_000;

    private final MemoryId id;
    private final UUID familyId;
    private final MemoryType type;
    private final MemoryStatus status;
    private final String title;
    private final String content;
    private final Set<UUID> relatedPersonIds;
    private final UUID createdBy;
    private final UUID updatedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private Memory(MemoryId id, UUID familyId, MemoryType type, MemoryStatus status, String title, String content,
            Collection<UUID> relatedPersonIds, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        this.relatedPersonIds = Set.copyOf(relatedPersonIds);
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /**
     * A new ACTIVE story. The title is trimmed; the text is kept as written, line breaks included.
     *
     * @throws DomainException {@code VALIDATION_FAILED} when the title or the text is missing,
     *     blank or too long, or when no Person is related
     */
    public static Memory createStory(MemoryId id, UUID familyId, String title, String content,
            Collection<UUID> relatedPersonIds, UUID createdBy, Instant now) {
        return new Memory(id, familyId, MemoryType.STORY, MemoryStatus.ACTIVE, validTitle(title),
                validContent(content), validPersons(relatedPersonIds), createdBy, createdBy, now, now, 0);
    }

    /**
     * This story with the given changes; an absent value keeps the current one (OQ-008). The
     * version is the one read: the repository increments it when writing.
     *
     * @return this same instance when nothing changes, so that nothing is written
     * @throws DomainException {@code VALIDATION_FAILED} for a blank or too long title or text, or
     *     an empty list of Persons
     */
    public Memory updateStory(Optional<String> title, Optional<String> content,
            Optional<? extends Collection<UUID>> relatedPersonIds, UUID updatedBy, Instant now) {
        String newTitle = title.map(Memory::validTitle).orElse(this.title);
        String newContent = content.map(Memory::validContent).orElse(this.content);
        Set<UUID> newPersons = relatedPersonIds.map(Memory::validPersons).orElse(this.relatedPersonIds);
        if (newTitle.equals(this.title) && newContent.equals(this.content)
                && newPersons.equals(this.relatedPersonIds)) {
            return this;
        }
        return new Memory(id, familyId, type, status, newTitle, newContent, newPersons, createdBy, updatedBy,
                createdAt, now, version);
    }

    /** This Memory, ARCHIVED: hidden everywhere, kept for support (mvp.md §17). */
    public Memory archive(UUID archivedBy, Instant now) {
        return new Memory(id, familyId, type, MemoryStatus.ARCHIVED, title, content, relatedPersonIds, createdBy,
                archivedBy, createdAt, now, version);
    }

    public boolean isCreatedBy(UUID userId) {
        return createdBy.equals(userId);
    }

    /** The title is trimmed. */
    private static String validTitle(String title) {
        String trimmed = title == null ? null : title.strip();
        if (trimmed == null || trimmed.isEmpty()) {
            throw invalid("title", "NOT_BLANK", "The title must not be blank.");
        }
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw invalid("title", "SIZE", "The title must not exceed " + TITLE_MAX_LENGTH + " characters.");
        }
        return trimmed;
    }

    /** The text is kept as written, line breaks included. */
    private static String validContent(String content) {
        if (content == null || content.isBlank()) {
            throw invalid("content", "NOT_BLANK", "The story must not be blank.");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw invalid("content", "SIZE", "The story must not exceed " + CONTENT_MAX_LENGTH + " characters.");
        }
        return content;
    }

    private static Set<UUID> validPersons(Collection<UUID> relatedPersonIds) {
        if (relatedPersonIds == null || relatedPersonIds.isEmpty()) {
            throw invalid("relatedPersonIds", "SIZE", "A memory must be linked to at least one person.");
        }
        return Set.copyOf(relatedPersonIds);
    }

    /** Rebuilds a stored Memory. */
    public static Memory restore(MemoryId id, UUID familyId, MemoryType type, MemoryStatus status, String title,
            String content, Set<UUID> relatedPersonIds, UUID createdBy, UUID updatedBy, Instant createdAt,
            Instant updatedAt, long version) {
        return new Memory(id, familyId, type, status, title, content, relatedPersonIds, createdBy, updatedBy,
                createdAt, updatedAt, version);
    }

    private static DomainException invalid(String field, String fieldCode, String detail) {
        return new FieldValidationException(field, fieldCode, detail);
    }

    public MemoryId id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public MemoryType type() {
        return type;
    }

    public MemoryStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    /** No duplicate, at least one (data-model.md §15). */
    public Set<UUID> relatedPersonIds() {
        return relatedPersonIds;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID updatedBy() {
        return updatedBy;
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
