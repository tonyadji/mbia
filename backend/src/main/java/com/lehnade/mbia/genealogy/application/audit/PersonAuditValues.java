package com.lehnade.mbia.genealogy.application.audit;

import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.HashMap;
import java.util.Map;

/** The audited values of a Person's identity and profile (data-model.md §17), field by field. */
public final class PersonAuditValues {

    private PersonAuditValues() {}

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
