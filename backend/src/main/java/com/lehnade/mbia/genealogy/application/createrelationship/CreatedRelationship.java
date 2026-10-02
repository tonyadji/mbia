package com.lehnade.mbia.genealogy.application.createrelationship;

import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.RelationshipWarning;
import java.util.List;

/** The created relationship and the date warnings the User confirmed, empty when there were none. */
public record CreatedRelationship(FamilyRelationship relationship, List<RelationshipWarning> warnings) {

    public CreatedRelationship {
        warnings = List.copyOf(warnings);
    }
}
