package com.lehnade.mbia.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** mvp.md §6 and data-model.md §10: Person identity rules. */
class PersonDetailsTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void firstNameIsRequired(String firstName) {
        assertValidationFailed(() -> details(firstName, null, null));
    }

    @Test
    void namesAreTrimmedAndBlankOptionalValuesBecomeNull() {
        PersonDetails details = new PersonDetails("  Marie ", " ", " Mbida ", "", Gender.FEMALE, null, false, null,
                "  ");

        assertThat(details.firstName()).isEqualTo("Marie");
        assertThat(details.middleNames()).isNull();
        assertThat(details.lastName()).isEqualTo("Mbida");
        assertThat(details.preferredName()).isNull();
        assertThat(details.biography()).isNull();
    }

    @Test
    void missingGenderAndDatesAreUnknown() {
        PersonDetails details = details("Marie", null, null);

        assertThat(details.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(details.birth()).isEqualTo(PartialDate.UNKNOWN);
        assertThat(details.death()).isEqualTo(PartialDate.UNKNOWN);
    }

    @Test
    void lengthLimitsCountCharacters() {
        assertThat(details("é".repeat(150), null, null).firstName()).hasSize(150);
        assertValidationFailed(() -> details("x".repeat(151), null, null));
        assertValidationFailed(() -> details("Marie", "x".repeat(151), null));
        assertValidationFailed(() -> details("Marie", null, "x".repeat(151)));
        assertValidationFailed(() -> new PersonDetails("Marie", "x".repeat(251), null, null, null, null, false,
                null, null));
        assertValidationFailed(() -> new PersonDetails("Marie", null, null, null, null, null, false, null,
                "x".repeat(10_001)));
    }

    @Test
    void aDeathDateRequiresADeceasedPerson() {
        assertValidationFailed(() -> new PersonDetails("Marie", null, null, null, null, null, false,
                PartialDate.yearOnly(2001), null));
    }

    @Test
    void aDeceasedPersonMayHaveAKnownOrUnknownDeathDate() {
        assertThat(new PersonDetails("Marie", null, null, null, null, null, true, PartialDate.yearOnly(2001), null)
                .death()).isEqualTo(PartialDate.yearOnly(2001));
        assertThat(new PersonDetails("Marie", null, null, null, null, null, true, null, null).death())
                .isEqualTo(PartialDate.UNKNOWN);
    }

    @Test
    void displayNameFollowsMvpRules() {
        assertThat(details("Marie", "Mbida", "Mamie").displayName()).isEqualTo("Mamie");
        assertThat(details("Marie", "Mbida", "  ").displayName()).isEqualTo("Marie Mbida");
        assertThat(details("Marie", null, null).displayName()).isEqualTo("Marie");
        assertThat(new PersonDetails("Marie", "Jeanne", "Mbida", null, null, null, false, null, null).displayName())
                .isEqualTo("Marie Mbida");
    }

    private static PersonDetails details(String firstName, String lastName, String preferredName) {
        return new PersonDetails(firstName, null, lastName, preferredName, null, null, false, null, null);
    }

    private static void assertValidationFailed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
