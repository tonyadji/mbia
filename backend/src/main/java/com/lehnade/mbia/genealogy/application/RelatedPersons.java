package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Persons that content of other modules is attached to (the Persons of a Memory, mvp.md §17,
 * data-model.md §15). Other modules call it; genealogy never depends on them. The caller's
 * membership is checked by the calling use case.
 */
@Service
public class RelatedPersons {

    private final PersonRepository persons;

    public RelatedPersons(PersonRepository persons) {
        this.persons = persons;
    }

    /**
     * Checks that these Persons can be newly attached and locks them until the end of the
     * caller's transaction, so that an archive or a merge cannot interleave (genealogy.md §12).
     *
     * @return the Persons, in UUID order
     * @throws DomainException {@code PERSON_NOT_FOUND} for a Person unknown, of another Family or
     *     MERGED; otherwise {@code PERSON_NOT_ACTIVE} for an ARCHIVED one (OQ-035, OQ-037)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RelatedPerson> requireLinkable(UUID familyId, Collection<UUID> personIds) {
        return requireRelinkable(familyId, Set.of(), personIds);
    }

    /**
     * The new Persons of existing content: as {@link #requireLinkable}, except that an ARCHIVED
     * Person already attached may stay (OQ-035). Every Person is locked, so that the caller can
     * rely on the returned statuses until the end of its transaction.
     *
     * @param alreadyLinked the Persons the content is attached to now
     * @return the Persons, in UUID order, with their status
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RelatedPerson> requireRelinkable(UUID familyId, Set<UUID> alreadyLinked,
            Collection<UUID> personIds) {
        List<Person> found = persons.lockInFamily(familyId, personIds.stream().map(PersonId::new).toList());
        boolean allVisible = found.size() == personIds.stream().distinct().count()
                && found.stream().noneMatch(person -> person.status() == PersonStatus.MERGED);
        if (!allVisible) {
            throw PersonNotFound.exception();
        }
        boolean newOnesActive = found.stream()
                .filter(person -> !alreadyLinked.contains(person.id().value()))
                .allMatch(Person::isActive);
        if (!newOnesActive) {
            throw new DomainException(ErrorCode.PERSON_NOT_ACTIVE, "An archived person cannot be linked.");
        }
        return found.stream().map(RelatedPersons::toRelated).toList();
    }

    /** @return these Persons of the Family, whatever their status, by id; another Family's are absent */
    @Transactional(readOnly = true)
    public Map<UUID, RelatedPerson> describe(UUID familyId, Collection<UUID> personIds) {
        return persons.findAllInFamily(familyId, personIds.stream().map(PersonId::new).toList()).stream()
                .map(RelatedPersons::toRelated)
                .collect(Collectors.toMap(RelatedPerson::id, Function.identity()));
    }

    private static RelatedPerson toRelated(Person person) {
        return new RelatedPerson(person.id().value(), person.details().displayName(),
                RelatedPerson.Status.valueOf(person.status().name()));
    }
}
