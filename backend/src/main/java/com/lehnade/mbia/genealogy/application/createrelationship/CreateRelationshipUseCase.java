package com.lehnade.mbia.genealogy.application.createrelationship;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.RelationshipRules;
import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipWarning;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an explicit relationship between two Persons of a Family (openapi
 * {@code createRelationship}; mvp.md §8, §10; person-relationships-collaboration.md §6–7). ADMIN or
 * CONTRIBUTOR only. Hard blocks: self relation, Person unknown or of another Family, ARCHIVED or
 * MERGED Person, exact ACTIVE duplicate, parental cycle. Date warnings need the User's confirmation.
 */
@Service
public class CreateRelationshipUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final RelationshipRepository relationships;
    private final RelationshipRules rules;
    private final FamilyGraphLock graphLock;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreateRelationshipUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
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
    public CreatedRelationship create(CreateRelationshipCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        UUID familyId = command.familyId();
        familyAccess.requireRole(familyId, FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);

        Instant now = clock.instant();
        FamilyRelationship relationship = FamilyRelationship.create(RelationshipId.newId(), familyId,
                command.type(), new PersonId(command.sourcePersonId()), new PersonId(command.targetPersonId()),
                callerId, now);

        graphLock.lock(familyId);
        List<RelationshipWarning> warnings = rules.check(relationship);
        if (!warnings.isEmpty() && !command.confirmWarnings()) {
            throw new DomainException(ErrorCode.RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED,
                    "The birth dates look inconsistent; confirm to add this link anyway.",
                    Map.of("warnings", warnings.stream().map(CreateRelationshipUseCase::describe).toList()));
        }

        relationships.insert(relationship);
        auditLog.append(new AuditEntry(familyId, callerId, "RELATIONSHIP_CREATED", AuditEntry.RELATIONSHIP,
                relationship.id().value(), Map.of(), Map.of("type", relationship.type().name(),
                        "sourcePersonId", relationship.source().value(),
                        "targetPersonId", relationship.target().value()), now));
        return new CreatedRelationship(relationship, warnings);
    }

    /** The {@code RelationshipWarning} shape of the contract, for {@code details.warnings}. */
    private static Map<String, Object> describe(RelationshipWarning warning) {
        return Map.of("code", warning.code().name(), "context", Map.of(
                "parentBirthYear", warning.parentBirthYear(), "childBirthYear", warning.childBirthYear()));
    }
}
