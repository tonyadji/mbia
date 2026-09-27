package com.lehnade.mbia.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** mvp.md §18, data-model.md §8, OQ-057: expiry, renewal, revocation and acceptance of an invitation. */
class InvitationTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    @Test
    void aLinkInvitationIsPendingAndExpires14DaysAfterItsCreation() {
        Invitation invitation = newInvitation();

        assertThat(invitation.channel()).isEqualTo(InvitationChannel.LINK);
        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.expiresAt()).isEqualTo(Instant.parse("2026-10-11T10:00:00Z"));
        assertThat(invitation.renewedAt()).isEmpty();
        assertThat(invitation.version()).isZero();
    }

    @Test
    void aBlankEmailIsNoEmailAndAnEmailIsTrimmed() {
        assertThat(Invitation.createLink(InvitationId.newId(), UUID.randomUUID(), "  ", "fr",
                InvitationRole.VIEWER, null, "hash", ADMIN, NOW).email()).isEmpty();
        assertThat(Invitation.createLink(InvitationId.newId(), UUID.randomUUID(), " awa@example.com ", "fr",
                InvitationRole.VIEWER, null, "hash", ADMIN, NOW).email()).contains("awa@example.com");
    }

    @ParameterizedTest
    @EnumSource(value = InvitationStatus.class, names = {"PENDING", "EXPIRED"})
    void aPendingOrExpiredInvitationIsRenewedWithANewLinkAndA14DayExpiry(InvitationStatus status) {
        Instant later = NOW.plus(Duration.ofDays(20));

        Invitation invitation = inStatus(status);

        Invitation renewed = invitation.renew("new-hash", later);

        assertThat(renewed.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(renewed.tokenHash()).isEqualTo("new-hash");
        assertThat(renewed.expiresAt()).isEqualTo(later.plus(Duration.ofDays(14)));
        assertThat(renewed.renewedAt()).contains(later);
        assertThat(renewed.personId()).isEqualTo(invitation.personId());
    }

    @ParameterizedTest
    @EnumSource(value = InvitationStatus.class, names = {"PENDING", "EXPIRED"})
    void aPendingOrExpiredInvitationIsRevoked(InvitationStatus status) {
        Invitation revoked = inStatus(status).revoke(ADMIN, NOW);

        assertThat(revoked.status()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(revoked.revokedBy()).contains(ADMIN);
        assertThat(revoked.revokedAt()).contains(NOW);
    }

    @Test
    void aRevokedInvitationCanNeitherBeRenewedNorRevokedAgain() {
        Invitation revoked = newInvitation().revoke(ADMIN, NOW);

        assertFinal(() -> revoked.renew("new-hash", NOW), ErrorCode.INVITATION_REVOKED);
        assertFinal(() -> revoked.revoke(ADMIN, NOW), ErrorCode.INVITATION_REVOKED);
    }

    @Test
    void anAcceptedInvitationCanNeitherBeRenewedNorRevoked() {
        Invitation accepted = inStatus(InvitationStatus.ACCEPTED);

        assertFinal(() -> accepted.renew("new-hash", NOW), ErrorCode.INVITATION_ALREADY_USED);
        assertFinal(() -> accepted.revoke(ADMIN, NOW), ErrorCode.INVITATION_ALREADY_USED);
    }

    @Test
    void aPendingInvitationIsAcceptedOnceByTheUserWhoAcceptsIt() {
        UUID invitee = UUID.randomUUID();
        Invitation accepted = newInvitation().accept(invitee, NOW.plus(Duration.ofDays(1)));

        assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(accepted.acceptedBy()).contains(invitee);
        assertThat(accepted.acceptedAt()).contains(NOW.plus(Duration.ofDays(1)));
        assertThat(accepted.personId()).isPresent();
        assertFinal(() -> accepted.accept(UUID.randomUUID(), NOW), ErrorCode.INVITATION_ALREADY_USED);
    }

    @Test
    void anInvitationThatIsNoLongerPendingCannotBeAccepted() {
        assertFinal(() -> inStatus(InvitationStatus.REVOKED).accept(ADMIN, NOW), ErrorCode.INVITATION_REVOKED);
        assertFinal(() -> inStatus(InvitationStatus.EXPIRED).accept(ADMIN, NOW), ErrorCode.INVITATION_EXPIRED);
    }

    @Test
    void aPendingInvitationPastItsExpiryIsExpiredBeforeItsStatusIsUpdated() {
        Invitation invitation = newInvitation();

        invitation.requireAcceptable(invitation.expiresAt().minusSeconds(1));
        assertFinal(() -> invitation.requireAcceptable(invitation.expiresAt()), ErrorCode.INVITATION_EXPIRED);
        assertFinal(() -> invitation.accept(ADMIN, invitation.expiresAt().plusSeconds(1)),
                ErrorCode.INVITATION_EXPIRED);
    }

    private static void assertFinal(Runnable change, ErrorCode code) {
        assertThatThrownBy(change::run)
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    assertThat(((DomainException) error).code()).isEqualTo(code);
                    assertThat(((DomainException) error).httpStatus()).isEqualTo(410);
                });
    }

    private static Invitation newInvitation() {
        return Invitation.createLink(InvitationId.newId(), UUID.randomUUID(), null, "fr", InvitationRole.CONTRIBUTOR,
                UUID.randomUUID(), "hash", ADMIN, NOW);
    }

    private static Invitation inStatus(InvitationStatus status) {
        Invitation invitation = newInvitation();
        return Invitation.restore(invitation.id(), invitation.familyId(), invitation.channel(), null,
                invitation.locale(), invitation.role(), invitation.personId().orElseThrow(), invitation.tokenHash(),
                status, ADMIN, null, null, invitation.expiresAt(), null, null, null, NOW, NOW, 3);
    }
}
