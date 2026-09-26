package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MemoryJpaRepository extends JpaRepository<MemoryJpaEntity, UUID> {

    Optional<MemoryJpaEntity> findByIdAndFamilyIdAndStatus(UUID id, UUID familyId, String status);

    /** The Person's ACTIVE Memories, most recently added first, then by id (data-model.md §23.3, OQ-034). */
    @Query("""
            SELECT m FROM MemoryJpaEntity m
            JOIN MemoryPersonJpaEntity mp ON mp.memoryId = m.id AND mp.familyId = m.familyId
            WHERE mp.familyId = :familyId AND mp.personId = :personId AND m.status = 'ACTIVE'
            ORDER BY m.createdAt DESC, m.id ASC
            """)
    List<MemoryJpaEntity> findActiveForPerson(UUID familyId, UUID personId, Pageable pageable);

    @Query("""
            SELECT COUNT(m) FROM MemoryJpaEntity m
            JOIN MemoryPersonJpaEntity mp ON mp.memoryId = m.id AND mp.familyId = m.familyId
            WHERE mp.familyId = :familyId AND mp.personId = :personId AND m.status = 'ACTIVE'
            """)
    long countActiveForPerson(UUID familyId, UUID personId);

    /** The Family's ACTIVE Memories, most recently added first, then by id (data-model.md §23.4, OQ-034). */
    @Query("""
            SELECT m FROM MemoryJpaEntity m
            WHERE m.familyId = :familyId AND m.status = 'ACTIVE' AND (:type IS NULL OR m.type = :type)
            ORDER BY m.createdAt DESC, m.id ASC
            """)
    List<MemoryJpaEntity> findActiveInFamily(UUID familyId, String type, Pageable pageable);

    @Query("""
            SELECT COUNT(m) FROM MemoryJpaEntity m
            WHERE m.familyId = :familyId AND m.status = 'ACTIVE' AND (:type IS NULL OR m.type = :type)
            """)
    long countActiveInFamily(UUID familyId, String type);

    @Query("""
            SELECT new com.lehnade.mbia.memory.infrastructure.persistence.MemoryCountRow(m.familyId, COUNT(m))
            FROM MemoryJpaEntity m
            WHERE m.familyId IN :familyIds AND m.status = 'ACTIVE'
            GROUP BY m.familyId
            """)
    List<MemoryCountRow> countActiveByFamily(Collection<UUID> familyIds);

    /** Each row is {@code [id, display_name, status]}. */
    @Query(nativeQuery = true, value = "SELECT id, display_name, status FROM users WHERE id IN (:userIds)")
    List<Object[]> findAuthors(Collection<UUID> userIds);
}
