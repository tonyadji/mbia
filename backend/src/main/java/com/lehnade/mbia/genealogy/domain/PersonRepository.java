package com.lehnade.mbia.genealogy.domain;

import java.util.UUID;

public interface PersonRepository {

    /**
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code USER_ALREADY_LINKED} when the
     *     Person's linked User already represents another non-MERGED Person of the Family
     */
    void insert(Person person);

    /** @return whether the User represents a non-MERGED Person of the Family (data-model.md §21) */
    boolean existsLinkedTo(UUID familyId, UUID userId);
}
