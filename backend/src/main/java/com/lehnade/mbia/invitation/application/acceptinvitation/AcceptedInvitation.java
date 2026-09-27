package com.lehnade.mbia.invitation.application.acceptinvitation;

import com.lehnade.mbia.family.application.InvitationFamilies.Joining;
import com.lehnade.mbia.genealogy.application.InvitablePersons.Suggestion;
import com.lehnade.mbia.identity.application.CurrentUser;
import java.util.Optional;

/**
 * The outcome of accepting an invitation, for the member who accepted it.
 *
 * @param suggestedPerson the Person to offer with "Are you {displayName}?" (OQ-050)
 * @param linkedPersonDisplayName the display name of the member's linked Person, {@code null} when
 *     none
 */
public record AcceptedInvitation(CurrentUser member, Joining joining, Optional<Suggestion> suggestedPerson,
        String linkedPersonDisplayName) {}
