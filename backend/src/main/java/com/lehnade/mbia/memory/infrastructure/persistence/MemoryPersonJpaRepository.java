package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemoryPersonJpaRepository extends JpaRepository<MemoryPersonJpaEntity, MemoryPersonJpaEntity.Key> {

    List<MemoryPersonJpaEntity> findByMemoryIdAndFamilyId(UUID memoryId, UUID familyId);
}
