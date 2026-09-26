-- Internal, append-only audit trail of important mutations (data-model.md §17, genealogy.md §13).
-- Values are field-focused and never hold secrets. The Person history reads it (§18).
CREATE TABLE audit_entries (
    id              UUID PRIMARY KEY,
    family_id       UUID REFERENCES families(id),
    actor_user_id   UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     UUID,
    old_value       JSONB,
    new_value       JSONB,
    trace_id        VARCHAR(100),
    occurred_at     TIMESTAMPTZ NOT NULL
);

-- History of one resource, most recent first (Phase 2 plan §3.3).
CREATE INDEX ix_audit_entries_resource
ON audit_entries (family_id, resource_type, resource_id, occurred_at DESC);
