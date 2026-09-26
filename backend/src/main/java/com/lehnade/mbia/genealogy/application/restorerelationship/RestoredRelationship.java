package com.lehnade.mbia.genealogy.application.restorerelationship;

import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.RelationshipWarning;
import java.util.List;

/**
 * The restored relationship and its date warnings from the current birth data, for information:
 * they do not block a restoration (OQ-021).
 */
public record RestoredRelationship(FamilyRelationship relationship, List<RelationshipWarning> warnings) {

    public RestoredRelationship {
        warnings = List.copyOf(warnings);
    }
}
