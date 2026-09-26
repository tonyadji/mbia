package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Recursive CTE over ACTIVE {@code PARENT_OF} relations (genealogy.md §7). */
@Component
class JpaParentalCycleCheck implements ParentalCycleCheck {

    private final FamilyRelationshipJpaRepository jpa;

    JpaParentalCycleCheck(FamilyRelationshipJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@code parent PARENT_OF child} closes a cycle when {@code parent} descends from {@code child}. */
    @Override
    public boolean wouldCreateCycle(UUID familyId, PersonId parent, PersonId child) {
        return jpa.isDescendant(familyId, child.value(), parent.value());
    }

    /** A Person is on a cycle when they descend from themselves. */
    @Override
    public boolean isOnCycle(UUID familyId, PersonId person) {
        return jpa.isDescendant(familyId, person.value(), person.value());
    }
}
