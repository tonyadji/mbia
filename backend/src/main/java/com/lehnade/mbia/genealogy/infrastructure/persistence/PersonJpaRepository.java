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

    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.PersonCountRow(p.familyId, COUNT(p))
            FROM PersonJpaEntity p
            WHERE p.familyId IN :familyIds AND p.status = 'ACTIVE'
            GROUP BY p.familyId
            """)
    List<PersonCountRow> countActiveByFamily(Collection<UUID> familyIds);
}
