package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import java.util.Optional;

/**
 * A Person as shown to the current User.
 *
 * @param relationshipToCurrentUser a {@code KinshipCode} name (openapi), empty when the current
 *     User has no linked Person in the Family
 */
public record PersonView(Person person, Optional<String> relationshipToCurrentUser) {}
