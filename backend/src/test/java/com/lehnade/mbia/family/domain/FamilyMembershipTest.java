package com.lehnade.mbia.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FamilyMembershipTest {

    @Test
    void creatorMembershipIsAnActiveAdminJoinedNow() {
        Instant now = Instant.parse("2026-09-25T10:00:00Z");
        FamilyId familyId = FamilyId.newId();
        UUID userId = UUID.randomUUID();

        FamilyMembership membership = FamilyMembership.creator(familyId, userId, now);

        assertThat(membership.id()).isNotNull();
        assertThat(membership.familyId()).isEqualTo(familyId);
        assertThat(membership.userId()).isEqualTo(userId);
        assertThat(membership.role()).isEqualTo(MembershipRole.ADMIN);
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.joinedAt()).isEqualTo(now);
        assertThat(membership.removedAt()).isNull();
        assertThat(membership.createdAt()).isEqualTo(now);
        assertThat(membership.updatedAt()).isEqualTo(now);
    }

    @Test
    void anInvitedUserJoinsWithTheInvitationRoleButNeverAsAdmin() {
        Instant now = Instant.parse("2026-09-27T10:00:00Z");

        FamilyMembership membership = FamilyMembership.invited(FamilyId.newId(), UUID.randomUUID(),
                MembershipRole.VIEWER, now);

        assertThat(membership.role()).isEqualTo(MembershipRole.VIEWER);
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.joinedAt()).isEqualTo(now);
        assertThatThrownBy(() -> FamilyMembership.invited(FamilyId.newId(), UUID.randomUUID(),
                MembershipRole.ADMIN, now)).isInstanceOf(IllegalArgumentException.class);
    }

    /** mvp.md §5, data-model.md §7: a removed User invited again returns with the new role. */
    @Test
    void aRemovedMembershipIsReactivatedWithTheNewRoleAndANewJoiningDate() {
        Instant removedAt = Instant.parse("2026-09-20T10:00:00Z");
        Instant now = Instant.parse("2026-09-27T10:00:00Z");
        FamilyMembership removed = FamilyMembership.restore(UUID.randomUUID(), FamilyId.newId(), UUID.randomUUID(),
                MembershipRole.CONTRIBUTOR, MembershipStatus.REMOVED, removedAt.minusSeconds(3600), removedAt,
                removedAt.minusSeconds(3600), removedAt, 2);

        FamilyMembership rejoined = removed.rejoin(MembershipRole.VIEWER, now);

        assertThat(rejoined.id()).isEqualTo(removed.id());
        assertThat(rejoined.role()).isEqualTo(MembershipRole.VIEWER);
        assertThat(rejoined.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(rejoined.joinedAt()).isEqualTo(now);
        assertThat(rejoined.removedAt()).isNull();
        assertThat(rejoined.createdAt()).isEqualTo(removed.createdAt());
        assertThat(rejoined.version()).isEqualTo(2);
        assertThatThrownBy(() -> rejoined.rejoin(MembershipRole.VIEWER, now))
                .isInstanceOf(IllegalStateException.class);
    }
}
