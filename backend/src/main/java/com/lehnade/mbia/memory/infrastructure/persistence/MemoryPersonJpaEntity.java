package com.lehnade.mbia.memory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code memory_persons} (data-model.md §15). Always new when saved: an association is
 * inserted, never updated, so Spring Data persists it without reading it first.
 */
@Entity
@Table(name = "memory_persons")
@IdClass(MemoryPersonJpaEntity.Key.class)
class MemoryPersonJpaEntity implements Persistable<MemoryPersonJpaEntity.Key> {

    @Id
    @Column(name = "memory_id")
    private UUID memoryId;

    @Id
    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MemoryPersonJpaEntity() {}

    MemoryPersonJpaEntity(UUID familyId, UUID memoryId, UUID personId, Instant createdAt) {
        this.familyId = familyId;
        this.memoryId = memoryId;
        this.personId = personId;
        this.createdAt = createdAt;
    }

    UUID personId() {
        return personId;
    }

    @Override
    public Key getId() {
        return new Key(memoryId, personId);
    }

    @Override
    public boolean isNew() {
        return true;
    }

    record Key(UUID memoryId, UUID personId) implements Serializable {}
}
