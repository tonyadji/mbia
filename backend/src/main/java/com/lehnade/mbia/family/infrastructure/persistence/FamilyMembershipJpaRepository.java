package com.lehnade.mbia.family.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    Optional<FamilyMembershipJpaEntity> findByIdAndFamilyIdAndStatus(UUID id, UUID familyId, String status);

    List<FamilyMembershipJpaEntity> findByFamilyIdAndStatusOrderByJoinedAtAscIdAsc(UUID familyId, String status);

    /** Each row is {@code [id, display_name]}; a deleted account has no name (openapi {@code ActivityActor}). */
    @Query(nativeQuery = true, value = """
            SELECT u.id, u.display_name FROM users u
            WHERE u.id IN (:userIds) AND u.status <> 'DELETED' AND u.display_name IS NOT NULL
            """)
    List<Object[]> findDisplayNames(Collection<UUID> userIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m FROM FamilyMembershipJpaEntity m
            WHERE m.familyId = :familyId AND m.role = 'ADMIN' AND m.status = 'ACTIVE'
            ORDER BY m.id
            """)
    List<FamilyMembershipJpaEntity> lockActiveAdmins(UUID familyId);

    long countByFamilyIdAndStatus(UUID familyId, String status);

    @Query("""
            SELECT m.role FROM FamilyMembershipJpaEntity m
            WHERE m.familyId = :familyId AND m.userId = :userId AND m.status = 'ACTIVE'
            """)
    Optional<String> findActiveRole(UUID familyId, UUID userId);
}
