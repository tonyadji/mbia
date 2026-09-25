package com.lehnade.mbia.genealogy.application.getfamilytree;

import com.lehnade.mbia.genealogy.domain.FamilyTree;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.Map;
import java.util.Optional;

/**
 * A family tree as shown to the current User.
 *
 * @param tree empty when the Family has no ACTIVE Person
 * @param relationshipToCurrentUser what each node is to the current User's linked Person; no entry
 *     when the current User has no linked Person in the Family
 */
public record FamilyTreeView(Optional<FamilyTree> tree, Map<PersonId, KinshipCode> relationshipToCurrentUser) {

    public FamilyTreeView {
        relationshipToCurrentUser = Map.copyOf(relationshipToCurrentUser);
    }

    static FamilyTreeView empty() {
        return new FamilyTreeView(Optional.empty(), Map.of());
    }
}
