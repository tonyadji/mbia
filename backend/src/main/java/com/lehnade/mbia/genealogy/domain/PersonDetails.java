package com.lehnade.mbia.genealogy.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;

/**
 * The identity and profile data of a {@link Person} (mvp.md §6), normalised and checked:
 *
 * <ul>
 *   <li>names and biography are trimmed; a blank optional value becomes {@code null};
 *   <li>{@code firstName} is the only required name;
 *   <li>a missing gender or date is {@code UNKNOWN};
 *   <li>a Person who is not deceased has an {@code UNKNOWN} death date.
 * </ul>
 */
public record PersonDetails(String firstName, String middleNames, String lastName, String preferredName,
        Gender gender, PartialDate birth, boolean deceased, PartialDate death, String biography) {

    public static final int FIRST_NAME_MAX_LENGTH = 150;
    public static final int MIDDLE_NAMES_MAX_LENGTH = 250;
    public static final int LAST_NAME_MAX_LENGTH = 150;
    public static final int PREFERRED_NAME_MAX_LENGTH = 150;
    public static final int BIOGRAPHY_MAX_LENGTH = 10_000;

    public PersonDetails {
        firstName = optional("firstName", firstName, FIRST_NAME_MAX_LENGTH);
        if (firstName == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "The first name must not be blank.");
        }
        middleNames = optional("middleNames", middleNames, MIDDLE_NAMES_MAX_LENGTH);
        lastName = optional("lastName", lastName, LAST_NAME_MAX_LENGTH);
        preferredName = optional("preferredName", preferredName, PREFERRED_NAME_MAX_LENGTH);
        biography = optional("biography", biography, BIOGRAPHY_MAX_LENGTH);
        gender = gender == null ? Gender.UNKNOWN : gender;
        birth = birth == null ? PartialDate.UNKNOWN : birth;
        death = death == null ? PartialDate.UNKNOWN : death;
        if (!deceased && death.isKnown()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "A death date can only be given for a deceased person.");
        }
    }

    /**
     * The name shown for the Person (mvp.md §6): the preferred name when present, otherwise the
     * first name followed by the last name. Computed, never stored.
     */
    public String displayName() {
        if (preferredName != null) {
            return preferredName;
        }
        return lastName == null ? firstName : firstName + " " + lastName;
    }

    private static String optional(String field, String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    field + " must not exceed " + maxLength + " characters.");
        }
        return trimmed;
    }
}
