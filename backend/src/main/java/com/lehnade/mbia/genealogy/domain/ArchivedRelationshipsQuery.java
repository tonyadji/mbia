package com.lehnade.mbia.genealogy.domain;

import java.util.List;
import java.util.UUID;

/** The ADMIN "Removed links" read of data-model.md §23.2bis, in one query whatever their number. */
public interface ArchivedRelationshipsQuery {

    /**
     * @return the ARCHIVED relationships of which {@code person} is the source or the target, most
     *     recently archived first (then by id), each with the other Person
     */
    List<ArchivedRelationship> of(UUID familyId, PersonId person);
}
