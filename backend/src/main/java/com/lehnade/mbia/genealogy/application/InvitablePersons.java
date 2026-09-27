package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Persons an invitation may be sent for (mvp.md §18, OQ-050), for the invitation module;
 * genealogy never depends on it. The caller's membership is checked by the calling use case.
 */
@Service
public class InvitablePersons {

    private final PersonRepository persons;

    public InvitablePersons(PersonRepository persons) {
        this.persons = persons;
    }

    /**
     * Checks that an invitation may be sent for this Person, and locks it until the end of the
     * caller's transaction, so that a second invitation for it waits for the first one.
     *
     * @throws DomainException {@code PERSON_NOT_FOUND} for a Person unknown, of another Family,
     *     ARCHIVED or MERGED; {@code PERSON_ALREADY_CLAIMED} when linked to a User;
     *     {@code VALIDATION_FAILED} on {@code personId} when deceased (OQ-050)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireInvitable(UUID familyId, UUID personId) {
        Person person = persons.lockInFamily(familyId, List.of(new PersonId(personId))).stream()
                .filter(Person::isActive)
                .findFirst()
                .orElseThrow(PersonNotFound::exception);
        if (person.isLinked()) {
            throw new DomainException(ErrorCode.PERSON_ALREADY_CLAIMED,
                    "This person is already linked to a member.");
        }
        if (person.details().deceased()) {
            throw new FieldValidationException("personId", "DECEASED", "A deceased person cannot be invited.");
        }
    }

    /**
     * @return the Person an invitation was sent for, to offer to the member who accepted it, while
     *     it is ACTIVE and linked to no User; empty once linked, archived or merged (OQ-050, OQ-056)
     */
    @Transactional(readOnly = true)
    public Optional<Suggestion> suggestion(UUID familyId, UUID personId) {
        return persons.findInFamily(familyId, new PersonId(personId))
                .filter(person -> person.isActive() && !person.isLinked())
                .map(person -> new Suggestion(person.id().value(), person.details().displayName()));
    }

    public record Suggestion(UUID personId, String displayName) {}

    /**
     * @return the display name of those of these Persons of the Family that are ACTIVE: an
     *     invitation shows its Person only while it is ACTIVE (OQ-056)
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> activeDisplayNames(UUID familyId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return persons.findAllInFamily(familyId, personIds.stream().distinct().map(PersonId::new).toList()).stream()
                .filter(Person::isActive)
                .collect(Collectors.toMap(person -> person.id().value(), person -> person.details().displayName()));
    }
}
