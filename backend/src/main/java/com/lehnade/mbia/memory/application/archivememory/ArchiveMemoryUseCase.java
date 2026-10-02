package com.lehnade.mbia.memory.application.archivememory;

import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.memory.application.MemoryEditors;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.memory.domain.MemoryStatus;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archives a Memory, from its current version (openapi {@code archiveMemory}, SCREEN-013). Its
 * creator or an ADMIN, with a role that can write (mvp.md §17, OQ-041). The Memory is then hidden
 * everywhere and answers 404 {@code MEMORY_NOT_FOUND}; its row and Person associations stay, so
 * that support can restore it. Audited as {@code MEMORY_ARCHIVED} (OQ-039).
 */
@Service
public class ArchiveMemoryUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final MemoryEditors editors;
    private final MemoryRepository memories;
    private final AuditLog auditLog;
    private final Clock clock;

    public ArchiveMemoryUseCase(CurrentUserAccessor currentUserAccessor, MemoryEditors editors,
            MemoryRepository memories, AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.editors = editors;
        this.memories = memories;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void archive(ArchiveMemoryCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        Memory memory = editors.requireEditable(command.familyId(), command.memoryId(), callerId);
        Versions.requireCurrent(command.expectedVersion(), memory.version());

        Instant now = clock.instant();
        Memory archived = memories.update(memory.archive(callerId, now));
        auditLog.append(new AuditEntry(archived.familyId(), callerId, "MEMORY_ARCHIVED", AuditEntry.MEMORY,
                archived.id().value(), Map.of("status", MemoryStatus.ACTIVE.name()),
                Map.of("status", MemoryStatus.ARCHIVED.name()), now));
    }
}
