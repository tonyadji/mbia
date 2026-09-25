package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Kinship;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.Person;
import org.springframework.stereotype.Component;

/**
 * What {@code to} is to {@code from} (genealogy.md §9), for use cases that already checked the
 * caller's access and loaded both Persons of the same Family.
 */
@Component
public class KinshipResolver {

    private final KinshipGraphQuery graphQuery;

    public KinshipResolver(KinshipGraphQuery graphQuery) {
        this.graphQuery = graphQuery;
    }

    /**
     * Paths use ACTIVE Persons only, so an ARCHIVED or MERGED Person has no known kinship
     * (OQ-013), except with itself.
     */
    public Kinship resolve(Person from, Person to) {
        if (from.id().equals(to.id())) {
            return Kinship.self();
        }
        if (!from.isActive() || !to.isActive()) {
            return Kinship.noneKnown();
        }
        return graphQuery.activeGraph(from.familyId()).kinship(from.id(), to.id(), to.details().gender());
    }
}
