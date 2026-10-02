package com.lehnade.mbia.family.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FamilyJpaRepository extends JpaRepository<FamilyJpaEntity, UUID> {

    /** Families where the user's membership is ACTIVE, with that membership's role and the ACTIVE member count. */
    @Query("""
            SELECT new com.lehnade.mbia.family.infrastructure.persistence.MyFamilyRow(
                f,
                m.role,
                (SELECT count(a) FROM FamilyMembershipJpaEntity a
                 WHERE a.familyId = f.id AND a.status = 'ACTIVE'))
            FROM FamilyJpaEntity f
            JOIN FamilyMembershipJpaEntity m ON m.familyId = f.id
            WHERE m.userId = :userId AND m.status = 'ACTIVE'
            ORDER BY f.createdAt, f.id
            """)
    List<MyFamilyRow> findActiveFor(UUID userId);
}
