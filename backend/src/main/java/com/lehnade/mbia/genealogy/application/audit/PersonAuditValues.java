package com.lehnade.mbia.genealogy.application.audit;

import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** The audited values of a Person's identity and profile (data-model.md §17), field by field. */
public final class PersonAuditValues {

    /**
     * The photo of the Person (OQ-046): its value is the media asset id, never a storage key; the
     * Person history shows the field without values.
     */
    public static final String PROFILE_PICTURE = "profilePicture";

    private PersonAuditValues() {}

    /** The photo field as a one-entry map, or an empty map when there is no photo. */
    public static Map<String, Object> profilePicture(UUID mediaAssetId) {
        return mediaAssetId == null ? Map.of() : Map.of(PROFILE_PICTURE, mediaAssetId);
    }

    /** Every field that has a value; an absent key means "no value". */
    public static Map<String, Object> of(PersonDetails details) {
        Map<String, Object> values = new HashMap<>();
        values.put("firstName", details.firstName());
        putIfPresent(values, "middleNames", details.middleNames());
        putIfPresent(values, "lastName", details.lastName());
        putIfPresent(values, "preferredName", details.preferredName());
        values.put("gender", details.gender().name());
        values.put("birth", describe(details.birth()));
        values.put("isDeceased", details.deceased());
        values.put("death", describe(details.death()));
        putIfPresent(values, "biography", details.biography());
        return values;
    }

    /**
     * The fields of {@code from} whose value differs in {@code to}, with their value in
     * {@code from}. {@code changed(old, new)} and {@code changed(new, old)} are the old and new
     * values of an update.
     */
    public static Map<String, Object> changed(PersonDetails from, PersonDetails to) {
        Map<String, Object> before = of(from);
        Map<String, Object> after = of(to);
        Map<String, Object> changed = new HashMap<>();
        before.forEach((field, value) -> {
            if (!value.equals(after.get(field))) {
                changed.put(field, value);
            }
        });
        return changed;
    }

    /**
     * The names of the fields whose value differs between {@code from} and {@code to}, including a
     * field that has a value on one side only, in name order.
     */
    public static Set<String> changedFields(PersonDetails from, PersonDetails to) {
        Map<String, Object> before = of(from);
        Map<String, Object> after = of(to);
        Set<String> fields = new TreeSet<>(before.keySet());
        fields.addAll(after.keySet());
        fields.removeIf(field -> Objects.equals(before.get(field), after.get(field)));
        return fields;
    }

    /** The value of one field, as a one-entry map, or an empty map when the field has no value. */
    public static Map<String, Object> field(PersonDetails details, String field) {
        Object value = of(details).get(field);
        return value == null ? Map.of() : Map.of(field, value);
    }

    private static String describe(PartialDate date) {
        return switch (date.precision()) {
            case EXACT -> date.date().toString();
            case YEAR_ONLY -> date.year().toString();
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
