package com.lehnade.mbia.genealogy.domain;

import java.util.Objects;

/**
 * A removed relationship of a Person and the other Person it links, which may itself be ARCHIVED
 * or MERGED (genealogy.md §11bis).
 */
public record ArchivedRelationship(FamilyRelationship relationship, Person relatedPerson) {

    public ArchivedRelationship {
        Objects.requireNonNull(relationship, "relationship");
        Objects.requireNonNull(relatedPerson, "relatedPerson");
    }
}
