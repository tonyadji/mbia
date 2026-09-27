package com.lehnade.mbia.family.infrastructure.persistence;

import com.lehnade.mbia.family.application.MemberNames;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Member names from {@code users}, in one query, like the senders of invitations. */
@Repository
class JpaMemberNames implements MemberNames {

    private final FamilyMembershipJpaRepository jpa;

    JpaMemberNames(FamilyMembershipJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, String> displayNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findDisplayNames(userIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (String) row[1]));
    }
}
