package com.lehnade.mbia.genealogy.infrastructure.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of {@code audit_entries} (data-model.md §17): written once, never changed. */
@Entity
@Immutable
@Table(name = "audit_entries")
class AuditEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private Map<String, Object> newValue;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntryJpaEntity() {}

    AuditEntryJpaEntity(UUID id, UUID familyId, UUID actorUserId, String action, String resourceType,
            UUID resourceId, Map<String, Object> oldValue, Map<String, Object> newValue, String traceId,
            Instant occurredAt) {
        this.id = id;
        this.familyId = familyId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }

    UUID id() {
        return id;
    }

    UUID actorUserId() {
        return actorUserId;
    }

    String action() {
        return action;
    }

    Map<String, Object> oldValue() {
        return oldValue == null ? Map.of() : oldValue;
    }

    Map<String, Object> newValue() {
        return newValue == null ? Map.of() : newValue;
    }

    Instant occurredAt() {
        return occurredAt;
    }
}
