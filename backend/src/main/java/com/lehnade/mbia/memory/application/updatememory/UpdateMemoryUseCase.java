package com.lehnade.mbia.memory.application.updatememory;

import com.lehnade.mbia.genealogy.application.RelatedPerson;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.memory.application.MemoryAuthors;
import com.lehnade.mbia.memory.application.MemoryEditors;
import com.lehnade.mbia.memory.application.MemoryPhotoViews;
import com.lehnade.mbia.memory.application.MemoryPhotos;
import com.lehnade.mbia.memory.application.MemorySettings;
import com.lehnade.mbia.memory.application.MemoryView;
import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryPhoto;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.memory.domain.MemoryType;
import com.lehnade.mbia.memory.domain.PartialDay;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the title, text, date, Persons or photos of a story, from its current version (openapi
 * {@code updateMemory}, SCREEN-014, technical-specification.md §13). Its creator or an ADMIN, with
 * a role that can write (mvp.md §17, OQ-041). A field of a photo Memory is refused (OQ-037). When
 * the Persons change, every Person newly added is ACTIVE and at least one Person stays ACTIVE,
 * while an archived Person already linked may stay (OQ-035, OQ-043). A request that changes
 * nothing writes nothing and keeps the version (OQ-008).
 *
 * <p>A photo added follows the rules of a new Memory, and an addition may not leave more photos
 * than {@code mbia.memory.max-photos}; a Memory above a lowered limit may still change without
 * gaining a photo. A removed photo's asset becomes ARCHIVED (data-model.md §13, §14bis, OQ-042).
 * The Memory, its photos, their assets and the audit are written in one transaction.
 *
 * <p>Each changed field is audited on its own; the title, the text and the captions are never
 * copied into the audit, only the fact that they changed (OQ-039, OQ-042, data-model.md §17). The
 * date is, before and after, as a Person's birth: a date is not a family text.
 */
@Service
public class UpdateMemoryUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final MemoryEditors editors;
    private final MemoryRepository memories;
    private final RelatedPersons relatedPersons;
    private final MemoryAuthors authors;
    private final MemoryPhotoViews photoViews;
    private final MemoryPhotos memoryPhotos;
    private final MemorySettings settings;
    private final AuditLog auditLog;
    private final Clock clock;

    public UpdateMemoryUseCase(CurrentUserAccessor currentUserAccessor, MemoryEditors editors,
            MemoryRepository memories, RelatedPersons relatedPersons, MemoryAuthors authors,
            MemoryPhotoViews photoViews, MemoryPhotos memoryPhotos, MemorySettings settings, AuditLog auditLog,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.editors = editors;
        this.memories = memories;
        this.relatedPersons = relatedPersons;
        this.authors = authors;
        this.photoViews = photoViews;
        this.memoryPhotos = memoryPhotos;
        this.settings = settings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public MemoryView update(UpdateMemoryCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        Memory memory = editors.requireEditable(command.familyId(), command.memoryId(), callerId);
        if (memory.type() == MemoryType.STORY && !command.photoFields().isEmpty()) {
            String field = command.photoFields().stream().sorted().findFirst().orElseThrow();
            throw new FieldValidationException(field, "NOT_ALLOWED", "A story has no " + field + ".");
        }
        Versions.requireCurrent(command.expectedVersion(), memory.version());

        Instant now = clock.instant();
        Memory changed = memory.updateStory(command.title(), command.content(), command.happenedAt(),
                command.relatedPersonIds(), command.photos(), callerId, now);
        if (changed == memory) {
            return view(memory);
        }
        List<MediaAssetId> added = photosNotIn(changed, memory);
        if (!added.isEmpty() && changed.photos().size() > settings.maxPhotos()) {
            throw MemoryPhotos.limitReached(settings.maxPhotos());
        }
        boolean personsChanged = !changed.relatedPersonIds().equals(memory.relatedPersonIds());
        Collection<RelatedPerson> persons = personsChanged
                ? requireRelinkable(memory, changed)
                : relatedPersons.describe(memory.familyId(), memory.relatedPersonIds()).values();

        memoryPhotos.requireAttachable(memory.familyId(), added, callerId);

        Memory updated = memories.update(changed);
        memoryPhotos.archive(memory.familyId(), photosNotIn(memory, changed), now);
        audit(memory, updated, callerId, now);
        return MemoryView.of(updated, persons, authors.author(updated.createdBy()), photoViews.of(updated));
    }

    /** The new Persons, when they change: at least one stays ACTIVE (OQ-035, OQ-043). */
    private List<RelatedPerson> requireRelinkable(Memory memory, Memory changed) {
        List<RelatedPerson> persons = relatedPersons.requireRelinkable(memory.familyId(),
                memory.relatedPersonIds(), changed.relatedPersonIds());
        if (persons.stream().noneMatch(person -> person.status() == RelatedPerson.Status.ACTIVE)) {
            throw new FieldValidationException("relatedPersonIds", "ACTIVE_PERSON_REQUIRED",
                    "A memory must stay linked to at least one active person.");
        }
        return persons;
    }

    private void audit(Memory before, Memory after, UUID callerId, Instant now) {
        for (String field : List.of("title", "content")) {
            boolean changed = field.equals("title")
                    ? !before.title().equals(after.title())
                    : !Objects.equals(before.content(), after.content());
            if (changed) {
                // Only the name of the field: family texts are never copied into the audit.
                append(after, callerId, Map.of(), Map.of("field", field), now);
            }
        }
        if (!before.happenedAt().equals(after.happenedAt())) {
            append(after, callerId, Map.of(Memory.HAPPENED_AT, describe(before.happenedAt())),
                    Map.of(Memory.HAPPENED_AT, describe(after.happenedAt())), now);
        }
        if (!before.relatedPersonIds().equals(after.relatedPersonIds())) {
            append(after, callerId, Map.of("relatedPersonIds", sorted(before)),
                    Map.of("relatedPersonIds", sorted(after)), now);
        }
        List<UUID> photosBefore = photoIds(before.photos());
        List<UUID> photosAfter = photoIds(after.photos());
        if (!Set.copyOf(photosBefore).equals(Set.copyOf(photosAfter))) {
            append(after, callerId, Map.of("photos", photosBefore), Map.of("photos", photosAfter), now);
        }
        Map<MediaAssetId, MemoryPhoto> kept = before.photos().stream()
                .collect(Collectors.toMap(MemoryPhoto::mediaAssetId, photo -> photo));
        List<MemoryPhoto> described = after.photos().stream()
                .filter(photo -> kept.containsKey(photo.mediaAssetId())
                        && !kept.get(photo.mediaAssetId()).equals(photo))
                .toList();
        if (!described.isEmpty()) {
            // Only the asset ids: captions are family texts, never copied into the audit.
            append(after, callerId, Map.of(),
                    Map.of("field", "photoDetails", "mediaAssetIds", photoIds(described)), now);
        }
    }

    /** As a Person's birth in its audit: the ISO date, the year, or {@code UNKNOWN}. */
    private static String describe(PartialDay date) {
        return switch (date.precision()) {
            case EXACT -> date.date().toString();
            case YEAR_ONLY -> date.year().toString();
            case UNKNOWN -> "UNKNOWN";
        };
    }

    /** The photos of {@code memory} that {@code other} does not have, in position order. */
    private static List<MediaAssetId> photosNotIn(Memory memory, Memory other) {
        Set<MediaAssetId> others = other.photos().stream()
                .map(MemoryPhoto::mediaAssetId)
                .collect(Collectors.toSet());
        return memory.photos().stream()
                .map(MemoryPhoto::mediaAssetId)
                .filter(id -> !others.contains(id))
                .toList();
    }

    /** In position order, as in {@code MEMORY_CREATED}. */
    private static List<UUID> photoIds(List<MemoryPhoto> photos) {
        return photos.stream().map(photo -> photo.mediaAssetId().value()).toList();
    }

    private void append(Memory memory, UUID callerId, Map<String, Object> oldValue, Map<String, Object> newValue,
            Instant now) {
        auditLog.append(new AuditEntry(memory.familyId(), callerId, "MEMORY_UPDATED", AuditEntry.MEMORY,
                memory.id().value(), oldValue, newValue, now));
    }

    /**
     * In text order, which is PostgreSQL's {@code uuid} order and that of {@code MEMORY_CREATED}.
     * Not {@link UUID#compareTo}: it compares signed numbers and puts {@code 8…}–{@code f…} first.
     */
    private static List<UUID> sorted(Memory memory) {
        return memory.relatedPersonIds().stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private MemoryView view(Memory memory) {
        return MemoryView.of(memory, relatedPersons.describe(memory.familyId(), memory.relatedPersonIds()).values(),
                authors.author(memory.createdBy()), photoViews.of(memory));
    }
}
