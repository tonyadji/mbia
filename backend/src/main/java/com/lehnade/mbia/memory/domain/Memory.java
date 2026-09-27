package com.lehnade.mbia.memory.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A Family Memory linked to one or more Persons (mvp.md §17, data-model.md §14, §14bis, §15). Every
 * Memory is a STORY: a title, a text and 0 to a few photos; the text is required when it has no
 * photo (OQ-042). The status of the related Persons, the photo limit and the media assets are
 * checked by the use cases.
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
    private final List<MemoryPhoto> photos;
    private final UUID createdBy;
    private final UUID updatedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private Memory(MemoryId id, UUID familyId, MemoryType type, MemoryStatus status, String title, String content,
            Collection<UUID> relatedPersonIds, List<MemoryPhoto> photos, UUID createdBy, UUID updatedBy,
            Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.title = Objects.requireNonNull(title, "title");
        this.content = content;
        this.relatedPersonIds = Set.copyOf(relatedPersonIds);
        this.photos = photos.stream().sorted(Comparator.comparingInt(MemoryPhoto::position)).toList();
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /**
     * A new ACTIVE story. The title is trimmed; the text is kept as written, line breaks included.
     * The photos take the positions 1 to n in the given order (data-model.md §14bis).
     *
     * @param content {@code null} for a Memory without text, allowed only with a photo
     * @throws DomainException {@code VALIDATION_FAILED} when the title is missing, blank or too
     *     long, when the text is blank or too long, or missing without a photo, when no Person is
     *     related, or when a photo is given twice or has a caption too long
     */
    public static Memory createStory(MemoryId id, UUID familyId, String title, String content,
            Collection<UUID> relatedPersonIds, List<MemoryPhoto.New> photos, UUID createdBy, Instant now) {
        String validTitle = validTitle(title);
        List<MemoryPhoto.New> valid = validPhotos(photos);
        List<MemoryPhoto> numbered = new ArrayList<>();
        for (MemoryPhoto.New photo : valid) {
            numbered.add(new MemoryPhoto(photo.mediaAssetId(), numbered.size() + 1, photo.caption(),
                    photo.takenAt()));
        }
        if (content == null && numbered.isEmpty()) {
            throw invalid("content", "NOT_BLANK", "A memory without photo needs a story.");
        }
        return new Memory(id, familyId, MemoryType.STORY, MemoryStatus.ACTIVE, validTitle,
                content == null ? null : validContent(content), validPersons(relatedPersonIds), numbered, createdBy,
                createdBy, now, now, 0);
    }

    /**
     * This story with the given changes; an absent value keeps the current one (OQ-008). A blank
     * text empties it. {@code photos} is the complete new list (OQ-042): a photo already on the
     * Memory keeps its position whatever its place in the list, with the caption and taken date
     * given; a new one takes the next position, after every current one, in list order; a missing
     * one is removed. Positions are never renumbered (data-model.md §14bis). The version is the one
     * read: the repository increments it when writing.
     *
     * @return this same instance when nothing changes, so that nothing is written
     * @throws DomainException {@code VALIDATION_FAILED} for a blank or too long title, a too long
     *     text, an empty list of Persons, a photo given twice or with a caption too long, or a
     *     Memory left without text and without photo
     */
    public Memory updateStory(Optional<String> title, Optional<String> content,
            Optional<? extends Collection<UUID>> relatedPersonIds, Optional<List<MemoryPhoto.New>> photos,
            UUID updatedBy, Instant now) {
        String newTitle = title.map(Memory::validTitle).orElse(this.title);
        // Not Optional.map: a text emptied to null would fall back to the current one.
        String newContent = content.isEmpty() ? this.content
                : content.get().isBlank() ? null : validContent(content.get());
        Set<UUID> newPersons = relatedPersonIds.map(Memory::validPersons).orElse(this.relatedPersonIds);
        List<MemoryPhoto> newPhotos = photos.map(this::mergedPhotos).orElse(this.photos);
        if (newContent == null && newPhotos.isEmpty()) {
            throw content.isPresent() && this.content != null
                    ? invalid("content", "NOT_BLANK", "A memory without photo needs a story.")
                    : invalid("photos", "NOT_EMPTY", "A memory without story needs a photo.");
        }
        if (newTitle.equals(this.title) && Objects.equals(newContent, this.content)
                && newPersons.equals(this.relatedPersonIds) && newPhotos.equals(this.photos)) {
            return this;
        }
        return new Memory(id, familyId, type, status, newTitle, newContent, newPersons, newPhotos, createdBy,
                updatedBy, createdAt, now, version);
    }

    /** In position order, as this Memory's photos, so that an unchanged list compares equal. */
    private List<MemoryPhoto> mergedPhotos(List<MemoryPhoto.New> photos) {
        Map<MediaAssetId, MemoryPhoto> current = new HashMap<>();
        this.photos.forEach(photo -> current.put(photo.mediaAssetId(), photo));
        int next = this.photos.stream().mapToInt(MemoryPhoto::position).max().orElse(0);
        List<MemoryPhoto> merged = new ArrayList<>();
        for (MemoryPhoto.New photo : validPhotos(photos)) {
            MemoryPhoto kept = current.get(photo.mediaAssetId());
            merged.add(new MemoryPhoto(photo.mediaAssetId(), kept != null ? kept.position() : ++next,
                    photo.caption(), photo.takenAt()));
        }
        return merged.stream().sorted(Comparator.comparingInt(MemoryPhoto::position)).toList();
    }

    /** This Memory, ARCHIVED: hidden everywhere, kept for support (mvp.md §17). */
    public Memory archive(UUID archivedBy, Instant now) {
        return new Memory(id, familyId, type, MemoryStatus.ARCHIVED, title, content, relatedPersonIds, photos,
                createdBy, archivedBy, createdAt, now, version);
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

    /** Each asset once; a blank caption is no caption (data-model.md §14bis). */
    private static List<MemoryPhoto.New> validPhotos(List<MemoryPhoto.New> photos) {
        Set<MediaAssetId> seen = new HashSet<>();
        List<MemoryPhoto.New> valid = new ArrayList<>();
        for (MemoryPhoto.New photo : photos) {
            if (!seen.add(photo.mediaAssetId())) {
                throw invalid("photos", "UNIQUE", "The same photo is given twice.");
            }
            String caption = photo.caption() == null || photo.caption().isBlank() ? null : photo.caption();
            if (caption != null && caption.length() > MemoryPhoto.CAPTION_MAX_LENGTH) {
                throw invalid("photos", "SIZE",
                        "A caption must not exceed " + MemoryPhoto.CAPTION_MAX_LENGTH + " characters.");
            }
            valid.add(new MemoryPhoto.New(photo.mediaAssetId(), caption, photo.takenAt()));
        }
        return valid;
    }

    /** Rebuilds a stored Memory. */
    public static Memory restore(MemoryId id, UUID familyId, MemoryType type, MemoryStatus status, String title,
            String content, Set<UUID> relatedPersonIds, List<MemoryPhoto> photos, UUID createdBy, UUID updatedBy,
            Instant createdAt, Instant updatedAt, long version) {
        return new Memory(id, familyId, type, status, title, content, relatedPersonIds, photos, createdBy,
                updatedBy, createdAt, updatedAt, version);
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

    /** {@code null} for a Memory without text, which has a photo. */
    public String content() {
        return content;
    }

    /** No duplicate, at least one (data-model.md §15). */
    public Set<UUID> relatedPersonIds() {
        return relatedPersonIds;
    }

    /** In position order, the order of addition (data-model.md §14bis). */
    public List<MemoryPhoto> photos() {
        return photos;
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
