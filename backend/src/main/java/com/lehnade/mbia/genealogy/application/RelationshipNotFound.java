package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;

/** The one answer for a relationship the caller cannot see: unknown, or of another Family. */
public final class RelationshipNotFound {

    private RelationshipNotFound() {}

    public static DomainException exception() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Relationship not found.");
    }
}
