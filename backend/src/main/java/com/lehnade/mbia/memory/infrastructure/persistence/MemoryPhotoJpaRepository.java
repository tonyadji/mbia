package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemoryPhotoJpaRepository extends JpaRepository<MemoryPhotoJpaEntity, MemoryPhotoJpaEntity.Key> {

    List<MemoryPhotoJpaEntity> findByFamilyIdAndMemoryIdIn(UUID familyId, Collection<UUID> memoryIds);
}
