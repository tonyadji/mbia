package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.memory.domain.MemoryStatus;
import com.lehnade.mbia.memory.domain.MemoryType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class JpaMemoryRepository implements MemoryRepository {

    private final MemoryJpaRepository jpa;
    private final MemoryPersonJpaRepository links;

    JpaMemoryRepository(MemoryJpaRepository jpa, MemoryPersonJpaRepository links) {
        this.jpa = jpa;
        this.links = links;
    }

    @Override
    public void insert(Memory memory) {
        UUID familyId = memory.familyId();
        UUID memoryId = memory.id().value();
        jpa.saveAndFlush(new MemoryJpaEntity(memoryId, familyId, memory.type().name(), memory.status().name(),
                memory.title(), memory.content(), memory.createdBy(), memory.updatedBy(), memory.createdAt(),
                memory.updatedAt()));
        links.saveAllAndFlush(memory.relatedPersonIds().stream()
                .map(personId -> new MemoryPersonJpaEntity(familyId, memoryId, personId, memory.createdAt()))
                .toList());
    }

    @Override
    public Optional<Memory> findActiveInFamily(UUID familyId, MemoryId id) {
        return jpa.findByIdAndFamilyIdAndStatus(id.value(), familyId, MemoryStatus.ACTIVE.name())
                .map(entity -> toDomain(entity, links.findByMemoryIdAndFamilyId(entity.id(), familyId).stream()
                        .map(MemoryPersonJpaEntity::personId)
                        .collect(Collectors.toSet())));
    }

    private static Memory toDomain(MemoryJpaEntity entity, Set<UUID> relatedPersonIds) {
        return Memory.restore(new MemoryId(entity.id()), entity.familyId(), MemoryType.valueOf(entity.type()),
                MemoryStatus.valueOf(entity.status()), entity.title(), entity.content(), relatedPersonIds,
                entity.createdBy(), entity.updatedBy(), entity.createdAt(), entity.updatedAt(), entity.version());
    }
}
