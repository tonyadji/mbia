package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.genealogy.domain.RelationshipWarning;
import com.lehnade.mbia.genealogy.domain.RelationshipWarnings;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.List;
import java.util.UUID;

/**
 * The graph-wide blocks of person-relationships-collaboration.md §7, shared by the creation and
 * the restoration of a relationship (§8, genealogy.md §7): both Persons ACTIVE, no identical ACTIVE
 * relationship, no parental cycle. The caller holds the Family graph lock.
 */
public final class RelationshipRules {

    private final PersonRepository persons;
    private final RelationshipRepository relationships;
    private final ParentalCycleCheck cycleCheck;

    public RelationshipRules(PersonRepository persons, RelationshipRepository relationships,
            ParentalCycleCheck cycleCheck) {
        this.persons = persons;
        this.relationships = relationships;
        this.cycleCheck = cycleCheck;
    }

    /**
     * @return the date warnings of the relationship, from the current birth data (§7.1)
     * @throws DomainException {@code PERSON_NOT_FOUND}, {@code PERSON_NOT_ACTIVE},
     *     {@code RELATIONSHIP_ALREADY_EXISTS} or {@code RELATIONSHIP_CREATES_CYCLE}
     */
    public List<RelationshipWarning> check(FamilyRelationship relationship) {
        UUID familyId = relationship.familyId();
        Person source = activePerson(familyId, relationship.source());
        Person target = activePerson(familyId, relationship.target());

        if (relationships.existsActive(familyId, relationship.type(), relationship.source(), relationship.target())) {
            throw new DomainException(ErrorCode.RELATIONSHIP_ALREADY_EXISTS, "These people are already linked.");
        }
        if (relationship.type() != RelationshipType.PARENT_OF) {
            return List.of();
        }
        if (cycleCheck.wouldCreateCycle(familyId, relationship.source(), relationship.target())) {
            throw new DomainException(ErrorCode.RELATIONSHIP_CREATES_CYCLE,
                    "This link would make a person one of their own ancestors.");
        }
        return RelationshipWarnings.forParentOf(source.details().birth(), target.details().birth());
    }

    private Person activePerson(UUID familyId, PersonId id) {
        Person person = persons.findInFamily(familyId, id).orElseThrow(PersonNotFound::exception);
        if (!person.isActive()) {
            throw new DomainException(ErrorCode.PERSON_NOT_ACTIVE,
                    "An archived or merged person cannot be linked.");
        }
        return person;
    }
}
