package com.lehnade.mbia.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Family name rules of PR-13 and rename of PR-14: trimmed, not blank, 1–200 characters. */
class FamilyTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID CREATOR = UUID.randomUUID();

    @Test
    void newFamilyHasTrimmedNameAndStartsAtVersionZero() {
        FamilyId id = FamilyId.newId();

        Family family = Family.create(id, "  Famille Mbida \t", CREATOR, NOW);

        assertThat(family.id()).isEqualTo(id);
        assertThat(family.name()).isEqualTo("Famille Mbida");
        assertThat(family.createdBy()).isEqualTo(CREATOR);
        assertThat(family.version()).isZero();
        assertThat(family.createdAt()).isEqualTo(NOW);
        assertThat(family.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   \t\n ", "　"})
    void blankNameIsRejected(String name) {
        assertInvalid(name);
    }

    @Test
    void nameOfOneCharacterIsAccepted() {
        assertThat(Family.create(FamilyId.newId(), "A", CREATOR, NOW).name()).isEqualTo("A");
    }

    @Test
    void nameOf200CharactersIsAccepted() {
        String name = "x".repeat(200);

        assertThat(Family.create(FamilyId.newId(), name, CREATOR, NOW).name()).isEqualTo(name);
    }

    @Test
    void nameOf201CharactersIsRejected() {
        assertInvalid("x".repeat(201));
    }

    @Test
    void lengthIsCheckedAfterTrimming() {
        String name = "x".repeat(200);

        assertThat(Family.create(FamilyId.newId(), "  " + name + "  ", CREATOR, NOW).name()).isEqualTo(name);
    }

    @Test
    void lengthCountsCharactersNotUtf16Units() {
        // 200 emoji are 400 UTF-16 units but 200 characters.
        String name = "🌳".repeat(200);

        assertThat(Family.create(FamilyId.newId(), name, CREATOR, NOW).name()).isEqualTo(name);
        assertInvalid(name + "🌳");
    }

    @Test
    void renameTrimsTheNewNameAndKeepsIdentityAndVersion() {
        Family family = Family.restore(FamilyId.newId(), "Famille Mbida", CREATOR, NOW, NOW, 4);
        Instant later = NOW.plusSeconds(60);

        Family renamed = family.rename("  Famille Ndongo ", later);

        assertThat(renamed.id()).isEqualTo(family.id());
        assertThat(renamed.name()).isEqualTo("Famille Ndongo");
        assertThat(renamed.createdBy()).isEqualTo(CREATOR);
        assertThat(renamed.createdAt()).isEqualTo(NOW);
        assertThat(renamed.updatedAt()).isEqualTo(later);
        assertThat(renamed.version()).isEqualTo(4);
        assertThat(family.name()).isEqualTo("Famille Mbida");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void renameToABlankNameIsRejected(String name) {
        Family family = Family.create(FamilyId.newId(), "Famille Mbida", CREATOR, NOW);

        assertThatThrownBy(() -> family.rename(name, NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void renameToMoreThan200CharactersIsRejected() {
        Family family = Family.create(FamilyId.newId(), "Famille Mbida", CREATOR, NOW);

        assertThatThrownBy(() -> family.rename("x".repeat(201), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static void assertInvalid(String name) {
        assertThatThrownBy(() -> Family.create(FamilyId.newId(), name, CREATOR, NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
