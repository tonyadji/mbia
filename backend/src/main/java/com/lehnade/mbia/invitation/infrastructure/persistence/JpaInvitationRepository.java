package com.lehnade.mbia.invitation.infrastructure.persistence;

import com.lehnade.mbia.invitation.application.InvitationErrors;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationChannel;
import com.lehnade.mbia.invitation.domain.InvitationId;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.invitation.domain.InvitationRole;
import com.lehnade.mbia.invitation.domain.InvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaInvitationRepository implements InvitationRepository {

    /** The unique partial index of data-model.md §8: one PENDING invitation per Person. */
    private static final String ONE_PENDING_PER_PERSON = "uq_invitations_person_pending";

    private final InvitationJpaRepository jpa;

    JpaInvitationRepository(InvitationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(Invitation invitation) {
        InvitationJpaEntity entity = new InvitationJpaEntity(invitation.id().value(), invitation.familyId(),
                invitation.channel().name(), invitation.email().orElse(null), invitation.locale(),
                invitation.role().name(), invitation.personId().orElse(null), invitation.invitedBy(),
                invitation.createdAt());
        changeState(entity, invitation);
        flushing(() -> jpa.saveAndFlush(entity));
    }

    /**
     * Hibernate writes {@code UPDATE … WHERE version = ?} (JPA {@code @Version}): a commit by
     * another transaction since the invitation was read fails the flush instead of being overwritten.
     */
    @Override
    public Invitation update(Invitation invitation) {
        InvitationJpaEntity entity = jpa.findByIdAndFamilyId(invitation.id().value(), invitation.familyId())
                .filter(found -> found.version() == invitation.version())
                .orElseThrow(() -> new OptimisticLockingFailureException("The invitation changed since it was read."));
        changeState(entity, invitation);
        return toDomain(flushing(() -> jpa.saveAndFlush(entity)));
    }

    @Override
    public Optional<Invitation> findInFamily(UUID familyId, InvitationId id) {
        return jpa.findByIdAndFamilyId(id.value(), familyId).map(JpaInvitationRepository::toDomain);
    }

    @Override
    public List<Invitation> findInFamily(UUID familyId, InvitationStatus status) {
        return jpa.findByFamilyIdAndStatusOrderByCreatedAtDescIdDesc(familyId, status.name()).stream()
                .map(JpaInvitationRepository::toDomain)
                .toList();
    }

    @Override
    public boolean existsPendingForPerson(UUID personId) {
        return jpa.existsByPersonIdAndStatus(personId, InvitationStatus.PENDING.name());
    }

    @Override
    public void expireDue(UUID familyId, Instant now) {
        jpa.expireDue(familyId, now);
    }

    private static void changeState(InvitationJpaEntity entity, Invitation invitation) {
        entity.changeState(invitation.tokenHash(), invitation.status().name(), invitation.expiresAt(),
                invitation.revokedBy().orElse(null), invitation.revokedAt().orElse(null),
                invitation.renewedAt().orElse(null), invitation.updatedAt());
    }

    /** A second PENDING invitation for a Person, committed concurrently, is the business conflict. */
    private static <T> T flushing(Supplier<T> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException violation) {
            if (String.valueOf(violation.getMostSpecificCause().getMessage()).contains(ONE_PENDING_PER_PERSON)) {
                throw InvitationErrors.alreadyPending();
            }
            throw violation;
        }
    }

    private static Invitation toDomain(InvitationJpaEntity entity) {
        return Invitation.restore(new InvitationId(entity.id()), entity.familyId(),
                InvitationChannel.valueOf(entity.channel()), entity.email(), entity.locale(),
                InvitationRole.valueOf(entity.role()), entity.personId(), entity.tokenHash(),
                InvitationStatus.valueOf(entity.status()), entity.invitedBy(), entity.revokedBy(),
                entity.expiresAt(), entity.revokedAt(), entity.renewedAt(), entity.createdAt(), entity.updatedAt(),
                entity.version());
    }
}
