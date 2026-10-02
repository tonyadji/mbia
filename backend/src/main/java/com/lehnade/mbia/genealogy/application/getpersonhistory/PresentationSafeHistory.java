package com.lehnade.mbia.genealogy.application.getpersonhistory;

import com.lehnade.mbia.genealogy.application.audit.PersonAuditValues;
import com.lehnade.mbia.genealogy.application.getpersonhistory.PersonHistoryQuery.Entry;
import java.util.HashSet;
import java.util.Set;

/**
 * What the Person history shows of the audit trail (data-model.md §18, genealogy.md §13, OQ-031):
 * only entries whose resource is the Person, and only the old and new value of a field changed by
 * {@code PERSON_UPDATED}, except the biography and the photo (OQ-046). Other values (user ids, merge details, statuses)
 * stay in the internal audit.
 */
final class PresentationSafeHistory {

    static final Set<String> ACTIONS = Set.of("PERSON_CREATED", "PERSON_UPDATED", "PERSON_CLAIMED",
            "PERSON_UNCLAIMED", "PERSON_ARCHIVED", "PERSON_RESTORED", "PERSONS_MERGED");

    /** Fields whose change is shown without its values. */
    private static final Set<String> FIELDS_WITHOUT_VALUES = Set.of("biography", PersonAuditValues.PROFILE_PICTURE);

    private PresentationSafeHistory() {}

    static PersonHistoryEntryView of(Entry entry) {
        String field = null;
        Object oldValue = null;
        Object newValue = null;
        if (entry.action().equals("PERSON_UPDATED")) {
            Set<String> fields = new HashSet<>(entry.oldValue().keySet());
            fields.addAll(entry.newValue().keySet());
            // Entries are field-focused: one changed field each.
            if (fields.size() == 1) {
                field = fields.iterator().next();
                if (!FIELDS_WITHOUT_VALUES.contains(field)) {
                    oldValue = entry.oldValue().get(field);
                    newValue = entry.newValue().get(field);
                }
            }
        }
        return new PersonHistoryEntryView(entry.id(), entry.action(), entry.actor(), field, oldValue, newValue,
                entry.occurredAt());
    }
}
