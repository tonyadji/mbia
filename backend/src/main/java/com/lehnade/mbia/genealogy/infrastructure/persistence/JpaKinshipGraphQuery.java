package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.KinshipGraph;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The Family's usable relations in one query; the breadth-first search then runs in memory over
 * this one Family only (genealogy.md §9, §15).
 */
@Component
class JpaKinshipGraphQuery implements KinshipGraphQuery {

    private final FamilyRelationshipJpaRepository jpa;

    JpaKinshipGraphQuery(FamilyRelationshipJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public KinshipGraph activeGraph(UUID familyId) {
        return KinshipGraph.of(jpa.findKinshipEdges(familyId).stream()
                .map(row -> new KinshipGraph.Edge(RelationshipType.valueOf(row.type()),
                        new PersonId(row.sourcePersonId()), new PersonId(row.targetPersonId())))
                .toList());
    }
}
