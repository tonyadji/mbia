package com.lehnade.mbia.genealogy.application.createperson;

import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.UUID;

/**
 * @param linkToCurrentUser "Start with me": the new Person represents the current User
 * @param confirmPossibleDuplicate the User chose to create the Person despite similar Persons
 */
public record CreatePersonCommand(UUID familyId, PersonDetails details, boolean linkToCurrentUser,
        boolean confirmPossibleDuplicate) {}
