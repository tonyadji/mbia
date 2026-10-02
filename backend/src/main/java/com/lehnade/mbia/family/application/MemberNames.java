package com.lehnade.mbia.family.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** The names of the Family's members, read from their accounts. */
public interface MemberNames {

    /** @return the display name of each requested User; a User without one, or deleted, is absent */
    Map<UUID, String> displayNames(Collection<UUID> userIds);
}
