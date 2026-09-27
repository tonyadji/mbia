package com.lehnade.mbia.invitation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {

    /**
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code INVITATION_ALREADY_PENDING}
     *     when its Person already has a PENDING invitation
     */
    void insert(Invitation invitation);

    /**
     * Writes the changes of an invitation read at {@code invitation.version()}; a commit by another
     * transaction since it was read fails instead of being overwritten (technical-specification.md
     * §13).
     *
     * @return the invitation as stored, with its incremented version
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code INVITATION_ALREADY_PENDING}
     *     when a renewal makes a second PENDING invitation for its Person
     */
    Invitation update(Invitation invitation);

    /** @return the invitation of this Family, whatever its status; empty for another Family's */
    Optional<Invitation> findInFamily(UUID familyId, InvitationId id);

    /** @return the Family's invitations in this status, most recent first */
    List<Invitation> findInFamily(UUID familyId, InvitationStatus status);

    boolean existsPendingForPerson(UUID personId);

    /**
     * Marks EXPIRED the Family's PENDING invitations whose expiry has passed, each with a new
     * version (data-model.md §8: {@code EXPIRED} is set lazily when read).
     */
    void expireDue(UUID familyId, Instant now);
}
