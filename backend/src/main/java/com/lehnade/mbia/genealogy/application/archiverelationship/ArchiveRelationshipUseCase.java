package com.lehnade.mbia.genealogy.application.archiverelationship;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.RelationshipNotFound;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipStatus;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
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
 * Removes a relationship: ACTIVE → ARCHIVED, never deleted (openapi {@code archiveRelationship};
 * mvp.md §13; person-relationships-collaboration.md §8; data-model.md §20). ADMIN or CONTRIBUTOR,
 * from the current version. Derived kinship is computed, so it changes with the next read. A
 * relationship already removed stays as it is (OQ-020).
 */
@Service
public class ArchiveRelationshipUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final RelationshipRepository relationships;
    private final AuditLog auditLog;
    private final Clock clock;

    public ArchiveRelationshipUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            RelationshipRepository relationships, AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.relationships = relationships;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void archive(ArchiveRelationshipCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);
        FamilyRelationship relationship = relationships
                .findInFamily(command.familyId(), new RelationshipId(command.relationshipId()))
                .orElseThrow(RelationshipNotFound::exception);
        Versions.requireCurrent(command.expectedVersion(), relationship.version());
        if (!relationship.isActive()) {
            return;
        }

        Instant now = clock.instant();
        FamilyRelationship archived = relationships.update(relationship.archive(callerId, now));
        auditLog.append(new AuditEntry(archived.familyId(), callerId, "RELATIONSHIP_ARCHIVED",
                AuditEntry.RELATIONSHIP, archived.id().value(),
                Map.of("status", RelationshipStatus.ACTIVE.name()),
                Map.of("status", RelationshipStatus.ARCHIVED.name()), now));
    }
}
