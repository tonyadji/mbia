package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.PersonSearchQuery;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The Family people search in two queries, the page then the total, on the {@code unaccent}
 * extension of V005 (genealogy.md §11, data-model.md §23.1).
 */
@Component
class JpaPersonSearchQuery implements PersonSearchQuery {

    private final PersonJpaRepository persons;

    JpaPersonSearchQuery(PersonJpaRepository persons) {
        this.persons = persons;
    }

    @Override
    public Result search(UUID familyId, PersonStatus status, String text, int page, int size) {
        String pattern = "%" + escapeLike(text) + "%";
        return new Result(
                persons.search(familyId, status.name(), pattern, size, (long) page * size).stream()
                        .map(JpaPersonRepository::toDomain)
                        .toList(),
                persons.countSearch(familyId, status.name(), pattern));
    }

    /** The typed text matches literally: {@code %} and {@code _} are not wildcards. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
