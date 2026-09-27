package com.lehnade.mbia.invitation.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface InvitationJpaRepository extends JpaRepository<InvitationJpaEntity, UUID> {

    Optional<InvitationJpaEntity> findByIdAndFamilyId(UUID id, UUID familyId);

    List<InvitationJpaEntity> findByFamilyIdAndStatusOrderByCreatedAtDescIdDesc(UUID familyId, String status);

    boolean existsByPersonIdAndStatus(UUID personId, String status);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE family_invitations
            SET status = 'EXPIRED', updated_at = :now, version = version + 1
            WHERE family_id = :familyId AND status = 'PENDING' AND expires_at <= :now
            """)
    int expireDue(UUID familyId, Instant now);

    /** Each row is {@code [id, display_name, status]}. */
    @Query(nativeQuery = true, value = "SELECT id, display_name, status FROM users WHERE id IN (:userIds)")
    List<Object[]> findUsers(Collection<UUID> userIds);
}
