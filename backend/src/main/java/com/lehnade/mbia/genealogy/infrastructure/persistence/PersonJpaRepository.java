package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {

    Optional<PersonJpaEntity> findByIdAndFamilyId(UUID id, UUID familyId);

    boolean existsByFamilyIdAndLinkedUserIdAndStatusNot(UUID familyId, UUID linkedUserId, String status);

    /** At most one row: {@code uq_person_linked_user_per_family} (data-model.md §21). */
    Optional<PersonJpaEntity> findByFamilyIdAndLinkedUserIdAndStatusNot(UUID familyId, UUID linkedUserId,
            String status);

    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.PersonCountRow(p.familyId, COUNT(p))
            FROM PersonJpaEntity p
            WHERE p.familyId IN :familyIds AND p.status = 'ACTIVE'
            GROUP BY p.familyId
            """)
    List<PersonCountRow> countActiveByFamily(Collection<UUID> familyIds);

    /** At most one row per Family: {@code uq_person_linked_user_per_family} (data-model.md §21). */
    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.LinkedPersonRow(p.familyId, p.id)
            FROM PersonJpaEntity p
            WHERE p.linkedUserId = :userId AND p.familyId IN :familyIds AND p.status <> 'MERGED'
            """)
    List<LinkedPersonRow> findLinkedTo(UUID userId, Collection<UUID> familyIds);
}
