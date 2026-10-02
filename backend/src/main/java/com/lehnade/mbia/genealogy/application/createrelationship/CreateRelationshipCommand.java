package com.lehnade.mbia.genealogy.application.createrelationship;

import com.lehnade.mbia.genealogy.domain.RelationshipType;
import java.util.Objects;
import java.util.UUID;

/**
 * @param sourcePersonId for {@code PARENT_OF}, the parent
 * @param targetPersonId for {@code PARENT_OF}, the child
 * @param confirmWarnings the User confirmed the date warnings of a previous attempt
 */
public record CreateRelationshipCommand(UUID familyId, RelationshipType type, UUID sourcePersonId,
        UUID targetPersonId, boolean confirmWarnings) {

    public CreateRelationshipCommand {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourcePersonId, "sourcePersonId");
        Objects.requireNonNull(targetPersonId, "targetPersonId");
    }
}
