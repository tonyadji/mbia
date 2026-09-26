package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** PR-29: a STORY has a title and a text, and at least one Person (mvp.md §17, data-model.md §14). */
class MemoryTest {

    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Test
    void aNewStoryIsActiveAtVersionZeroWithATrimmedTitleAndTheTextAsWritten() {
        UUID other = UUID.randomUUID();

        Memory memory = story("  Le marché  ", "  Grand-mère vendait\ndu plantain.  ", List.of(PERSON, other));

        assertThat(memory.type()).isEqualTo(MemoryType.STORY);
        assertThat(memory.status()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(memory.title()).isEqualTo("Le marché");
        assertThat(memory.content()).isEqualTo("  Grand-mère vendait\ndu plantain.  ");
        assertThat(memory.relatedPersonIds()).containsExactlyInAnyOrder(PERSON, other);
        assertThat(memory.createdBy()).isEqualTo(USER);
        assertThat(memory.updatedBy()).isEqualTo(USER);
        assertThat(memory.createdAt()).isEqualTo(NOW);
        assertThat(memory.updatedAt()).isEqualTo(NOW);
        assertThat(memory.version()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void aMissingOrBlankTitleIsRefused(String title) {
        assertInvalid(() -> story(title, "Texte", List.of(PERSON)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void aMissingOrBlankTextIsRefused(String content) {
        assertInvalid(() -> story("Titre", content, List.of(PERSON)));
    }

    @Test
    void theTitleIsLimitedTo250Characters() {
        assertThat(story("x".repeat(250), "Texte", List.of(PERSON)).title()).hasSize(250);
        assertThat(story(" " + "x".repeat(250) + " ", "Texte", List.of(PERSON)).title()).hasSize(250);
        assertInvalid(() -> story("x".repeat(251), "Texte", List.of(PERSON)));
    }

    @Test
    void theTextIsLimitedTo50000Characters() {
        assertThat(story("Titre", "x".repeat(50_000), List.of(PERSON)).content()).hasSize(50_000);
        assertInvalid(() -> story("Titre", "x".repeat(50_001), List.of(PERSON)));
    }

    @Test
    void aStoryWithoutPersonIsRefused() {
        assertInvalid(() -> story("Titre", "Texte", List.of()));
        assertInvalid(() -> story("Titre", "Texte", null));
    }

    @Test
    void aPersonRepeatedIsLinkedOnce() {
        assertThat(story("Titre", "Texte", List.of(PERSON, PERSON)).relatedPersonIds()).isEqualTo(Set.of(PERSON));
    }

    private static Memory story(String title, String content, List<UUID> persons) {
        return Memory.createStory(MemoryId.newId(), FAMILY, title, content, persons, USER, NOW);
    }

    private static void assertInvalid(Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
