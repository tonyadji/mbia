package com.lehnade.mbia.genealogy.application.audit;

/**
 * Append-only audit trail of important genealogy mutations (genealogy.md §13, data-model.md §17).
 * Called inside the transaction of the mutation it records.
 */
public interface AuditLog {

    void append(AuditEntry entry);
}
