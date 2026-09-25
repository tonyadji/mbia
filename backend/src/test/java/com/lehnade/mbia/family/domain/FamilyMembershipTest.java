package com.lehnade.mbia.family.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
