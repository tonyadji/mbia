package com.lehnade.mbia.genealogy.domain;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {

    /**
     * The order of Person ids used wherever Mbia must choose deterministically (partner order,
     * kinship tie-breaking). PostgreSQL orders {@code uuid} values byte by byte, which is the
     * order of their lowercase hexadecimal form; {@link UUID#compareTo} compares signed longs and
     * would disagree with the database.
     */
    public static final Comparator<PersonId> ORDER = Comparator.comparing(id -> id.value().toString());

    public PersonId {
        Objects.requireNonNull(value, "value");
    }

    public static PersonId newId() {
        return new PersonId(UUID.randomUUID());
    }
}
