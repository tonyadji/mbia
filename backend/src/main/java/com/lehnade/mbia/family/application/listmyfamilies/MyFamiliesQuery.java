package com.lehnade.mbia.family.application.listmyfamilies;

import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.MembershipRole;
import java.util.List;
import java.util.UUID;

/** Read model of the Families a User can access. */
public interface MyFamiliesQuery {

    /**
     * Families where the User's membership is ACTIVE, oldest first.
     */
    List<MyFamily> findActiveFor(UUID userId);

    record MyFamily(Family family, MembershipRole myRole, long activeMemberCount) {}
}
