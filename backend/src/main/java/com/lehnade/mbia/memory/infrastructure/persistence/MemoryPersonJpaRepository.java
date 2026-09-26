package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MemoryPersonJpaRepository extends JpaRepository<MemoryPersonJpaEntity, MemoryPersonJpaEntity.Key> {

    List<MemoryPersonJpaEntity> findByMemoryIdAndFamilyId(UUID memoryId, UUID familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from MemoryPersonJpaEntity l"
            + " where l.memoryId = :memoryId and l.familyId = :familyId and l.personId in :personIds")
    void deleteLinks(@Param("memoryId") UUID memoryId, @Param("familyId") UUID familyId,
            @Param("personIds") Collection<UUID> personIds);

    List<MemoryPersonJpaEntity> findByFamilyIdAndMemoryIdIn(UUID familyId, Collection<UUID> memoryIds);
}
