package com.lehnade.mbia.shared.application.audit;

/**
 * Append-only audit trail of important mutations of every module (genealogy.md §13,
 * data-model.md §17). Called inside the transaction of the mutation it records.
 */
public interface AuditLog {

    void append(AuditEntry entry);
}
