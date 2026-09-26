package com.lehnade.mbia.memory.application.createstorymemory;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.RelatedPerson;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.memory.application.MemoryAuthors;
import com.lehnade.mbia.memory.application.MemoryView;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a family story linked to one or more Persons (openapi {@code createStoryMemory}; mvp.md
 * §17; person-relationships-collaboration.md §12). ADMIN or CONTRIBUTOR only. Every related Person
 * is of the Family, not MERGED and ACTIVE (OQ-035, OQ-037). The Memory, its Persons and the audit
 * entry are written in one transaction; the audit never holds the title or the text (OQ-039).
 */
@Service
public class CreateStoryMemoryUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final RelatedPersons relatedPersons;
    private final MemoryRepository memories;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreateStoryMemoryUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            RelatedPersons relatedPersons, MemoryRepository memories, AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.relatedPersons = relatedPersons;
        this.memories = memories;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public MemoryView create(CreateStoryMemoryCommand command) {
        CurrentUser caller = currentUserAccessor.currentUser();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);

        Instant now = clock.instant();
        Memory memory = Memory.createStory(MemoryId.newId(), command.familyId(), command.title(),
                command.content(), command.relatedPersonIds(), caller.id(), now);
        List<RelatedPerson> persons = relatedPersons.requireLinkable(memory.familyId(), memory.relatedPersonIds());

        memories.insert(memory);
        auditLog.append(new AuditEntry(memory.familyId(), caller.id(), "MEMORY_CREATED", AuditEntry.MEMORY,
                memory.id().value(), Map.of(), Map.of("type", memory.type().name(),
                        "relatedPersonIds", persons.stream().map(RelatedPerson::id).toList()),
                now));
        return MemoryView.of(memory, persons, new MemoryAuthors.Author(caller.id(), caller.displayName(), false));
    }
}
