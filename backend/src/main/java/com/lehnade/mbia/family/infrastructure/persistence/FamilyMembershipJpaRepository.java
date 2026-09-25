package com.lehnade.mbia.family.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FamilyMembershipJpaRepository extends JpaRepository<FamilyMembershipJpaEntity, UUID> {

    long countByFamilyIdAndStatus(UUID familyId, String status);

    @Query("""
            SELECT m.role FROM FamilyMembershipJpaEntity m
            WHERE m.familyId = :familyId AND m.userId = :userId AND m.status = 'ACTIVE'
            """)
    Optional<String> findActiveRole(UUID familyId, UUID userId);
}
