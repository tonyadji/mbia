package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds {@link MemberView}s for use cases that already checked the caller's access. */
@Component
public class MemberViews {

    private final MemberNames names;
    private final MemberPersonsPort persons;

    public MemberViews(MemberNames names, MemberPersonsPort persons) {
        this.names = names;
        this.persons = persons;
    }

    /** @return the memberships as {@code callerId} sees them, in the same order */
    public List<MemberView> of(UUID familyId, UUID callerId, List<FamilyMembership> memberships) {
        List<UUID> userIds = memberships.stream().map(FamilyMembership::userId).toList();
        Map<UUID, String> displayNames = names.displayNames(userIds);
        Map<UUID, MemberPersonsPort.MemberPerson> linked = persons.linkedPersons(familyId, callerId, userIds);
        return memberships.stream().map(membership -> {
            Optional<MemberPersonsPort.MemberPerson> person = Optional.ofNullable(linked.get(membership.userId()));
            return new MemberView(membership.id(), membership.userId(), displayNames.get(membership.userId()),
                    FamilyRole.of(membership.role()),
                    person.map(MemberPersonsPort.MemberPerson::personId).orElse(null),
                    person.map(MemberPersonsPort.MemberPerson::displayName).orElse(null),
                    person.map(MemberPersonsPort.MemberPerson::relationshipToCaller).orElse(null),
                    membership.joinedAt(), membership.version());
        }).toList();
    }

    /** The one answer for a membership that is unknown, of another Family or REMOVED (OQ-061). */
    public static DomainException memberNotFound() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Member not found.");
    }
}
