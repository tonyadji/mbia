package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may change a Memory (mvp.md §17, person-relationships-collaboration.md §12): its creator or
 * an ADMIN, both with a role that can write, so that a VIEWER stays read-only even on their own
 * Memory (OQ-041). Shared by the edit and archive use cases.
 */
@Service
public class MemoryEditors {

    private final FamilyAccess familyAccess;
    private final MemoryRepository memories;

    public MemoryEditors(FamilyAccess familyAccess, MemoryRepository memories) {
        this.familyAccess = familyAccess;
        this.memories = memories;
    }

    /**
     * @return the ACTIVE Memory, which the caller may change
     * @throws DomainException {@code FAMILY_NOT_FOUND} outside the Family; {@code PERMISSION_DENIED}
     *     for a VIEWER, or a CONTRIBUTOR on another member's Memory; {@code MEMORY_NOT_FOUND} for a
     *     Memory unknown, of another Family or ARCHIVED (OQ-037)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Memory requireEditable(UUID familyId, UUID memoryId, UUID callerId) {
        FamilyRole role = familyAccess.requireRole(familyId, FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);
        Memory memory = memories.findActiveInFamily(familyId, new MemoryId(memoryId))
                .orElseThrow(MemoryNotFound::exception);
        if (role != FamilyRole.ADMIN && !memory.isCreatedBy(callerId)) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only the member who added this memory or an administrator can change it.");
        }
        return memory;
    }
}
