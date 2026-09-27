package com.lehnade.mbia.activity.infrastructure.persistence;

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

/** Row of {@code activities} (data-model.md §16): written once, never changed. */
@Entity
@Immutable
@Table(name = "activities")
class ActivityJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "activity_type", nullable = false)
    private String activityType;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ActivityJpaEntity() {}

    ActivityJpaEntity(UUID id, UUID familyId, UUID actorUserId, String activityType, String resourceType,
            UUID resourceId, Map<String, Object> payload, Instant occurredAt) {
        this.id = id;
        this.familyId = familyId;
        this.actorUserId = actorUserId;
        this.activityType = activityType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
