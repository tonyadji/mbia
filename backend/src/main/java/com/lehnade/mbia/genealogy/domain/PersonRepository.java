package com.lehnade.mbia.genealogy.domain;

import java.util.Collection;
import java.util.List;
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
     * Locks the rows of these Persons of the Family until the end of the transaction, in the
     * deterministic order of their UUIDs, so that two transactions locking the same Persons cannot
     * deadlock (genealogy.md §12).
     *
     * @return the locked Persons, whatever their status, in UUID order; another Family's are absent
     */
    List<Person> lockInFamily(UUID familyId, Collection<PersonId> ids);

    /** @return these Persons of the Family, whatever their status; another Family's are absent */
    List<Person> findAllInFamily(UUID familyId, Collection<PersonId> ids);

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

    /** @return the non-MERGED Person of the Family that represents the User, if any */
    Optional<Person> findLinkedTo(UUID familyId, UUID userId);
}
