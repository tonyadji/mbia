package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.ArchivedRelationship;
import com.lehnade.mbia.genealogy.domain.ArchivedRelationshipsQuery;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The removed relationships of a Person and the other Persons in one query (genealogy.md §11bis). */
@Component
class JpaArchivedRelationshipsQuery implements ArchivedRelationshipsQuery {

    private final FamilyRelationshipJpaRepository relationships;

    JpaArchivedRelationshipsQuery(FamilyRelationshipJpaRepository relationships) {
        this.relationships = relationships;
    }

    @Override
    public List<ArchivedRelationship> of(UUID familyId, PersonId person) {
        return relationships.findArchivedWithOtherPerson(familyId, person.value()).stream()
                .map(row -> new ArchivedRelationship(
                        JpaRelationshipRepository.toDomain((FamilyRelationshipJpaEntity) row[0]),
                        JpaPersonRepository.toDomain((PersonJpaEntity) row[1])))
                .toList();
    }
}
