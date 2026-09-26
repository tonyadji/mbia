package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Person as the openapi {@code PersonSummary} describes it, for the {@code details} of a problem
 * response (the candidates of {@code POSSIBLE_DUPLICATE}). Absent values are left out; no photo in
 * Phase 2.
 */
public final class PersonSummaries {

    private PersonSummaries() {}

    public static Map<String, Object> of(Person person) {
        PersonDetails details = person.details();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", person.id().value());
        summary.put("familyId", person.familyId());
        summary.put("firstName", details.firstName());
        putIfPresent(summary, "middleNames", details.middleNames());
        putIfPresent(summary, "lastName", details.lastName());
        putIfPresent(summary, "preferredName", details.preferredName());
        summary.put("displayName", details.displayName());
        summary.put("gender", details.gender().name());
        summary.put("birth", of(details.birth()));
        summary.put("isDeceased", details.deceased());
        summary.put("death", of(details.death()));
        person.linkedUserId().ifPresent(user -> summary.put("linkedUserId", user));
        summary.put("status", person.status().name());
        summary.put("version", person.version());
        return summary;
    }

    private static Map<String, Object> of(PartialDate date) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("precision", date.precision().name());
        putIfPresent(value, "date", date.date() == null ? null : date.date().toString());
        putIfPresent(value, "year", date.year());
        return value;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
