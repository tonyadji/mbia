package com.lehnade.mbia.genealogy.infrastructure.audit;

import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import org.springframework.stereotype.Component;

/**
 * <strong>Temporary</strong> (Phase 2 plan §3.4): {@code audit_entries} does not exist yet, so
 * entries are dropped. PR-28 replaces it with the table-backed implementation.
 */
@Component
class NoOpAuditLog implements AuditLog {

    @Override
    public void append(AuditEntry entry) {
        // Nothing to write before V006__audit_entries.sql.
    }
}
