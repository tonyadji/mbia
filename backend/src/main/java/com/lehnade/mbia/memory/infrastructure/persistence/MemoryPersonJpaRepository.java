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

    /** Increases the version of every Memory linked to the Person, whatever its status. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(nativeQuery = true, value = """
            UPDATE memories SET version = version + 1
            WHERE family_id = :familyId AND id IN (
                SELECT memory_id FROM memory_persons WHERE family_id = :familyId AND person_id = :personId)
            """)
    int touchMemoriesOf(@Param("familyId") UUID familyId, @Param("personId") UUID personId);

    /** Drops the source links of the Memories already linked to the target. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(nativeQuery = true, value = """
            DELETE FROM memory_persons s
            WHERE s.family_id = :familyId AND s.person_id = :sourceId AND EXISTS (
                SELECT 1 FROM memory_persons t
                WHERE t.family_id = :familyId AND t.memory_id = s.memory_id AND t.person_id = :targetId)
            """)
    int deleteLinksAlsoOn(@Param("familyId") UUID familyId, @Param("sourceId") UUID sourceId,
            @Param("targetId") UUID targetId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(nativeQuery = true, value = """
            UPDATE memory_persons SET person_id = :targetId
            WHERE family_id = :familyId AND person_id = :sourceId
            """)
    int replacePerson(@Param("familyId") UUID familyId, @Param("sourceId") UUID sourceId,
            @Param("targetId") UUID targetId);
}
