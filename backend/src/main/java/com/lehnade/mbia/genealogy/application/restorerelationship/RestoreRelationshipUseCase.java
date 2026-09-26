package com.lehnade.mbia.genealogy.application.restorerelationship;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.RelationshipNotFound;
import com.lehnade.mbia.genealogy.application.RelationshipRules;
import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipWarning;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores a removed relationship: ARCHIVED → ACTIVE (openapi {@code restoreRelationship}; mvp.md
 * §13; person-relationships-collaboration.md §8). ADMIN only, from the current version. Every
 * current block of §7 is re-run under the Family graph lock (Persons ACTIVE, no identical ACTIVE
 * relationship, no parental cycle); date warnings are returned without blocking (OQ-021). A
 * relationship already ACTIVE stays as it is (OQ-020).
 */
@Service
public class RestoreRelationshipUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final RelationshipRepository relationships;
    private final RelationshipRules rules;
    private final FamilyGraphLock graphLock;
    private final AuditLog auditLog;
    private final Clock clock;

    public RestoreRelationshipUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, RelationshipRepository relationships, ParentalCycleCheck cycleCheck,
            FamilyGraphLock graphLock, AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.relationships = relationships;
        this.rules = new RelationshipRules(persons, relationships, cycleCheck);
        this.graphLock = graphLock;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public RestoredRelationship restore(RestoreRelationshipCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        FamilyRelationship relationship = relationships
                .findInFamily(command.familyId(), new RelationshipId(command.relationshipId()))
                .orElseThrow(RelationshipNotFound::exception);
        Versions.requireCurrent(command.expectedVersion(), relationship.version());
        if (relationship.isActive()) {
            return new RestoredRelationship(relationship, List.of());
        }

        graphLock.lock(relationship.familyId());
        List<RelationshipWarning> warnings = rules.check(relationship);
        Instant now = clock.instant();
        FamilyRelationship restored = relationships.update(relationship.unarchive(callerId, now));
        auditLog.append(new AuditEntry(restored.familyId(), callerId, "RELATIONSHIP_RESTORED",
                AuditEntry.RELATIONSHIP, restored.id().value(),
                Map.of("status", RelationshipStatus.ARCHIVED.name()),
                Map.of("status", RelationshipStatus.ACTIVE.name()), now));
        return new RestoredRelationship(restored, warnings);
    }
}
