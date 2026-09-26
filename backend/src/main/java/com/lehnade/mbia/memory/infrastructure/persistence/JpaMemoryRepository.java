package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.memory.domain.MemoryStatus;
import com.lehnade.mbia.memory.domain.MemoryType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
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

    @Override
    public List<Memory> findActiveForPerson(UUID familyId, UUID personId, int page, int size) {
        return withPersons(familyId, jpa.findActiveForPerson(familyId, personId, PageRequest.of(page, size)));
    }

    @Override
    public long countActiveForPerson(UUID familyId, UUID personId) {
        return jpa.countActiveForPerson(familyId, personId);
    }

    @Override
    public List<Memory> findActiveInFamily(UUID familyId, Optional<MemoryType> type, int page, int size) {
        return withPersons(familyId, jpa.findActiveInFamily(familyId, type.map(Enum::name).orElse(null),
                PageRequest.of(page, size)));
    }

    @Override
    public long countActiveInFamily(UUID familyId, Optional<MemoryType> type) {
        return jpa.countActiveInFamily(familyId, type.map(Enum::name).orElse(null));
    }

    /** The Memories with their Persons, read for the whole page in one query. */
    private List<Memory> withPersons(UUID familyId, List<MemoryJpaEntity> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<UUID>> personsByMemory = links
                .findByFamilyIdAndMemoryIdIn(familyId, found.stream().map(MemoryJpaEntity::id).toList()).stream()
                .collect(Collectors.groupingBy(MemoryPersonJpaEntity::memoryId,
                        Collectors.mapping(MemoryPersonJpaEntity::personId, Collectors.toSet())));
        return found.stream()
                .map(entity -> toDomain(entity, personsByMemory.getOrDefault(entity.id(), Set.of())))
                .toList();
    }

    private static Memory toDomain(MemoryJpaEntity entity, Set<UUID> relatedPersonIds) {
        return Memory.restore(new MemoryId(entity.id()), entity.familyId(), MemoryType.valueOf(entity.type()),
                MemoryStatus.valueOf(entity.status()), entity.title(), entity.content(), relatedPersonIds,
                entity.createdBy(), entity.updatedBy(), entity.createdAt(), entity.updatedAt(), entity.version());
    }
}
