package com.lehnade.mbia.genealogy.domain;

import java.util.Optional;
import java.util.UUID;

public interface PersonRepository {

    /**
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code USER_ALREADY_LINKED} when the
     *     Person's linked User already represents another non-MERGED Person of the Family
     */
    void insert(Person person);

    /** @return the Person of this Family, whatever its status; empty for another Family's Person */
    Optional<Person> findInFamily(UUID familyId, PersonId id);

    /**
     * Persists a change of {@code person}, built from its persisted {@code version()}.
     *
     * @return the Person as persisted, with its version incremented
     * @throws org.springframework.dao.OptimisticLockingFailureException when the Person changed
     *     since it was read
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code USER_ALREADY_LINKED} when the
     *     Person's linked User already represents another non-MERGED Person of the Family
     */
    Person update(Person person);

    /** @return whether the User represents a non-MERGED Person of the Family (data-model.md §21) */
    boolean existsLinkedTo(UUID familyId, UUID userId);
}
