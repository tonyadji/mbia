package com.lehnade.mbia.genealogy.application.createperson;

import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.Optional;
import java.util.UUID;

/**
 * @param profileMediaAssetId the uploaded photo of the new Person, if any (OQ-036, OQ-040)
 * @param linkToCurrentUser "Start with me": the new Person represents the current User
 * @param confirmPossibleDuplicate the User chose to create the Person despite similar Persons
 */
public record CreatePersonCommand(UUID familyId, PersonDetails details, Optional<UUID> profileMediaAssetId,
        boolean linkToCurrentUser, boolean confirmPossibleDuplicate) {}
