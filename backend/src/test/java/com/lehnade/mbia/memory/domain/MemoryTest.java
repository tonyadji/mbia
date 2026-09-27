package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PR-29, PR-33: a STORY has a title and a text, and at least one Person (mvp.md §17, data-model.md
 * §14); it is edited partially (OQ-008) and archived.
 */
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

    // --- Edit and archive (PR-33) ---

    private static final UUID EDITOR = UUID.randomUUID();
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void anEditChangesOnlyTheGivenValues() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));
        UUID other = UUID.randomUUID();

        Memory edited = memory.updateStory(Optional.of("  Nouveau  "), Optional.empty(),
                Optional.of(List.of(other)), EDITOR, LATER);

        assertThat(edited.title()).isEqualTo("Nouveau");
        assertThat(edited.content()).isEqualTo("Texte");
        assertThat(edited.relatedPersonIds()).containsExactly(other);
        assertThat(edited.updatedBy()).isEqualTo(EDITOR);
        assertThat(edited.updatedAt()).isEqualTo(LATER);
        assertThat(edited.createdBy()).isEqualTo(USER);
        assertThat(edited.version()).isZero();
    }

    @Test
    void anEditThatChangesNothingReturnsTheSameMemory() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));

        assertThat(memory.updateStory(Optional.empty(), Optional.empty(), Optional.empty(), EDITOR, LATER))
                .isSameAs(memory);
        assertThat(memory.updateStory(Optional.of(" Titre "), Optional.of("Texte"), Optional.of(List.of(PERSON)),
                EDITOR, LATER)).isSameAs(memory);
    }

    @Test
    void anEditKeepsTheRulesOfAStory() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));

        assertInvalid(() -> memory.updateStory(Optional.of(" "), Optional.empty(), Optional.empty(), EDITOR, LATER));
        assertInvalid(() -> memory.updateStory(Optional.of("x".repeat(251)), Optional.empty(), Optional.empty(),
                EDITOR, LATER));
        assertInvalid(() -> memory.updateStory(Optional.empty(), Optional.of("\n"), Optional.empty(), EDITOR,
                LATER));
        assertInvalid(() -> memory.updateStory(Optional.empty(), Optional.empty(), Optional.of(List.of()), EDITOR,
                LATER));
    }

    @Test
    void anArchivedStoryKeepsItsContentAndPersons() {
        Memory archived = story("Titre", "Texte", List.of(PERSON)).archive(EDITOR, LATER);

        assertThat(archived.status()).isEqualTo(MemoryStatus.ARCHIVED);
        assertThat(archived.title()).isEqualTo("Titre");
        assertThat(archived.relatedPersonIds()).containsExactly(PERSON);
        assertThat(archived.updatedBy()).isEqualTo(EDITOR);
    }

    // --- PR-41: photos (mvp.md §17, data-model.md §14, §14bis, OQ-042) ---

    @Test
    void photosTakeThePositionsOneToNInTheOrderGiven() {
        MemoryPhoto.New first = photo("Au marché");
        MemoryPhoto.New second = new MemoryPhoto.New(MediaAssetId.newId(), null, TakenDate.of(
                TakenDate.Precision.YEAR_ONLY, null, 1974));
        MemoryPhoto.New third = photo(null);

        Memory memory = withPhotos("Titre", "Texte", List.of(first, second, third));

        assertThat(memory.photos()).extracting(MemoryPhoto::mediaAssetId)
                .containsExactly(first.mediaAssetId(), second.mediaAssetId(), third.mediaAssetId());
        assertThat(memory.photos()).extracting(MemoryPhoto::position).containsExactly(1, 2, 3);
        assertThat(memory.photos().getFirst().caption()).isEqualTo("Au marché");
        assertThat(memory.photos().get(1).takenAt()).isEqualTo(TakenDate.of(TakenDate.Precision.YEAR_ONLY, null,
                1974));
        assertThat(memory.photos().getLast().takenAt()).isEqualTo(TakenDate.UNKNOWN);
    }

    @Test
    void aStoryWithAPhotoNeedsNoText() {
        Memory memory = withPhotos("Titre", null, List.of(photo(null)));

        assertThat(memory.content()).isNull();
        assertThat(memory.photos()).hasSize(1);
    }

    @Test
    void aStoryWithoutPhotoNeedsAText() {
        assertInvalid(() -> withPhotos("Titre", null, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \n  "})
    void aTextGivenWithAPhotoIsNeverBlank(String content) {
        assertInvalid(() -> withPhotos("Titre", content, List.of(photo(null))));
    }

    @Test
    void theSamePhotoTwiceIsRefused() {
        MediaAssetId id = MediaAssetId.newId();

        assertInvalid(() -> withPhotos("Titre", "Texte", List.of(new MemoryPhoto.New(id, null, null),
                new MemoryPhoto.New(id, "Encore", null))));
    }

    @Test
    void aCaptionIsKeptAsWrittenUpTo5000CharactersAndABlankOneIsNoCaption() {
        Memory memory = withPhotos("Titre", "Texte", List.of(photo("  Mamie\n1974  "), photo("   "),
                photo("x".repeat(5000))));

        assertThat(memory.photos()).extracting(MemoryPhoto::caption)
                .containsExactly("  Mamie\n1974  ", null, "x".repeat(5000));
        assertInvalid(() -> withPhotos("Titre", "Texte", List.of(photo("x".repeat(5001)))));
    }

    @Test
    void aTitleEditOfAStoryWithoutTextKeepsItWithoutText() {
        Memory memory = withPhotos("Titre", null, List.of(photo(null)));

        Memory changed = memory.updateStory(Optional.of("Nouveau"), Optional.empty(), Optional.empty(), EDITOR,
                LATER);

        assertThat(changed.content()).isNull();
        assertThat(changed.photos()).isEqualTo(memory.photos());
        assertThat(memory.updateStory(Optional.of("Titre"), Optional.empty(), Optional.empty(), EDITOR, LATER))
                .isSameAs(memory);
    }

    @Test
    void onlyItsCreatorIsTheCreator() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));

        assertThat(memory.isCreatedBy(USER)).isTrue();
        assertThat(memory.isCreatedBy(EDITOR)).isFalse();
    }

    private static Memory story(String title, String content, List<UUID> persons) {
        return Memory.createStory(MemoryId.newId(), FAMILY, title, content, persons, List.of(), USER, NOW);
    }

    private static Memory withPhotos(String title, String content, List<MemoryPhoto.New> photos) {
        return Memory.createStory(MemoryId.newId(), FAMILY, title, content, List.of(PERSON), photos, USER, NOW);
    }

    private static MemoryPhoto.New photo(String caption) {
        return new MemoryPhoto.New(MediaAssetId.newId(), caption, null);
    }

    private static void assertInvalid(Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
