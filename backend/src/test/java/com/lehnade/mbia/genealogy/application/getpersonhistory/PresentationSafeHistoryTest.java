package com.lehnade.mbia.genealogy.application.getpersonhistory;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.genealogy.application.getpersonhistory.PersonHistoryQuery.Actor;
import com.lehnade.mbia.genealogy.application.getpersonhistory.PersonHistoryQuery.Entry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PR-28: what the Person history shows of an audit entry (data-model.md §18, OQ-031). */
class PresentationSafeHistoryTest {

    @Test
    void onlyPersonActionsAreShown() {
        assertThat(PresentationSafeHistory.ACTIONS).containsExactlyInAnyOrder("PERSON_CREATED", "PERSON_UPDATED",
                "PERSON_CLAIMED", "PERSON_UNCLAIMED", "PERSON_ARCHIVED", "PERSON_RESTORED", "PERSONS_MERGED")
                .noneMatch(action -> action.startsWith("RELATIONSHIP_"));
    }

    @Test
    void aChangedFieldShowsItsOldAndNewValue() {
        PersonHistoryEntryView view = PresentationSafeHistory.of(entry("PERSON_UPDATED",
                Map.of("birth", "1954"), Map.of("birth", "1956")));

        assertThat(view.field()).isEqualTo("birth");
        assertThat(view.oldValue()).isEqualTo("1954");
        assertThat(view.newValue()).isEqualTo("1956");
    }

    @Test
    void aFieldWithoutPreviousValueShowsNoOldValue() {
        PersonHistoryEntryView view = PresentationSafeHistory.of(entry("PERSON_UPDATED",
                Map.of(), Map.of("lastName", "Mbida")));

        assertThat(view.field()).isEqualTo("lastName");
        assertThat(view.oldValue()).isNull();
        assertThat(view.newValue()).isEqualTo("Mbida");
    }

    @Test
    void theBiographyChangeShowsNoValue() {
        PersonHistoryEntryView view = PresentationSafeHistory.of(entry("PERSON_UPDATED",
                Map.of("biography", "Avant"), Map.of("biography", "Après")));

        assertThat(view.field()).isEqualTo("biography");
        assertThat(view.oldValue()).isNull();
        assertThat(view.newValue()).isNull();
    }

    @Test
    void otherActionsShowNoFieldNorValue() {
        for (String action : new String[] {"PERSON_CREATED", "PERSON_CLAIMED", "PERSON_UNCLAIMED",
                "PERSON_ARCHIVED", "PERSON_RESTORED", "PERSONS_MERGED"}) {
            PersonHistoryEntryView view = PresentationSafeHistory.of(entry(action,
                    Map.of("linkedUserId", UUID.randomUUID(), "status", "ACTIVE"),
                    Map.of("firstName", "Marie", "status", "MERGED")));

            assertThat(view.field()).isNull();
            assertThat(view.oldValue()).isNull();
            assertThat(view.newValue()).isNull();
        }
    }

    @Test
    void anUpdateOfSeveralFieldsShowsNoValue() {
        PersonHistoryEntryView view = PresentationSafeHistory.of(entry("PERSON_UPDATED",
                Map.of("birth", "1954"), Map.of("birth", "1956", "lastName", "Mbida")));

        assertThat(view.field()).isNull();
        assertThat(view.newValue()).isNull();
    }

    private static Entry entry(String action, Map<String, Object> oldValue, Map<String, Object> newValue) {
        return new Entry(UUID.randomUUID(), action, new Actor(UUID.randomUUID(), "Alice", false), oldValue, newValue,
                Instant.parse("2026-09-26T10:00:00Z"));
    }
}
