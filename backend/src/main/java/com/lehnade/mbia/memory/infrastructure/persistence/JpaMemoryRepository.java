package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.domain.MediaAssetId;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryPhoto;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.memory.domain.MemoryStatus;
import com.lehnade.mbia.memory.domain.MemoryType;
import com.lehnade.mbia.memory.domain.TakenDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaMemoryRepository implements MemoryRepository {

    private final MemoryJpaRepository jpa;
    private final MemoryPersonJpaRepository links;
    private final MemoryPhotoJpaRepository photos;

    JpaMemoryRepository(MemoryJpaRepository jpa, MemoryPersonJpaRepository links, MemoryPhotoJpaRepository photos) {
        this.jpa = jpa;
        this.links = links;
        this.photos = photos;
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
        photos.saveAllAndFlush(memory.photos().stream()
                .map(photo -> new MemoryPhotoJpaEntity(familyId, memoryId, photo.mediaAssetId().value(),
                        photo.position(), photo.caption(), photo.takenAt().date(), photo.takenAt().year(),
                        photo.takenAt().precision().name(), memory.createdAt()))
                .toList());
    }

    /**
     * Hibernate writes {@code UPDATE … WHERE version = ?} (JPA {@code @Version}): a commit by
     * another transaction since the Memory was read fails the flush instead of being overwritten.
     */
    @Override
    public Memory update(Memory memory) {
        UUID familyId = memory.familyId();
        UUID memoryId = memory.id().value();
        MemoryJpaEntity entity = jpa.findById(memoryId)
                .filter(found -> found.familyId().equals(familyId) && found.version() == memory.version())
                .orElseThrow(() -> new OptimisticLockingFailureException("The memory changed since it was read."));
        entity.changeStory(memory.title(), memory.content(), memory.updatedBy(), memory.updatedAt());
        if (memory.status() == MemoryStatus.ARCHIVED) {
            entity.archive(memory.updatedBy(), memory.updatedAt());
        }
        Set<UUID> linked = links.findByMemoryIdAndFamilyId(memoryId, familyId).stream()
                .map(MemoryPersonJpaEntity::personId)
                .collect(Collectors.toSet());
        Set<UUID> removed = linked.stream()
                .filter(personId -> !memory.relatedPersonIds().contains(personId))
                .collect(Collectors.toSet());
        if (!removed.isEmpty()) {
            links.deleteLinks(memoryId, familyId, removed);
        }
        links.saveAllAndFlush(memory.relatedPersonIds().stream()
                .filter(personId -> !linked.contains(personId))
                .map(personId -> new MemoryPersonJpaEntity(familyId, memoryId, personId, memory.updatedAt()))
                .toList());
        return toDomain(jpa.saveAndFlush(entity), memory.relatedPersonIds(), memory.photos());
    }

    @Override
    public Optional<Memory> findActiveInFamily(UUID familyId, MemoryId id) {
        return jpa.findByIdAndFamilyIdAndStatus(id.value(), familyId, MemoryStatus.ACTIVE.name())
                .map(entity -> withPersonsAndPhotos(familyId, List.of(entity)).getFirst());
    }

    @Override
    public List<Memory> findActiveForPerson(UUID familyId, UUID personId, int page, int size) {
        return withPersonsAndPhotos(familyId, jpa.findActiveForPerson(familyId, personId, PageRequest.of(page, size)));
    }

    @Override
    public long countActiveForPerson(UUID familyId, UUID personId) {
        return jpa.countActiveForPerson(familyId, personId);
    }

    @Override
    public List<Memory> findActiveInFamily(UUID familyId, Optional<MemoryType> type, int page, int size) {
        return withPersonsAndPhotos(familyId, jpa.findActiveInFamily(familyId, type.map(Enum::name).orElse(null),
                PageRequest.of(page, size)));
    }

    @Override
    public long countActiveInFamily(UUID familyId, Optional<MemoryType> type) {
        return jpa.countActiveInFamily(familyId, type.map(Enum::name).orElse(null));
    }

    @Override
    public Set<UUID> findPhotosAmong(Collection<UUID> mediaAssetIds) {
        if (mediaAssetIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jpa.findPhotosAmong(mediaAssetIds));
    }

    /** The Memories with their Persons and their photos, read for the whole page in one query each. */
    private List<Memory> withPersonsAndPhotos(UUID familyId, List<MemoryJpaEntity> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(MemoryJpaEntity::id).toList();
        Map<UUID, Set<UUID>> personsByMemory = links.findByFamilyIdAndMemoryIdIn(familyId, ids).stream()
                .collect(Collectors.groupingBy(MemoryPersonJpaEntity::memoryId,
                        Collectors.mapping(MemoryPersonJpaEntity::personId, Collectors.toSet())));
        Map<UUID, List<MemoryPhoto>> photosByMemory = photos.findByFamilyIdAndMemoryIdIn(familyId, ids).stream()
                .collect(Collectors.groupingBy(MemoryPhotoJpaEntity::memoryId,
                        Collectors.mapping(JpaMemoryRepository::toDomain, Collectors.toList())));
        return found.stream()
                .map(entity -> toDomain(entity, personsByMemory.getOrDefault(entity.id(), Set.of()),
                        photosByMemory.getOrDefault(entity.id(), List.of())))
                .toList();
    }

    private static Memory toDomain(MemoryJpaEntity entity, Set<UUID> relatedPersonIds, List<MemoryPhoto> photos) {
        return Memory.restore(new MemoryId(entity.id()), entity.familyId(), MemoryType.valueOf(entity.type()),
                MemoryStatus.valueOf(entity.status()), entity.title(), entity.content(), relatedPersonIds, photos,
                entity.createdBy(), entity.updatedBy(), entity.createdAt(), entity.updatedAt(), entity.version());
    }

    private static MemoryPhoto toDomain(MemoryPhotoJpaEntity row) {
        return new MemoryPhoto(new MediaAssetId(row.mediaAssetId()), row.position(), row.caption(),
                new TakenDate(TakenDate.Precision.valueOf(row.takenDatePrecision()), row.takenDate(),
                        row.takenYear()));
    }
}
