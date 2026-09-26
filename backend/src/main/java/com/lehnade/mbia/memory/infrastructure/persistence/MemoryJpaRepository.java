package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MemoryJpaRepository extends JpaRepository<MemoryJpaEntity, UUID> {

    Optional<MemoryJpaEntity> findByIdAndFamilyIdAndStatus(UUID id, UUID familyId, String status);

    @Query("""
            SELECT new com.lehnade.mbia.memory.infrastructure.persistence.MemoryCountRow(m.familyId, COUNT(m))
            FROM MemoryJpaEntity m
            WHERE m.familyId IN :familyIds AND m.status = 'ACTIVE'
            GROUP BY m.familyId
            """)
    List<MemoryCountRow> countActiveByFamily(Collection<UUID> familyIds);

    /** Each row is {@code [display_name, status]}. */
    @Query(nativeQuery = true, value = "SELECT display_name, status FROM users WHERE id = :userId")
    List<Object[]> findAuthor(UUID userId);
}
