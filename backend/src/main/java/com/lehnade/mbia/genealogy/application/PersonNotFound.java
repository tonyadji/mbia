package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;

/** The one answer for a Person the caller cannot see: unknown, or of another Family. */
public final class PersonNotFound {

    private PersonNotFound() {}

    public static DomainException exception() {
        return new DomainException(ErrorCode.PERSON_NOT_FOUND, "Person not found.");
    }
}
