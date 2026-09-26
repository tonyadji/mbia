package com.lehnade.mbia.genealogy.infrastructure.audit;

import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.shared.api.tracing.RequestIdFilter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes each entry to {@code audit_entries} (data-model.md §17), in the transaction of the
 * mutation it records, with the identifier of the request as {@code trace_id}.
 */
@Component
class JpaAuditLog implements AuditLog {

    private final AuditEntryJpaRepository entries;

    JpaAuditLog(AuditEntryJpaRepository entries) {
        this.entries = entries;
    }

    @Override
    public void append(AuditEntry entry) {
        entries.save(new AuditEntryJpaEntity(UUID.randomUUID(), entry.familyId(), entry.actorUserId(),
                entry.action(), entry.resourceType(), entry.resourceId(), entry.oldValue(), entry.newValue(),
                RequestIdFilter.currentRequestId(), entry.occurredAt()));
    }
}
