package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
            if (violates(e, LINKED_USER_UNIQUE_INDEX)) {
                throw new DomainException(ErrorCode.USER_ALREADY_LINKED,
                        "You are already linked to a person of this family.");
            }
            throw e;
        }
    }

    @Override
    public boolean existsLinkedTo(UUID familyId, UUID userId) {
        return jpa.existsByFamilyIdAndLinkedUserIdAndStatusNot(familyId, userId, PersonStatus.MERGED.name());
    }

    private static Short year(PartialDate date) {
        return date.year() == null ? null : date.year().shortValue();
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
