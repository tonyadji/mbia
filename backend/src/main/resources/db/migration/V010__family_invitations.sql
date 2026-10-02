-- Invitations to join a Family (data-model.md §8, mvp.md §18). Enumerations are checked VARCHAR
-- columns (§3). Only the SHA-256 hash of the raw token is stored; the token itself never is.
CREATE TABLE family_invitations (
    id              UUID PRIMARY KEY,
    family_id       UUID NOT NULL REFERENCES families(id),
    channel         VARCHAR(20) NOT NULL,
    email           VARCHAR(320),
    locale          VARCHAR(5) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    person_id       UUID,
    email_delivery  VARCHAR(20),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invited_by      UUID NOT NULL REFERENCES users(id),
    accepted_by     UUID REFERENCES users(id),
    revoked_by      UUID REFERENCES users(id),
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    renewed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,

    CHECK (channel IN ('EMAIL', 'LINK')),
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    -- The ADMIN role is never granted through an invitation (mvp.md §4).
    CHECK (role IN ('CONTRIBUTOR', 'VIEWER')),
    CHECK (locale IN ('fr', 'en')),
    CHECK (channel <> 'EMAIL' OR email IS NOT NULL),
    CHECK ((channel = 'EMAIL') = (email_delivery IS NOT NULL)),
    CHECK (email_delivery IS NULL OR email_delivery IN ('PENDING', 'SENT', 'FAILED')),
    CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL)),
    CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),

    -- The suggested Person belongs to the invitation's Family (§2.5, OQ-050).
    CONSTRAINT fk_invitation_person
        FOREIGN KEY (person_id, family_id) REFERENCES persons(id, family_id)
);

-- Pending invitations of a Family, by email.
CREATE INDEX idx_invitations_family_pending
ON family_invitations (family_id, lower(email))
WHERE status = 'PENDING';

-- A Person has at most one pending invitation (INVITATION_ALREADY_PENDING).
CREATE UNIQUE INDEX uq_invitations_person_pending
ON family_invitations (person_id)
WHERE status = 'PENDING' AND person_id IS NOT NULL;
