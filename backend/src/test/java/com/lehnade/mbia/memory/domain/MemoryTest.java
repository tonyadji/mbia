package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
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

        Memory edited = edit(memory, Optional.of("  Nouveau  "), Optional.empty(), Optional.of(List.of(other)));

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

        assertThat(edit(memory, Optional.empty(), Optional.empty(), Optional.empty()))
                .isSameAs(memory);
        assertThat(edit(memory, Optional.of(" Titre "), Optional.of("Texte"), Optional.of(List.of(PERSON))))
                .isSameAs(memory);
    }

    @Test
    void anEditKeepsTheRulesOfAStory() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));

        assertInvalid(() -> edit(memory, Optional.of(" "), Optional.empty(), Optional.empty()));
        assertInvalid(() -> edit(memory, Optional.of("x".repeat(251)), Optional.empty(), Optional.empty()));
        assertInvalid(() -> edit(memory, Optional.empty(), Optional.of("\n"), Optional.empty()));
        assertInvalid(() -> edit(memory, Optional.empty(), Optional.empty(), Optional.of(List.of())));
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

        Memory changed = edit(memory, Optional.of("Nouveau"), Optional.empty(), Optional.empty());

        assertThat(changed.content()).isNull();
        assertThat(changed.photos()).isEqualTo(memory.photos());
        assertThat(edit(memory, Optional.of("Titre"), Optional.empty(), Optional.empty()))
                .isSameAs(memory);
    }

    // --- PR-42: editing the photos (mvp.md §17, data-model.md §14bis, OQ-042) ---

    @Test
    void keptPhotosKeepTheirPositionWhateverTheOrderAndNewOnesFollow() {
        Memory memory = withPhotos("Titre", "Texte", List.of(photo(null), photo(null), photo(null)));
        MemoryPhoto first = memory.photos().get(0);
        MemoryPhoto second = memory.photos().get(1);
        MemoryPhoto third = memory.photos().get(2);
        MemoryPhoto.New added = photo("Nouvelle");

        Memory edited = editPhotos(memory, Optional.empty(), added, same(third, null, null), same(first, null, null));

        assertThat(edited.photos()).extracting(MemoryPhoto::mediaAssetId)
                .containsExactly(first.mediaAssetId(), third.mediaAssetId(), added.mediaAssetId());
        // No renumbering: the gap of the removed second photo stays, the new one comes after all.
        assertThat(edited.photos()).extracting(MemoryPhoto::position).containsExactly(1, 3, 4);
        assertThat(edited.photos()).extracting(MemoryPhoto::mediaAssetId).doesNotContain(second.mediaAssetId());
    }

    @Test
    void newPhotosAreAddedInListOrder() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));
        MemoryPhoto.New a = photo(null);
        MemoryPhoto.New b = photo(null);

        Memory edited = editPhotos(memory, Optional.empty(), a, b);

        assertThat(edited.photos()).extracting(MemoryPhoto::mediaAssetId)
                .containsExactly(a.mediaAssetId(), b.mediaAssetId());
        assertThat(edited.photos()).extracting(MemoryPhoto::position).containsExactly(1, 2);
    }

    @Test
    void theCaptionAndTakenDateOfAKeptPhotoAreReplaced() {
        Memory memory = withPhotos("Titre", "Texte", List.of(photo("Avant")));
        TakenDate year = TakenDate.of(TakenDate.Precision.YEAR_ONLY, null, 1980);

        Memory edited = editPhotos(memory, Optional.empty(), same(memory.photos().getFirst(), "  ", year));

        assertThat(edited.photos().getFirst().caption()).isNull();
        assertThat(edited.photos().getFirst().takenAt()).isEqualTo(year);
        assertThat(edited.photos().getFirst().position()).isEqualTo(1);
    }

    @Test
    void theSameListWithTheSameDetailsChangesNothing() {
        Memory memory = withPhotos("Titre", "Texte", List.of(photo("Légende"), photo(null)));
        MemoryPhoto first = memory.photos().get(0);
        MemoryPhoto second = memory.photos().get(1);

        assertThat(editPhotos(memory, Optional.empty(), same(second, "", null), same(first, "Légende", null)))
                .isSameAs(memory);
    }

    @Test
    void aBlankTextEmptiesItOnlyWhileAPhotoRemains() {
        Memory withPhoto = withPhotos("Titre", "Texte", List.of(photo(null)));
        Memory withoutPhoto = story("Titre", "Texte", List.of(PERSON));

        assertThat(edit(withPhoto, Optional.empty(), Optional.of(""), Optional.empty()).content()).isNull();
        assertInvalidField(() -> edit(withoutPhoto, Optional.empty(), Optional.of(" "), Optional.empty()), "content");
        assertInvalidField(() -> editPhotos(withPhoto, Optional.of("")), "content");
    }

    @Test
    void theLastPhotoIsRemovedOnlyWhileATextRemains() {
        Memory withText = withPhotos("Titre", "Texte", List.of(photo(null)));
        Memory withoutText = withPhotos("Titre", null, List.of(photo(null)));

        assertThat(editPhotos(withText, Optional.empty()).photos()).isEmpty();
        assertInvalidField(() -> editPhotos(withoutText, Optional.empty()), "photos");
        assertThat(editPhotos(withoutText, Optional.of("Une histoire")).content()).isEqualTo("Une histoire");
    }

    @Test
    void anEditedListFollowsTheRulesOfNewPhotos() {
        Memory memory = withPhotos("Titre", "Texte", List.of(photo(null)));
        MemoryPhoto.New kept = same(memory.photos().getFirst(), null, null);

        assertInvalid(() -> editPhotos(memory, Optional.empty(), kept, kept));
        assertInvalid(() -> editPhotos(memory, Optional.empty(), photo("x".repeat(5001))));
    }

    @Test
    void onlyItsCreatorIsTheCreator() {
        Memory memory = story("Titre", "Texte", List.of(PERSON));

        assertThat(memory.isCreatedBy(USER)).isTrue();
        assertThat(memory.isCreatedBy(EDITOR)).isFalse();
    }

    private static Memory edit(Memory memory, Optional<String> title, Optional<String> content,
            Optional<List<UUID>> persons) {
        return memory.updateStory(title, content, persons, Optional.empty(), EDITOR, LATER);
    }

    private static Memory editPhotos(Memory memory, Optional<String> content, MemoryPhoto.New... photos) {
        return memory.updateStory(Optional.empty(), content, Optional.empty(), Optional.of(List.of(photos)), EDITOR,
                LATER);
    }

    private static MemoryPhoto.New same(MemoryPhoto photo, String caption, TakenDate takenAt) {
        return new MemoryPhoto.New(photo.mediaAssetId(), caption, takenAt);
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

    private static void assertInvalidField(Runnable change, String field) {
        assertThatThrownBy(change::run)
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.field()).isEqualTo(field));
    }

    private static void assertInvalid(Runnable creation) {
        assertThatThrownBy(creation::run)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
