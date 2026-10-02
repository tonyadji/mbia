package com.lehnade.mbia.memory.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MemoryPhotoJpaRepository extends JpaRepository<MemoryPhotoJpaEntity, MemoryPhotoJpaEntity.Key> {

    List<MemoryPhotoJpaEntity> findByFamilyIdAndMemoryIdIn(UUID familyId, Collection<UUID> memoryIds);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from MemoryPhotoJpaEntity p"
            + " where p.memoryId = :memoryId and p.familyId = :familyId and p.mediaAssetId in :mediaAssetIds")
    void deletePhotos(@Param("memoryId") UUID memoryId, @Param("familyId") UUID familyId,
            @Param("mediaAssetIds") Collection<UUID> mediaAssetIds);

    /** The caption and taken date of a photo; its position never changes (data-model.md §14bis). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(nativeQuery = true, value = """
            UPDATE memory_photos
            SET caption = :caption, taken_date = :takenDate, taken_year = :takenYear,
                taken_date_precision = :takenDatePrecision
            WHERE memory_id = :memoryId AND family_id = :familyId AND media_asset_id = :mediaAssetId
            """)
    int describe(@Param("memoryId") UUID memoryId, @Param("familyId") UUID familyId,
            @Param("mediaAssetId") UUID mediaAssetId, @Param("caption") String caption,
            @Param("takenDate") LocalDate takenDate, @Param("takenYear") Integer takenYear,
            @Param("takenDatePrecision") String takenDatePrecision);
}
