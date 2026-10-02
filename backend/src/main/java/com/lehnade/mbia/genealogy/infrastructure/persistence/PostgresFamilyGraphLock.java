package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code pg_advisory_xact_lock}: held until the caller's transaction ends. Without it, two
 * concurrent {@code PARENT_OF} creations (A→B and B→A) would both pass the cycle check under READ
 * COMMITTED. A hash collision between two Families only serialises them, never breaks a rule.
 */
@Component
class PostgresFamilyGraphLock implements FamilyGraphLock {

    private final FamilyRelationshipJpaRepository jpa;

    PostgresFamilyGraphLock(FamilyRelationshipJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID familyId) {
        jpa.lockFamilyGraph(familyId);
    }
}
