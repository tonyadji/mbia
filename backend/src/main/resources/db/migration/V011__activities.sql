-- User-facing recent activity of a Family (data-model.md §16, OQ-054). Written in the transaction of
-- the operation it records; the payload holds presentation-safe names at the time of the action,
-- never a story text, a caption, an email address, a token or a storage key. Nothing is rebuilt
-- from audit_entries: the feed starts empty.
CREATE TABLE activities (
    id              UUID PRIMARY KEY,
    family_id       UUID NOT NULL REFERENCES families(id),
    actor_user_id   UUID REFERENCES users(id),
    activity_type   VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100),
    resource_id     UUID,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL
);

-- The recent activity of one Family, most recent first (data-model.md §16).
CREATE INDEX idx_activities_family_recent
ON activities (family_id, occurred_at DESC);
