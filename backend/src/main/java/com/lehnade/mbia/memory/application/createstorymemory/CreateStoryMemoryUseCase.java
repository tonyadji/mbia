package com.lehnade.mbia.memory.application.createstorymemory;

import com.lehnade.mbia.activity.application.Activity;
import com.lehnade.mbia.activity.application.ActivityLog;
import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.RelatedPerson;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.memory.application.MemoryAuthors;
import com.lehnade.mbia.memory.application.MemoryPhotoViews;
import com.lehnade.mbia.memory.application.MemoryPhotos;
import com.lehnade.mbia.memory.application.MemorySettings;
import com.lehnade.mbia.memory.application.MemoryView;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryPhoto;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a family story linked to one or more Persons, with 0 to a few photos (openapi
 * {@code createStoryMemory}; mvp.md §17; person-relationships-collaboration.md §12). ADMIN or
 * CONTRIBUTOR only. Every related Person is of the Family, not MERGED and ACTIVE (OQ-035, OQ-037).
 * Each photo is a READY {@code MEMORY_PHOTO} of the Family, uploaded by the caller and used nowhere
 * else, and there are no more than {@code mbia.memory.max-photos} (data-model.md §14bis, OQ-036,
 * OQ-042).
 *
 * <p>The Memory, its Persons, its photos and the audit entry are written in one transaction
 * (technical-specification.md §14); the audit holds the photo asset ids, never the title, the text
 * nor a caption (OQ-039, data-model.md §17).
 */
@Service
public class CreateStoryMemoryUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final RelatedPersons relatedPersons;
    private final MemoryRepository memories;
    private final MemoryPhotos memoryPhotos;
    private final MemoryPhotoViews photoViews;
    private final MemorySettings settings;
    private final AuditLog auditLog;
    private final ActivityLog activityLog;
    private final Clock clock;

    public CreateStoryMemoryUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            RelatedPersons relatedPersons, MemoryRepository memories, MemoryPhotos memoryPhotos,
            MemoryPhotoViews photoViews, MemorySettings settings, AuditLog auditLog, ActivityLog activityLog,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.relatedPersons = relatedPersons;
        this.memories = memories;
        this.memoryPhotos = memoryPhotos;
        this.photoViews = photoViews;
        this.settings = settings;
        this.auditLog = auditLog;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    @Transactional
    public MemoryView create(CreateStoryMemoryCommand command) {
        CurrentUser caller = currentUserAccessor.currentUser();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);

        Instant now = clock.instant();
        Memory memory = Memory.createStory(MemoryId.newId(), command.familyId(), command.title(),
                command.content(), command.relatedPersonIds(), command.photos(), caller.id(), now);
        if (memory.photos().size() > settings.maxPhotos()) {
            throw MemoryPhotos.limitReached(settings.maxPhotos());
        }
        List<RelatedPerson> persons = relatedPersons.requireLinkable(memory.familyId(), memory.relatedPersonIds());
        memoryPhotos.requireAttachable(memory.familyId(),
                memory.photos().stream().map(MemoryPhoto::mediaAssetId).toList(), caller.id());

        memories.insert(memory);
        List<UUID> photoIds = memory.photos().stream().map(photo -> photo.mediaAssetId().value()).toList();
        auditLog.append(new AuditEntry(memory.familyId(), caller.id(), "MEMORY_CREATED", AuditEntry.MEMORY,
                memory.id().value(), Map.of(), Map.of("type", memory.type().name(),
                        "relatedPersonIds", persons.stream().map(RelatedPerson::id).toList(),
                        "photos", photoIds),
                now));
        activityLog.record(Activity.memoryCreated(memory.familyId(), caller.id(), memory.id().value(),
                memory.title(), now));
        return MemoryView.of(memory, persons, new MemoryAuthors.Author(caller.id(), caller.displayName(), false),
                photoViews.of(memory));
    }
}
