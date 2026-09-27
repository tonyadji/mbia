package com.lehnade.mbia.family.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface FamilyMembershipJpaRepository extends JpaRepository<FamilyMembershipJpaEntity, UUID> {

    Optional<FamilyMembershipJpaEntity> findByFamilyIdAndUserId(UUID familyId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM FamilyMembershipJpaEntity m WHERE m.familyId = :familyId AND m.userId = :userId")
    Optional<FamilyMembershipJpaEntity> lockByFamilyIdAndUserId(UUID familyId, UUID userId);

    long countByFamilyIdAndStatus(UUID familyId, String status);

    @Query("""
            SELECT m.role FROM FamilyMembershipJpaEntity m
            WHERE m.familyId = :familyId AND m.userId = :userId AND m.status = 'ACTIVE'
            """)
    Optional<String> findActiveRole(UUID familyId, UUID userId);
}
