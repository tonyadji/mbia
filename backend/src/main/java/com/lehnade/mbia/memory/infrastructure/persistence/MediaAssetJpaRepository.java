package com.lehnade.mbia.memory.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface MediaAssetJpaRepository extends JpaRepository<MediaAssetJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MediaAssetJpaEntity m WHERE m.id = :id AND m.familyId = :familyId")
    Optional<MediaAssetJpaEntity> lockByIdAndFamilyId(UUID id, UUID familyId);

    @Query(nativeQuery = true, value = """
            SELECT m.*
            FROM media_assets m
            WHERE m.status = 'PENDING_UPLOAD' AND m.created_at < :cutoff
            ORDER BY m.id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    List<MediaAssetJpaEntity> lockPendingCreatedBefore(Instant cutoff, int limit);

    @Query(nativeQuery = true, value = """
            SELECT m.*
            FROM media_assets m
            WHERE m.status = 'READY' AND m.ready_at < :cutoff
              AND m.id > :afterId
            ORDER BY m.id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    List<MediaAssetJpaEntity> lockReadyBefore(Instant cutoff, UUID afterId, int limit);
}
