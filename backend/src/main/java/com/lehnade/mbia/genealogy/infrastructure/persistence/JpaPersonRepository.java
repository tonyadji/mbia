package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.DatePrecision;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaPersonRepository implements PersonRepository {

    /** One linked Person per User and Family (data-model.md §10): the final guard against races. */
    static final String LINKED_USER_UNIQUE_INDEX = "uq_person_linked_user_per_family";

    private final PersonJpaRepository jpa;

    JpaPersonRepository(PersonJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(Person person) {
        PersonDetails details = person.details();
        try {
            jpa.saveAndFlush(new PersonJpaEntity(person.id().value(), person.familyId(), details.firstName(),
                    details.middleNames(), details.lastName(), details.preferredName(), details.gender().name(),
                    details.birth().date(), year(details.birth()), details.birth().precision().name(),
                    details.deceased(), details.death().date(), year(details.death()),
                    details.death().precision().name(), details.biography(), person.linkedUserId().orElse(null),
                    person.status().name(), person.createdBy(), person.updatedBy(), person.createdAt(),
                    person.updatedAt()));
        } catch (DataIntegrityViolationException e) {
            throw translated(e);
        }
    }

    @Override
    public Optional<Person> findInFamily(UUID familyId, PersonId id) {
        return jpa.findByIdAndFamilyId(id.value(), familyId).map(JpaPersonRepository::toDomain);
    }

    @Override
    public List<Person> lockInFamily(UUID familyId, Collection<PersonId> ids) {
        return jpa.lockInFamily(familyId, ids.stream().map(PersonId::value).toList()).stream()
                .map(JpaPersonRepository::toDomain)
                .toList();
    }

    @Override
    public Set<UUID> findProfilePicturesAmong(Collection<UUID> mediaAssetIds) {
        if (mediaAssetIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jpa.findProfilePicturesAmong(mediaAssetIds));
    }

    @Override
    public List<Person> findAllInFamily(UUID familyId, Collection<PersonId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByFamilyIdAndIdIn(familyId, ids.stream().map(PersonId::value).toList()).stream()
                .map(JpaPersonRepository::toDomain)
                .toList();
    }

    /**
     * Hibernate writes {@code UPDATE … WHERE version = ?} (JPA {@code @Version}): a commit by
     * another transaction since the Person was read fails the flush instead of being overwritten.
     */
    @Override
    public Person update(Person person) {
        PersonJpaEntity entity = jpa.findByIdAndFamilyId(person.id().value(), person.familyId())
                .filter(found -> found.version() == person.version())
                .orElseThrow(() -> new OptimisticLockingFailureException("The person changed since it was read."));
        PersonDetails details = person.details();
        entity.changeDetails(details.firstName(), details.middleNames(), details.lastName(),
                details.preferredName(), details.gender().name(), details.birth().date(), year(details.birth()),
                details.birth().precision().name(), details.deceased(), details.death().date(),
                year(details.death()), details.death().precision().name(), details.biography(),
                person.updatedBy(), person.updatedAt());
        entity.changeLinkedUser(person.linkedUserId().orElse(null));
        entity.changeStatus(person.status().name(), person.archivedAt().orElse(null),
                person.mergedIntoPersonId().map(PersonId::value).orElse(null));
        try {
            return toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw translated(e);
        }
    }

    @Override
    public boolean existsLinkedTo(UUID familyId, UUID userId) {
        return jpa.existsByFamilyIdAndLinkedUserIdAndStatusNot(familyId, userId, PersonStatus.MERGED.name());
    }

    @Override
    public Optional<Person> findLinkedTo(UUID familyId, UUID userId) {
        return jpa.findByFamilyIdAndLinkedUserIdAndStatusNot(familyId, userId, PersonStatus.MERGED.name())
                .map(JpaPersonRepository::toDomain);
    }

    static Person toDomain(PersonJpaEntity entity) {
        PersonDetails details = new PersonDetails(entity.firstName(), entity.middleNames(), entity.lastName(),
                entity.preferredName(), Gender.valueOf(entity.gender()),
                partialDate(entity.birthDatePrecision(), entity.birthDate(), entity.birthYear()), entity.deceased(),
                partialDate(entity.deathDatePrecision(), entity.deathDate(), entity.deathYear()),
                entity.biography());
        return Person.restore(new PersonId(entity.id()), entity.familyId(), details, entity.linkedUserId(),
                PersonStatus.valueOf(entity.status()), entity.createdBy(), entity.updatedBy(), entity.createdAt(),
                entity.updatedAt(), entity.archivedAt(),
                entity.mergedIntoPersonId() == null ? null : new PersonId(entity.mergedIntoPersonId()),
                entity.version());
    }

    private static PartialDate partialDate(String precision, LocalDate date, Short year) {
        return new PartialDate(DatePrecision.valueOf(precision), date, year == null ? null : year.intValue());
    }

    private static Short year(PartialDate date) {
        return date.year() == null ? null : date.year().shortValue();
    }

    /** A concurrent link of the same User to another Person of the Family is the caller's conflict. */
    private static RuntimeException translated(DataIntegrityViolationException error) {
        if (violates(error, LINKED_USER_UNIQUE_INDEX)) {
            return new DomainException(ErrorCode.USER_ALREADY_LINKED,
                    "You are already linked to a person of this family.");
        }
        return error;
    }

    private static boolean violates(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
